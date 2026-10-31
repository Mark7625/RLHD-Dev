package rs117.hd.dev.sceneloader.devtools;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import rs117.hd.dev.sceneloader.scene.VariantObject;

@Singleton
public class SelectionHighlight extends Overlay {
	private static final Color OUTLINE = new Color(0x00E0FF);
	private static final Color SHADE = new Color(0x2200E0FF, true);
	private static final int TILE_SIZE = 128;
	private static final long SHOWN_FOR_MILLIS = 8000;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	private boolean added;

	@Nullable
	private volatile VariantObject.Placed target;
	private volatile String label = "";
	private volatile long until;

	public SelectionHighlight() {
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	public void startUp() {
		if (added)
			return;
		added = true;
		overlayManager.add(this);
	}

	public void shutDown() {
		if (!added)
			return;
		added = false;
		clear();
		overlayManager.remove(this);
	}

	public void mark(VariantObject.Placed placed, String label) {
		this.target = placed;
		this.label = label;
		this.until = System.currentTimeMillis() + SHOWN_FOR_MILLIS;
	}

	public void clear() {
		target = null;
	}

	@Override
	public Dimension render(Graphics2D g) {
		var placed = target;
		if (placed == null)
			return null;
		if (System.currentTimeMillis() > until) {
			target = null;
			return null;
		}

		int halfX = placed.sizeX * TILE_SIZE / 2;
		int halfY = placed.sizeY * TILE_SIZE / 2;

		Point southWest = project(placed.x - halfX, placed.z - halfY, placed.y);
		Point southEast = project(placed.x + halfX, placed.z - halfY, placed.y);
		Point northEast = project(placed.x + halfX, placed.z + halfY, placed.y);
		Point northWest = project(placed.x - halfX, placed.z + halfY, placed.y);
		if (southWest == null || southEast == null || northEast == null || northWest == null)
			return null;

		var footprint = new Polygon();
		footprint.addPoint(southWest.getX(), southWest.getY());
		footprint.addPoint(southEast.getX(), southEast.getY());
		footprint.addPoint(northEast.getX(), northEast.getY());
		footprint.addPoint(northWest.getX(), northWest.getY());

		g.setColor(SHADE);
		g.fill(footprint);
		g.setColor(OUTLINE);
		g.setStroke(new BasicStroke(2));
		g.draw(footprint);

		if (!label.isEmpty()) {
			int top = Math.min(Math.min(southWest.getY(), southEast.getY()), Math.min(northEast.getY(), northWest.getY()));
			int middle = (southWest.getX() + northEast.getX()) / 2;
			g.setFont(FontManager.getRunescapeSmallFont());
			int width = g.getFontMetrics().stringWidth(label);
			g.setColor(Color.BLACK);
			g.drawString(label, middle - width / 2 + 1, top - 5);
			g.setColor(OUTLINE);
			g.drawString(label, middle - width / 2, top - 6);
		}
		return null;
	}

	@Nullable
	private Point project(int x, int z, int height) {
		return Perspective.localToCanvas(client, x, z, height);
	}
}
