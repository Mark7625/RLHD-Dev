package rs117.hd.dev.sceneloader.devtools;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import rs117.hd.HdPlugin;
import rs117.hd.dev.sceneloader.SceneLoader;
import rs117.hd.dev.sceneloader.scene.BuiltScene;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.MAX_Z;

/**
 * Draws what RuneLite's scene tools would draw, for the scene that is actually on screen.
 *
 * <p>Its buttons are RuneLite's own, read through {@link DevToolBridge}, so Walls still means walls and
 * turning one off still turns it off. Only the two things RuneLite's own drawing cannot get right here are
 * done differently: the tiles come from the cache scene rather than from the client, and the projection uses
 * the camera the frame was drawn with rather than the client's, which stays wherever the player is standing
 * while this one is flown about.
 */
@Singleton
public class SceneToolsOverlay extends Overlay {
	private static final Color GAME_OBJECTS = Color.GREEN;
	private static final Color WALLS = new Color(0x00, 0xB0, 0xFF);
	private static final Color DECORATIONS = new Color(0xD0, 0x60, 0xFF);
	private static final Color GROUND_OBJECTS = new Color(0xFF, 0xA0, 0x30);
	private static final Color MAP_SQUARES = new Color(0xFF, 0xFF, 0x00, 0xA0);
	private static final Color TILE_LOCATION = Color.WHITE;
	/** Red, the same as RuneLite draws its tile flags in */
	private static final Color TILE_FLAGS = Color.RED;

	private static final int TILE_SIZE = 128;
	private static final int REGION_TILES = 64;
	/** How far out to bother drawing, since every tile has to be projected by hand */
	private static final int DRAW_RADIUS_TILES = 28;

	@Inject
	private Client client;

	@Inject
	private HdPlugin plugin;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SceneLoader tools;

	@Inject
	private DevToolBridge devTools;

	private boolean added;

	public SceneToolsOverlay() {
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
		overlayManager.remove(this);
	}

	@Override
	public Dimension render(Graphics2D g) {
		var scene = tools.getLoaded();
		if (scene == null || !devTools.isStandingIn())
			return null;

		g.setFont(FontManager.getRunescapeSmallFont());

		boolean gameObjects = devTools.isActive("gameObjects");
		boolean walls = devTools.isActive("walls");
		boolean decorations = devTools.isActive("decorations");
		boolean groundObjects = devTools.isActive("groundObjects");
		boolean tileLocation = devTools.isActive("tileLocation");
		boolean tileFlags = devTools.isActive("tileFlags");

		if (gameObjects || walls || decorations || groundObjects || tileLocation || tileFlags)
			drawTiles(g, scene, gameObjects, walls, decorations, groundObjects, tileLocation, tileFlags);

		if (devTools.isActive("mapSquares"))
			drawMapSquares(g, scene);

		return null;
	}

	private void drawTiles(
		Graphics2D g, BuiltScene scene,
		boolean gameObjects, boolean walls, boolean decorations, boolean groundObjects,
		boolean tileLocation, boolean tileFlags
	) {
		var tiles = scene.getExtendedTiles();
		int plane = Math.max(0, Math.min(MAX_Z - 1, client.getPlane()));

		int fromX = cameraTileX() - DRAW_RADIUS_TILES;
		int toX = cameraTileX() + DRAW_RADIUS_TILES;
		int fromY = cameraTileY() - DRAW_RADIUS_TILES;
		int toY = cameraTileY() + DRAW_RADIUS_TILES;

		for (int x = Math.max(0, fromX); x <= Math.min(EXTENDED_SCENE_SIZE - 1, toX); x++) {
			for (int y = Math.max(0, fromY); y <= Math.min(EXTENDED_SCENE_SIZE - 1, toY); y++) {
				Tile tile = tiles[plane][x][y];
				if (tile == null)
					continue;

				if (gameObjects)
					for (var object : tile.getGameObjects())
						mark(g, scene, plane, x, y, object, GAME_OBJECTS);
				if (walls)
					mark(g, scene, plane, x, y, tile.getWallObject(), WALLS);
				if (decorations)
					mark(g, scene, plane, x, y, tile.getDecorativeObject(), DECORATIONS);
				if (groundObjects)
					mark(g, scene, plane, x, y, tile.getGroundObject(), GROUND_OBJECTS);
				if (tileLocation)
					label(g, scene, plane, x, y, (x - scene.getSceneOffset()) + ", " + (y - scene.getSceneOffset()),
						TILE_LOCATION);
				// The same settings byte RuneLite reads off the client, taken from this scene instead
				if (tileFlags)
					label(g, scene, plane, x, y,
						String.valueOf(scene.getExtendedTileSettings()[plane][x][y]), TILE_FLAGS);
			}
		}
	}

	private void mark(
		Graphics2D g, BuiltScene scene, int plane, int x, int y, @Nullable TileObject object, Color color
	) {
		if (object == null)
			return;

		var poly = tilePoly(scene, plane, x, y);
		if (poly == null)
			return;

		g.setColor(color);
		g.drawPolygon(poly);
		label(g, scene, plane, x, y, String.valueOf(object.getId()), color);
	}

