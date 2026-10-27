package rs117.hd.gui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;

/**
 * Base class for a standardized dev-tools panel. Declare content via {@link #buildContent()},
 * then register()/unregister() and open()/close() it. It can present in RuneLite's sidebar, below
 * the client canvas, or as its own floating window, switching at runtime via popOut()/popIn()
 * without losing content.
 * <p>
 * RuneLite exposes no API to collapse a single sidebar panel on command, so {@link #close()} is a
 * no-op while presented as {@link DockType#SIDEBAR}.
 */
public abstract class HdDevFrame
{
	@Inject
	protected Client client;

	@Inject
	protected ClientToolbar clientToolbar;

	@Inject
	protected ClientUI clientUi;

	private final String title;
	private final BufferedImage icon;
	private final int priority;
	private final boolean canDeDock;

	private DockType type;
	private DockType homeType;
	private JComponent content;
	private NavigationButton navButton;
	private HdDevFrameBottomPanel bottomPanel;
	private HdDevFrameWindow window;
	private boolean registered;
	private boolean open;

	protected HdDevFrame(String title, BufferedImage icon, DockType defaultType, int priority)
	{
		this(title, icon, defaultType, priority, true);
	}

	protected HdDevFrame(String title, BufferedImage icon, DockType defaultType, int priority, boolean canDeDock)
	{
		this.title = title;
		this.icon = icon != null ? icon : createPlaceholderIcon(title);
		this.type = defaultType;
		this.homeType = defaultType;
		this.priority = priority;
		this.canDeDock = canDeDock;
	}

	protected abstract JComponent buildContent();

	protected void onRegister() {}

	protected void onUnregister() {}

	public final DockType getType()
	{
		return type;
	}

	public final DockType getHomeType()
	{
		return homeType;
	}

	public final boolean isRegistered()
	{
		return registered;
	}

	public final boolean isOpen()
	{
		return open;
	}

	public final void register()
	{
		runOnEdt(() -> {
			if (registered)
				return;

			if (content == null)
				content = buildContent();

			if (type == DockType.SIDEBAR)
				addSidebarPresentation();
			else if (type == DockType.BOTTOM)
				addBottomPresentation();

			registered = true;
			onRegister();
		});
	}

	public final void unregister()
	{
		runOnEdt(() -> {
			if (!registered)
				return;

			onUnregister();
			detachFromCurrentPresentation();

			if (window != null)
			{
				window.dispose();
				window = null;
			}

			content = null;
			registered = false;
			open = false;
		});
	}

	public final void open()
	{
		runOnEdt(() -> {
			if (!registered)
				register();

			if (type == DockType.SIDEBAR)
			{
				clientToolbar.openPanel(navButton);
			}
			else if (type == DockType.WINDOW)
			{
				if (window == null)
					window = createWindow();
				window.setVisible(true);
				window.toFront();
			}
			else if (type == DockType.BOTTOM && bottomPanel != null)
			{
				setBottomPanelVisible(true);
			}
			open = true;
		});
	}

	public final void open(DockType presentAs)
	{
		runOnEdt(() -> {
			if (presentAs != type)
			{
				if (presentAs == DockType.WINDOW)
				{
					popOut();
				}
				else if (type == DockType.WINDOW)
				{
					homeType = presentAs;
					popIn();
				}
				else
				{
					detachFromCurrentPresentation();
					type = presentAs;
					homeType = presentAs;
					if (registered)
						attachToHomePresentation();
				}
			}
			open();
		});
	}

	public final void close()
	{
		runOnEdt(() -> {
			if (type == DockType.WINDOW && window != null)
			{
				window.setVisible(false);
				open = false;
			}
			else if (type == DockType.BOTTOM && bottomPanel != null)
			{
				setBottomPanelVisible(false);
				open = false;
			}
		});
	}

	public final void popOut()
	{
		runOnEdt(() -> {
			if (!canDeDock || type == DockType.WINDOW)
				return;

			if (!registered)
				register();

			detachFromCurrentPresentation();

			type = DockType.WINDOW;
			window = createWindow();
			window.setVisible(true);
			window.toFront();
			open = true;
		});
	}

	public final void popIn()
	{
		runOnEdt(() -> {
			if (type != DockType.WINDOW)
				return;

			if (window != null)
			{
				window.dispose();
				window = null;
			}

			type = homeType;
			if (registered)
				attachToHomePresentation();
			open = false;
		});
	}

	private void attachToHomePresentation()
	{
		if (homeType == DockType.SIDEBAR)
			addSidebarPresentation();
		else if (homeType == DockType.BOTTOM)
			addBottomPresentation();
	}

	private void detachFromCurrentPresentation()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		removeBottomPresentation();
	}

	private void addSidebarPresentation()
	{
		HdDevFrameSidebarPanel panel = new HdDevFrameSidebarPanel(title, content, canDeDock ? this::popOut : null);
		navButton = NavigationButton.builder()
			.tooltip(title)
			.icon(icon)
			.priority(priority)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	private void addBottomPresentation()
	{
		bottomPanel = new HdDevFrameBottomPanel(title, content, canDeDock ? this::popOut : null);
		HdDevFrameBottomDock.add(clientUi, bottomPanel);
	}

	private void removeBottomPresentation()
	{
		if (bottomPanel != null)
		{
			HdDevFrameBottomDock.remove(clientUi, bottomPanel);
			bottomPanel = null;
		}
	}

	private void setBottomPanelVisible(boolean visible)
	{
		bottomPanel.setVisible(visible);
		bottomPanel.revalidate();
		if (bottomPanel.getParent() != null)
			bottomPanel.getParent().repaint();
	}

	private HdDevFrameWindow createWindow()
	{
		return new HdDevFrameWindow(title, content, client.getCanvas(), this::popIn);
	}

	protected static void runOnEdt(Runnable r)
	{
		if (SwingUtilities.isEventDispatchThread())
			r.run();
		else
			SwingUtilities.invokeLater(r);
	}

	private static BufferedImage createPlaceholderIcon(String title)
	{
		int size = 24;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(89, 74, 60));
		g.fillRoundRect(0, 0, size, size, 6, 6);
		g.setColor(Color.WHITE);
		g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 14f));
		String letter = title == null || title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase();
		FontMetrics fm = g.getFontMetrics();
		int x = (size - fm.stringWidth(letter)) / 2;
		int y = (size - fm.getHeight()) / 2 + fm.getAscent();
		g.drawString(letter, x, y);
		g.dispose();
		return image;
	}
}
