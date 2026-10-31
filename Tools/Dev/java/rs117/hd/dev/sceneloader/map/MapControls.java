package rs117.hd.dev.sceneloader.map;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import static rs117.hd.dev.sceneloader.map.MapProjection.REGION_SIZE;


class MapControls extends JPanel {
	private static final int NARROW = 44;
	private static final int WIDE = 58;

	private final RegionMapPanel map;

	private final JTextField worldX = field(WIDE, "The world tile under the mouse. Type one and press enter to go there.");
	private final JTextField worldY = field(WIDE, "The world tile under the mouse. Type one and press enter to go there.");
	private final JTextField regionId = field(WIDE, "The region under the mouse. Type one and press enter to go there.");
	private final JTextField regionX = field(NARROW, "Which region across. Type one and press enter to go there.");
	private final JTextField regionY = field(NARROW, "Which region up. Type one and press enter to go there.");
	private final JTextField baseX = readOnly(WIDE, "The world tile the region starts at");
	private final JTextField baseY = readOnly(WIDE, "The world tile the region starts at");
	private final JTextField localX = readOnly(NARROW, "How far into the region, across");
	private final JTextField localY = readOnly(NARROW, "How far into the region, up");
	private final JComboBox<String> plane = new JComboBox<>(new String[] { "0", "1", "2", "3" });
	private final JButton popOut = new JButton();
	private final JButton clearPicked = new JButton("Clear");
	private final MapWindow window;

	interface MapWindow {
		void togglePopOut();

		void close();

		boolean isPoppedOut();
	}

	MapControls(RegionMapPanel map, MapWindow window) {
		this.map = map;
		this.window = window;

		setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

		add(label("X"));
		add(worldX);
		add(label("Y"));
		add(worldY);
		add(gap());

		add(label("Region"));
		add(regionId);
		add(label("at"));
		add(regionX);
		add(label(","));
		add(regionY);
		add(gap());

		add(label("Base"));
		add(baseX);
		add(label(","));
		add(baseY);
		add(gap());

		add(label("Local"));
		add(localX);
		add(label(","));
		add(localY);
		add(gap());

		add(label("Plane"));
		plane.setFont(FontManager.getRunescapeSmallFont());
		plane.setToolTipText("Which floor of the map to show");
		plane.addActionListener(e -> map.setPlane(plane.getSelectedIndex()));
		add(plane);
		add(gap());

		popOut.setFont(FontManager.getRunescapeSmallFont());
		popOut.addActionListener(e -> {
			window.togglePopOut();
			updatePopOut();
		});
		add(popOut);

		clearPicked.setFont(FontManager.getRunescapeSmallFont());
		clearPicked.setToolTipText("Forget the picked regions and go back to choosing one at a time");
		clearPicked.addActionListener(e -> map.clearPicked());
		add(clearPicked);

		var close = new JButton("Close");
		close.setFont(FontManager.getRunescapeSmallFont());
		close.setToolTipText("Put the game view back");
		close.addActionListener(e -> window.close());
		add(close);

		updatePopOut();

		onEnter(worldX, this::goToWorld);
		onEnter(worldY, this::goToWorld);
		onEnter(regionId, this::goToRegionId);
		onEnter(regionX, this::goToRegionCoords);
		onEnter(regionY, this::goToRegionCoords);

		map.setPointerListener(this::follow);
		map.setPickingListener(this::updatePicking);
		updatePicking();
	}

	/** The button is only worth having while there is something picked to forget */
	private void updatePicking() {
		clearPicked.setVisible(map.isPicking());
	}

	private void updatePopOut() {
		boolean out = window.isPoppedOut();
		popOut.setText(out ? "Over game" : "Pop out");
		popOut.setToolTipText(out
			? "Put the map over the game view, where it has the most room"
			: "Move the map into a window of its own, so the game can be seen behind it");
	}

	private void follow(int tileX, int tileY) {
		int region = MapProjection.regionIdAt(tileX, tileY);
		int rx = MapProjection.regionX(region);
		int ry = MapProjection.regionY(region);

		set(worldX, tileX);
		set(worldY, tileY);
		set(regionId, region);
		set(regionX, rx);
		set(regionY, ry);
		set(baseX, rx * REGION_SIZE);
		set(baseY, ry * REGION_SIZE);
		set(localX, tileX - rx * REGION_SIZE);
		set(localY, tileY - ry * REGION_SIZE);
	}

	private static void set(JTextField field, int value) {
		if (field.isFocusOwner())
			return;
		field.setText(Integer.toString(value));
	}

	private void goToWorld() {
		Integer x = number(worldX);
		Integer y = number(worldY);
		if (x == null || y == null)
			return;
		map.goTo(x, y);
	}

	private void goToRegionId() {
		Integer id = number(regionId);
		if (id != null)
			map.select(id);
	}

	private void goToRegionCoords() {
		Integer x = number(regionX);
		Integer y = number(regionY);
		if (x == null || y == null || x < 0 || x > 0xFF || y < 0 || y > 0xFF)
			return;
		map.select(x << 8 | y);
	}

	@Nullable
	private static Integer number(JTextField field) {
		try {
			return Integer.parseInt(field.getText().trim());
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static void onEnter(JTextField field, Runnable action) {
		field.addActionListener(e -> action.run());
	}

	private static JTextField field(int width, String tooltip) {
		var field = new JTextField();
		field.setFont(FontManager.getRunescapeSmallFont());
		field.setToolTipText(tooltip);
		field.setHorizontalAlignment(JTextField.CENTER);
		field.setPreferredSize(new Dimension(width, 20));
		return field;
	}

	private static JTextField readOnly(int width, String tooltip) {
		var field = field(width, tooltip);
		field.setEditable(false);
		field.setFocusable(false);
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.LIGHT_GRAY);
		return field;
	}

	private static JLabel label(String text) {
		var label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.LIGHT_GRAY);
		return label;
	}

	private static java.awt.Component gap() {
		return Box.createHorizontalStrut(10);
	}
}
