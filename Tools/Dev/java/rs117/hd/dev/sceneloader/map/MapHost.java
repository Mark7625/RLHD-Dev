package rs117.hd.dev.sceneloader.map;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

@Slf4j
public class MapHost implements MapControls.MapWindow {
	private static final String CLIENT_PANEL = "net.runelite.client.ui.ClientPanel";
	private static final Dimension WINDOW_SIZE = new Dimension(960, 760);

	private final Client client;
	private final IntConsumer onChosen;
	private final IntConsumer onOpened;
	@Nullable
	private final RegionMapPanel.Coverage coverage;

	@Nullable
	private JPanel content;
	@Nullable
	private RegionMapPanel map;

	@Nullable
	private JDialog window;

	@Nullable
	private Container gameHost;
	@Nullable
	private Component gameView;

	private boolean poppedOut;

	public MapHost(
		Client client, IntConsumer onChosen, IntConsumer onOpened,
		@Nullable RegionMapPanel.Coverage coverage
	) {
		this.client = client;
		this.onChosen = onChosen;
		this.onOpened = onOpened;
		this.coverage = coverage;
	}

	public void setOnAdded(@Nullable IntConsumer onAdded) {
		this.onAdded = onAdded;
		if (map != null)
			map.setOnAdded(onAdded);
	}

	public void setOnCleared(@Nullable Runnable onCleared) {
		this.onCleared = onCleared;
		if (map != null)
			map.setOnCleared(onCleared);
	}

	public void setListed(int[] listedRegions) {
		this.listed = listedRegions;
		if (map != null)
			map.setListed(listedRegions);
	}

	@Nullable
	private IntConsumer onAdded;
	@Nullable
	private Runnable onCleared;
	private int[] listed = new int[0];

	public void refresh() {
		if (map != null)
			map.setCoverage(coverage);
	}

	public boolean isOpen() {
		return content != null;
	}

	public void open(int startRegion) {
		assert SwingUtilities.isEventDispatchThread();

		if (content != null) {
			if (map != null)
				map.select(startRegion);
			if (window != null)
				window.toFront();
			return;
		}

		build(startRegion);
		if (poppedOut || !embed())
			popOutWindow();
	}

	private void build(int startRegion) {
		var status = new JLabel(" ");
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(Color.LIGHT_GRAY);
		status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		map = new RegionMapPanel(startRegion, onChosen, region -> {
			onOpened.accept(region);
			if (!poppedOut)
				close();
		}, status);
		map.setCoverage(coverage);
		map.setOnAdded(onAdded);
		map.setOnCleared(onCleared);
		map.setListed(listed);

		content = new JPanel(new BorderLayout());
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(new MapControls(map, this), BorderLayout.NORTH);
		content.add(map, BorderLayout.CENTER);
		content.add(status, BorderLayout.SOUTH);
	}

	@Override
	public boolean isPoppedOut() {
		return poppedOut;
	}

	@Override
	public void togglePopOut() {
		if (content == null)
			return;

		if (poppedOut) {
			closeWindow();
			if (!embed())
				popOutWindow();
		} else {
			restoreGameView();
			popOutWindow();
		}
	}

	@Override
	public void close() {
		if (content == null)
			return;

		closeWindow();
		restoreGameView();
		if (map != null)
			map.shutDown();
		map = null;
		content = null;
	}


	private boolean embed() {
		Container host = findGameHost();
		if (host == null) {
			log.debug("Could not find the game view to put the map over, so it opens in a window instead");
			return false;
		}

		gameHost = host;
		gameView = centreOf(host);
		if (gameView == null)
			return false;

		gameView.setVisible(false);
		host.add(content, BorderLayout.CENTER);
		host.revalidate();
		host.repaint();

		poppedOut = false;
		return true;
	}

	private void restoreGameView() {
		if (gameHost == null || gameView == null)
			return;

		gameHost.remove(content);
		((BorderLayout) gameHost.getLayout()).addLayoutComponent(gameView, BorderLayout.CENTER);
		gameView.setVisible(true);
		gameHost.revalidate();
		gameHost.repaint();

		gameHost = null;
		gameView = null;
	}

	@Nullable
	private Container findGameHost() {
		Component canvas = client.getCanvas();
		for (Container parent = canvas == null ? null : canvas.getParent();
			 parent != null;
			 parent = parent.getParent()) {
			if (CLIENT_PANEL.equals(parent.getClass().getName()) && parent.getLayout() instanceof BorderLayout)
				return parent;
		}
		return null;
	}

	@Nullable
	private static Component centreOf(Container host) {
		var layout = (BorderLayout) host.getLayout();
		return layout.getLayoutComponent(BorderLayout.CENTER);
	}


	private void popOutWindow() {
		Window parent = SwingUtilities.getWindowAncestor(client.getCanvas());
		window = new JDialog(parent, "117 HD — pick a region", JDialog.ModalityType.MODELESS);
		window.setContentPane(content);
		window.setSize(WINDOW_SIZE);
		window.setLocationRelativeTo(parent);
		window.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				if (window != null) {
					window = null;
					close();
				}
			}
		});

		poppedOut = true;
		window.setVisible(true);
	}

	private void closeWindow() {
		if (window == null)
			return;
		var closing = window;
		window = null;
		closing.setContentPane(new JPanel());
		closing.dispose();
	}
}