	private void label(Graphics2D g, BuiltScene scene, int plane, int x, int y, String text, Color color) {
		float[] middle = project(
			(x - scene.getSceneOffset()) * TILE_SIZE + TILE_SIZE / 2,
			(y - scene.getSceneOffset()) * TILE_SIZE + TILE_SIZE / 2,
			heightAt(scene, plane, x, y)
		);
		if (middle == null)
			return;

		int width = g.getFontMetrics().stringWidth(text);
		g.setColor(Color.BLACK);
		g.drawString(text, middle[0] - width / 2f + 1, middle[1] + 1);
		g.setColor(color);
		g.drawString(text, middle[0] - width / 2f, middle[1]);
	}

	private void drawMapSquares(Graphics2D g, BuiltScene scene) {
		g.setColor(MAP_SQUARES);
		int offset = scene.getSceneOffset();

		// Where the regions fall is a property of the world, so the lines go wherever a region edge lands
		for (int x = -offset; x <= EXTENDED_SCENE_SIZE - offset; x++) {
			if (Math.floorMod(scene.getBaseX() + x, REGION_TILES) != 0)
				continue;
			drawSceneLine(g, scene, x, -offset, x, EXTENDED_SCENE_SIZE - offset);
		}
		for (int y = -offset; y <= EXTENDED_SCENE_SIZE - offset; y++) {
			if (Math.floorMod(scene.getBaseY() + y, REGION_TILES) != 0)
				continue;
			drawSceneLine(g, scene, -offset, y, EXTENDED_SCENE_SIZE - offset, y);
		}
	}

	private void drawSceneLine(Graphics2D g, BuiltScene scene, int fromX, int fromY, int toX, int toY) {
		int plane = Math.max(0, Math.min(MAX_Z - 1, client.getPlane()));
		int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
		float[] previous = null;

		for (int i = 0; i <= steps; i++) {
			int x = fromX + (toX - fromX) * i / steps;
			int y = fromY + (toY - fromY) * i / steps;
			float[] point = project(
				x * TILE_SIZE, y * TILE_SIZE,
				heightAt(scene, plane, x + scene.getSceneOffset(), y + scene.getSceneOffset())
			);
			if (previous != null && point != null)
				g.drawLine((int) previous[0], (int) previous[1], (int) point[0], (int) point[1]);
			previous = point;
		}
	}

	private int cameraTileX() {
		return (int) Math.floor(plugin.cameraPosition[0] / TILE_SIZE) + BuiltScene.SCENE_OFFSET;
	}

	private int cameraTileY() {
		return (int) Math.floor(plugin.cameraPosition[2] / TILE_SIZE) + BuiltScene.SCENE_OFFSET;
	}

	private static int heightAt(BuiltScene scene, int plane, int x, int y) {
		int[][][] heights = scene.getTileHeights();
		if (x < 0 || y < 0 || x >= heights[plane].length || y >= heights[plane][x].length)
			return 0;
		return heights[plane][x][y];
	}

	@Nullable
	private Polygon tilePoly(BuiltScene scene, int plane, int x, int y) {
		int offset = scene.getSceneOffset();
		int west = (x - offset) * TILE_SIZE;
		int east = west + TILE_SIZE;
		int south = (y - offset) * TILE_SIZE;
		int north = south + TILE_SIZE;

		float[] sw = project(west, south, heightAt(scene, plane, x, y));
		float[] se = project(east, south, heightAt(scene, plane, x + 1, y));
		float[] ne = project(east, north, heightAt(scene, plane, x + 1, y + 1));
		float[] nw = project(west, north, heightAt(scene, plane, x, y + 1));
		if (sw == null || se == null || ne == null || nw == null)
			return null;

		var poly = new Polygon();
		poly.addPoint((int) sw[0], (int) sw[1]);
		poly.addPoint((int) se[0], (int) se[1]);
		poly.addPoint((int) ne[0], (int) ne[1]);
		poly.addPoint((int) nw[0], (int) nw[1]);
		return poly;
	}

	/**
	 * Local scene coordinates to the canvas, through the camera the scene was drawn with.
	 *
	 * <p>The client's own camera is no use here: it stays where the player is standing while this tool's
	 * camera is flown elsewhere, and everything would be placed from there instead of from what is on
	 * screen. 117 HD keeps what it was handed, which is what was actually drawn.
	 */
	@Nullable
	private float[] project(int x, int y, int height) {
		float dx = x - plugin.cameraPosition[0];
		float dy = y - plugin.cameraPosition[2];
		float dz = height - plugin.cameraPosition[1];

		float yawSin = (float) Math.sin(plugin.cameraOrientation[0]);
		float yawCos = (float) Math.cos(plugin.cameraOrientation[0]);
		float pitchSin = (float) Math.sin(plugin.cameraOrientation[1]);
		float pitchCos = (float) Math.cos(plugin.cameraOrientation[1]);

		float x1 = dx * yawCos + dy * yawSin;
		float y1 = dy * yawCos - dx * yawSin;
		float y2 = dz * pitchCos - y1 * pitchSin;
		float z1 = y1 * pitchCos + dz * pitchSin;
		if (z1 < 1)
			return null;

		float scale = client.getScale();
		return new float[] {
			x1 * scale / z1 + client.getViewportWidth() / 2f + client.getViewportXOffset(),
			y2 * scale / z1 + client.getViewportHeight() / 2f + client.getViewportYOffset()
		};
	}
}
