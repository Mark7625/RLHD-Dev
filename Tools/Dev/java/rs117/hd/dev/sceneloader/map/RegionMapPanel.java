package rs117.hd.dev.sceneloader.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

import static rs117.hd.dev.sceneloader.map.MapProjection.MAX_ZOOM;
import static rs117.hd.dev.sceneloader.map.MapProjection.MIN_ZOOM;
import static rs117.hd.dev.sceneloader.map.MapProjection.REGION_SIZE;
import static rs117.hd.dev.sceneloader.map.MapProjection.TILE_SIZE;


public class RegionMapPanel extends JPanel {
	private static final int INITIAL_ZOOM = 6;
	private static final int GRID_VISIBLE_PX = 24;

	private static final Color HOVER_FILL = new Color(0x33, 0xB5, 0xE5, 90);
	private static final Color HOVER_LINE = new Color(0x33, 0xB5, 0xE5);
	private static final Color CHOSEN_LINE = new Color(0xFF, 0xA0, 0x30);
	private static final Color GRID_LINE = new Color(255, 255, 255, 40);
	private static final Color COVERAGE_FILL = new Color(0xFF, 0xA0, 0x30, 36);
	private static final Color COVERAGE_LINE = new Color(0xFF, 0xC8, 0x70, 190);
	private static final Color BUILT_LINE = new Color(0x7C, 0xE8, 0x7C);
	private static final BasicStroke COVERAGE_STROKE = new BasicStroke(
		2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 6, 4 }, 0);

	private static final Color LISTED_FILL = new Color(0x33, 0xE0, 0xFF, 0x33);
	private static final Color LISTED_LINE = new Color(0x33, 0xE0, 0xFF);

	private final MapTileCache tiles;
	private final IntConsumer onChosen;
	private final IntConsumer onOpened;
	private final JLabel status;

	private int plane;
	private int zoom = INITIAL_ZOOM;
	private double centreX, centreY;

	private int chosenRegion;
	private int hoveredRegion = -1;
	@Nullable
	private IntConsumer onAdded;
	@Nullable
	private Runnable onCleared;
	@Nullable
	private Runnable pickingListener;
	private int[] listedRegions = new int[0];
	private Point dragFrom;

	@Nullable
	private PointerListener pointerListener;

	public interface PointerListener {
		void moved(int worldX, int worldY);
	}

	@Nullable
	private Coverage coverage;

	public interface Coverage {
		@Nullable
		int[] tilesFor(int regionId);
	}

	public void setOnAdded(@Nullable IntConsumer onAdded) {
		this.onAdded = onAdded;
	}

	public void setOnCleared(@Nullable Runnable onCleared) {
		this.onCleared = onCleared;
	}

	public void clearPicked() {
		if (onCleared != null)
			onCleared.run();
	}

	public void setListed(int[] listedRegions) {
		this.listedRegions = listedRegions;
		if (pickingListener != null)
			pickingListener.run();
		describe();
		repaint();
	}

	public void setPickingListener(@Nullable Runnable pickingListener) {
		this.pickingListener = pickingListener;
	}

	/** Whether more than one region is picked, which is when the map stops being a single choice */
	public boolean isPicking() {
		return listedRegions.length > 1;
	}

	public void setCoverage(@Nullable Coverage coverage) {
		this.coverage = coverage;
		describe();
		repaint();
	}

	public RegionMapPanel(int startRegion, IntConsumer onChosen, IntConsumer onOpened, JLabel status) {
		this.onChosen = onChosen;
		this.onOpened = onOpened;
		this.status = status;
		// Where it opens looking, not what it has picked. Nothing is picked until something is clicked.
		this.chosenRegion = -1;
		this.tiles = new MapTileCache(this::repaintLater);

		centreOn(startRegion);

		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setFocusable(true);

		var mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				dragFrom = event.getPoint();
				requestFocusInWindow();
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				dragFrom = null;
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				if (dragFrom == null)
					return;
				int scale = MapProjection.scale(zoom);
				centreX -= (double) (event.getX() - dragFrom.x) * scale;
				centreY -= (double) (event.getY() - dragFrom.y) * scale;
				dragFrom = event.getPoint();
				updatePointer(event.getPoint());
				repaint();
			}

			@Override
			public void mouseMoved(MouseEvent event) {
				updatePointer(event.getPoint());
			}

			@Override
			public void mouseExited(MouseEvent event) {
				hoveredRegion = -1;
				describe();
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent event) {
				int region = regionAt(event.getPoint());
				if (region < 0)
					return;

				if (event.isControlDown() && onAdded != null) {
					onAdded.accept(region);
					describe();
					repaint();
					return;
				}

				// While several are picked the map is a list to edit, not a place to go, so an ordinary
				// click would either be ignored or throw the picking away. It does nothing instead.
				if (isPicking())
					return;

				chosenRegion = region;
				onChosen.accept(region);
				describe();
				repaint();

				if (event.getClickCount() >= 2)
					onOpened.accept(region);
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent event) {
				zoomBy(-event.getWheelRotation(), event.getPoint());
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(mouse);
	}

	public void shutDown() {
		tiles.shutDown();
	}

	public void setPlane(int plane) {
		this.plane = plane;
		repaint();
	}

	private void repaintLater() {
		SwingUtilities.invokeLater(this::repaint);
	}

	public void goTo(int worldX, int worldY) {
		centreX = MapProjection.pixelX(worldX);
		centreY = MapProjection.pixelY(worldY);
		repaint();
	}

	public void select(int regionId) {
		if (!MapProjection.isOnMap(regionId))
			return;
		chosenRegion = regionId;
		onChosen.accept(regionId);
		describe();
		centreOn(regionId);
	}

	public void centreOn(int regionId) {
		centreX = MapProjection.pixelX(MapProjection.regionX(regionId) * REGION_SIZE + REGION_SIZE / 2);
		centreY = MapProjection.pixelY(MapProjection.regionY(regionId) * REGION_SIZE + REGION_SIZE / 2);
		repaint();
	}

	private void zoomBy(int steps, Point at) {
		int wanted = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + steps));
		if (wanted == zoom)
			return;

		double beforeX = pixelAtX(at.x);
		double beforeY = pixelAtY(at.y);
		zoom = wanted;
		centreX += beforeX - pixelAtX(at.x);
		centreY += beforeY - pixelAtY(at.y);

		updatePointer(at);
		repaint();
	}

	private double pixelAtX(int screenX) {
		return centreX + (screenX - getWidth() / 2.0) * MapProjection.scale(zoom);
	}

	private double pixelAtY(int screenY) {
		return centreY + (screenY - getHeight() / 2.0) * MapProjection.scale(zoom);
	}

	public void setPointerListener(@Nullable PointerListener listener) {
		this.pointerListener = listener;
	}

	private void updatePointer(Point at) {
		if (pointerListener != null) {
			int worldX = MapProjection.worldX(pixelAtX(at.x));
			int worldY = MapProjection.worldY(pixelAtY(at.y));
			if (worldX >= 0 && worldY >= 0)
				pointerListener.moved(worldX, worldY);
		}
		updateHover(at);
	}

	private void updateHover(Point at) {
		int region = regionAt(at);
		if (region == hoveredRegion)
			return;
		hoveredRegion = region;
		describe();
		repaint();
	}

	private int regionAt(Point at) {
		int worldX = MapProjection.worldX(pixelAtX(at.x));
		int worldY = MapProjection.worldY(pixelAtY(at.y));
		if (worldX < 0 || worldY < 0)
			return -1;

		int region = MapProjection.regionIdAt(worldX, worldY);
		return MapProjection.isOnMap(region) ? region : -1;
	}

	private void describe() {
		if (status == null)
			return;

		String hint;
		if (onAdded == null) {
			hint = "Drag to move, scroll to zoom, double click a region to load it";
		} else if (isPicking()) {
			hint = "Ctrl click to pick a region or take one back, Clear to go back to one at a time";
		} else {
			hint = "Drag to move, scroll to zoom, double click to load, ctrl click to pick several";
		}
		String here = hoveredRegion < 0
			? hint
			: String.format(
				"Region %d  (%d, %d)  at %d, %d",
				hoveredRegion,
				MapProjection.regionX(hoveredRegion), MapProjection.regionY(hoveredRegion),
				MapProjection.regionX(hoveredRegion) * REGION_SIZE,
				MapProjection.regionY(hoveredRegion) * REGION_SIZE
			);
		if (hoveredRegion >= 0 && isPicking())
			here += "   —  " + listedRegions.length + " picked, ctrl click to "
					+ (isListed(hoveredRegion) ? "take it back" : "add it");
		status.setText(here + coverageNote());
	}

	/**
	 * What loading the region under the mouse would pull in.
	 *
	 * <p>It follows the pointer rather than whatever is chosen, so it reads as a preview of the region being
	 * considered. Pinned to the chosen one it painted a large overlay the moment the map opened, over a
	 * region nobody had picked, which looks like a selection that was never made.
	 */
	@Nullable
	private int[] coverageTiles() {
		if (coverage == null || hoveredRegion < 0)
			return null;
		return coverage.tilesFor(hoveredRegion);
	}

	private String coverageNote() {
		int[] tiles = coverageTiles();
		if (tiles == null)
			return "";

		int across = (tiles[2] >> 6) - (tiles[0] >> 6) + 1;
		int up = (tiles[3] >> 6) - (tiles[1] >> 6) + 1;
		return String.format(
			"     Loading %d builds %d x %d tiles, x %d to %d and y %d to %d, reading %d x %d regions (%d)",
			hoveredRegion,
			tiles[2] - tiles[0] + 1, tiles[3] - tiles[1] + 1,
			tiles[0], tiles[2], tiles[1], tiles[3],
			across, up, across * up
		);
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		var g = (Graphics2D) graphics.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			drawTiles(g);
			drawGrid(g);
			drawCoverage(g);
			for (int listed : listedRegions)
				drawRegion(g, listed, LISTED_FILL, LISTED_LINE, 2);
			if (!isPicking())
				drawRegion(g, chosenRegion, null, CHOSEN_LINE, 2);
			if (hoveredRegion >= 0 && hoveredRegion != chosenRegion)
				drawRegion(g, hoveredRegion, HOVER_FILL, HOVER_LINE, 1);
			else if (hoveredRegion >= 0)
				drawRegion(g, hoveredRegion, HOVER_FILL, CHOSEN_LINE, 2);
		} finally {
			g.dispose();
		}
	}

	private void drawTiles(Graphics2D g) {
		int scale = MapProjection.scale(zoom);
		int onScreen = TILE_SIZE;

		int firstX = MapProjection.tileIndex(pixelAtX(0), zoom);
		int lastX = MapProjection.tileIndex(pixelAtX(getWidth()), zoom);
		int firstY = MapProjection.tileIndex(pixelAtY(0), zoom);
		int lastY = MapProjection.tileIndex(pixelAtY(getHeight()), zoom);

		for (int tileX = firstX; tileX <= lastX; tileX++) {
			for (int tileY = firstY; tileY <= lastY; tileY++) {
				if (tileX < 0 || tileY < 0)
					continue;

				BufferedImage image = tiles.get(plane, zoom, tileX, MapProjection.flipTileY(tileY, zoom));
				if (image == null)
					continue;

				int screenX = screenXOf((double) tileX * TILE_SIZE * scale);
				int screenY = screenYOf((double) tileY * TILE_SIZE * scale);
				g.drawImage(image, screenX, screenY, onScreen, onScreen, null);
			}
		}
	}

	private void drawCoverage(Graphics2D g) {
		int[] tiles = coverageTiles();
		if (tiles == null)
			return;

		fillTiles(g,
			(tiles[0] >> 6) * REGION_SIZE, (tiles[1] >> 6) * REGION_SIZE,
			((tiles[2] >> 6) + 1) * REGION_SIZE - 1, ((tiles[3] >> 6) + 1) * REGION_SIZE - 1,
			COVERAGE_FILL, COVERAGE_LINE, new BasicStroke(1));

		fillTiles(g, tiles[0], tiles[1], tiles[2], tiles[3], null, BUILT_LINE, COVERAGE_STROKE);
	}

	private void fillTiles(
		Graphics2D g, int firstX, int firstY, int lastX, int lastY,
		@Nullable Color fill, Color line, BasicStroke stroke
	) {
		int left = screenXOf(MapProjection.pixelX(firstX));
		int right = screenXOf(MapProjection.pixelX(lastX + 1));
		int bottom = screenYOf(MapProjection.pixelY(firstY));
		int top = screenYOf(MapProjection.pixelY(lastY + 1));

		if (fill != null) {
			g.setColor(fill);
			g.fillRect(left, top, right - left, bottom - top);
		}
		g.setColor(line);
		g.setStroke(stroke);
		g.drawRect(left, top, right - left, bottom - top);
	}

	private void drawGrid(Graphics2D g) {
		int regionPx = MapProjection.regionPx(zoom);
		if (regionPx < GRID_VISIBLE_PX)
			return;

		g.setColor(GRID_LINE);
		g.setStroke(new BasicStroke(1));

		int firstX = Math.floorDiv(MapProjection.worldX(pixelAtX(0)), REGION_SIZE);
		int lastX = Math.floorDiv(MapProjection.worldX(pixelAtX(getWidth())), REGION_SIZE);
		for (int x = firstX; x <= lastX + 1; x++) {
			int screenX = screenXOf(MapProjection.pixelX(x * REGION_SIZE));
			g.drawLine(screenX, 0, screenX, getHeight());
		}

		int firstY = Math.floorDiv(MapProjection.worldY(pixelAtY(getHeight())), REGION_SIZE);
		int lastY = Math.floorDiv(MapProjection.worldY(pixelAtY(0)), REGION_SIZE);
		for (int y = firstY; y <= lastY + 1; y++) {
			int screenY = screenYOf(MapProjection.pixelY(y * REGION_SIZE));
			g.drawLine(0, screenY, getWidth(), screenY);
		}
	}

	private boolean isListed(int region) {
		for (int listed : listedRegions)
			if (listed == region)
				return true;
		return false;
	}

	private void drawRegion(Graphics2D g, int regionId, Color fill, Color line, int thickness) {
		if (regionId < 0)
			return;

		int worldX = MapProjection.regionX(regionId) * REGION_SIZE;
		int worldY = MapProjection.regionY(regionId) * REGION_SIZE;

		int left = screenXOf(MapProjection.pixelX(worldX));
		int right = screenXOf(MapProjection.pixelX(worldX + REGION_SIZE));
		int bottom = screenYOf(MapProjection.pixelY(worldY));
		int top = screenYOf(MapProjection.pixelY(worldY + REGION_SIZE));

		if (fill != null) {
			g.setColor(fill);
			g.fillRect(left, top, right - left, bottom - top);
		}
		g.setColor(line);
		g.setStroke(new BasicStroke(thickness));
		g.drawRect(left, top, right - left, bottom - top);
	}

	private int screenXOf(double pixelX) {
		return (int) Math.round((pixelX - centreX) / MapProjection.scale(zoom) + getWidth() / 2.0);
	}

	private int screenYOf(double pixelY) {
		return (int) Math.round((pixelY - centreY) / MapProjection.scale(zoom) + getHeight() / 2.0);
	}

}
