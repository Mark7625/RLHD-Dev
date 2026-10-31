package rs117.hd.dev.sceneloader.scene;

import java.util.ArrayDeque;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Node;
import net.runelite.api.Scene;
import javax.annotation.Nullable;
import net.runelite.api.Tile;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.MAX_Z;
import static net.runelite.api.Constants.SCENE_SIZE;

public class BuiltScene implements Scene {
	public static final int SCENE_OFFSET = (EXTENDED_SCENE_SIZE - SCENE_SIZE) / 2;

	private final int baseX, baseY;
	private final int worldViewId;

	private final BuiltTile[][][] extendedTiles = new BuiltTile[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
	private final Tile[][][] tiles = new Tile[MAX_Z][SCENE_SIZE][SCENE_SIZE];

	private final int[][][] tileHeights = new int[MAX_Z][EXTENDED_SCENE_SIZE + 1][EXTENDED_SCENE_SIZE + 1];
	private final byte[][][] extendedTileSettings = new byte[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
	private final short[][][] underlayIds = new short[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
	private final short[][][] overlayIds = new short[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
	private final byte[][][] tileShapes = new byte[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];
	private final int[][][] roofs = new int[MAX_Z][EXTENDED_SCENE_SIZE][EXTENDED_SCENE_SIZE];

	private final int[] mapRegions;

	private int drawDistance = 25;
	private int minLevel;
	private int roofRemovalMode;

	private int modelHeight;

	public BuiltScene(int baseX, int baseY, int worldViewId, int[] mapRegions) {
		this.baseX = baseX;
		this.baseY = baseY;
		this.worldViewId = worldViewId;
		this.mapRegions = mapRegions;
	}

	public int getSceneOffset() {
		return SCENE_OFFSET;
	}

	public BuiltTile tile(int plane, int extendedX, int extendedY) {
		BuiltTile tile = extendedTiles[plane][extendedX][extendedY];
		if (tile == null) {
			tile = new BuiltTile(this, plane, extendedX, extendedY);
			setTile(plane, extendedX, extendedY, tile);
		}
		return tile;
	}

	public BuiltTile tileOrNull(int plane, int extendedX, int extendedY) {
		return extendedTiles[plane][extendedX][extendedY];
	}

	public void setTile(int plane, int extendedX, int extendedY, BuiltTile tile) {
		extendedTiles[plane][extendedX][extendedY] = tile;

		int sceneX = extendedX - SCENE_OFFSET;
		int sceneY = extendedY - SCENE_OFFSET;
		if (sceneX >= 0 && sceneX < SCENE_SIZE && sceneY >= 0 && sceneY < SCENE_SIZE)
			tiles[plane][sceneX][sceneY] = tile;
	}

	@Override
	public Tile[][][] getTiles() {
		return tiles;
	}

	@Override
	public Tile[][][] getExtendedTiles() {
		return extendedTiles;
	}

	@Override
	public byte[][][] getExtendedTileSettings() {
		return extendedTileSettings;
	}

	@Override
	public int getDrawDistance() {
		return drawDistance;
	}

	@Override
	public void setDrawDistance(int drawDistance) {
		this.drawDistance = drawDistance;
	}

	@Override
	public int getWorldViewId() {
		return worldViewId;
	}

	@Override
	public int getMinLevel() {
		return minLevel;
	}

	@Override
	public void setMinLevel(int minLevel) {
		this.minLevel = minLevel;
	}

	@Override
	public void removeTile(Tile tile) {
		if (!(tile instanceof BuiltTile))
			return;
		BuiltTile cacheTile = (BuiltTile) tile;
		setTile(cacheTile.getPlane(), cacheTile.getExtendedX(), cacheTile.getExtendedY(), null);
	}

	@Override
	public void removeGameObject(GameObject gameObject) {
		for (int plane = 0; plane < MAX_Z; plane++) {
			for (int x = 0; x < EXTENDED_SCENE_SIZE; x++) {
				for (int y = 0; y < EXTENDED_SCENE_SIZE; y++) {
					BuiltTile tile = extendedTiles[plane][x][y];
					if (tile == null)
						continue;
					GameObject[] objects = tile.getGameObjects();
					for (int i = 0; i < objects.length; i++)
						if (objects[i] == gameObject)
							objects[i] = null;
				}
			}
		}
	}

	private boolean isRoofed(int plane, int x, int y) {
		return (extendedTileSettings[plane][x][y] & 4) != 0;
	}

	@Override
	public void buildRoofs() {
		for (int plane = 0; plane < MAX_Z; plane++)
			for (int x = 0; x < EXTENDED_SCENE_SIZE; x++)
				java.util.Arrays.fill(roofs[plane][x], 0);

		int nextRoofId = 1;
		for (int plane = MAX_Z - 1; plane >= 0; plane--) {
			for (int y = 0; y < EXTENDED_SCENE_SIZE; y++) {
				for (int x = 0; x < EXTENDED_SCENE_SIZE; x++) {
					if (extendedTiles[plane][x][y] == null || roofs[plane][x][y] != 0 || !isRoofed(plane, x, y))
						continue;
					floodRoof(plane, x, y, nextRoofId++);
				}
			}
		}
	}

	private void floodRoof(int startPlane, int startX, int startY, int roofId) {
		var queue = new ArrayDeque<int[]>();
		queue.add(new int[] { startPlane, startX, startY });

		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			int plane = at[0], x = at[1], y = at[2];
			if (roofs[plane][x][y] != 0)
				continue;

			if (isRoofed(plane, x, y)) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						if (dx == 0 && dy == 0)
							continue;
						int nx = x + dx;
						int ny = y + dy;
						if (nx < 0 || ny < 0 || nx >= EXTENDED_SCENE_SIZE || ny >= EXTENDED_SCENE_SIZE)
							continue;
						if (extendedTiles[plane][nx][ny] != null)
							queue.add(new int[] { plane, nx, ny });
					}
				}
			}

			roofs[plane][x][y] = roofId;

			for (int above = plane + 1, distance = 1; above < MAX_Z; above++, distance++) {
				if (roofs[above][x][y] != 0)
					break;
				roofs[above][x][y] = roofId | distance << 28;
			}
		}
	}

	private final java.util.List<Dynamic> dynamics = new java.util.ArrayList<>();

	public static final class Dynamic {
		public final net.runelite.api.TileObject tileObject;
		public final BuiltDynamicObject renderable;
		public final int orientation;
		public final int x, y, z;

		public Dynamic(
			net.runelite.api.TileObject tileObject, BuiltDynamicObject renderable,
			int orientation, int x, int y, int z
		) {
			this.tileObject = tileObject;
			this.renderable = renderable;
			this.orientation = orientation;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	public void addDynamic(Dynamic dynamic) {
		dynamics.add(dynamic);
	}

	public java.util.List<Dynamic> getDynamics() {
		return dynamics;
	}

	@Override
	public int[][][] getRoofs() {
		return roofs;
	}

	@Override
	public void setRoofRemovalMode(int flags) {
		roofRemovalMode = flags;
	}

	@Override
	public int getRoofRemovalMode() {
		return roofRemovalMode;
	}

	@Override
	public short[][][] getUnderlayIds() {
		return underlayIds;
	}

	@Override
	public short[][][] getOverlayIds() {
		return overlayIds;
	}

	@Override
	public byte[][][] getTileShapes() {
		return tileShapes;
	}

	@Override
	public int[][][] getTileHeights() {
		return tileHeights;
	}

	@Override
	public int getBaseX() {
		return baseX;
	}

	@Override
	public int getBaseY() {
		return baseY;
	}

	public static final int CHUNKS_PER_SIDE = EXTENDED_SCENE_SIZE / 8;

	@Nullable
	private int[][][] instanceTemplateChunks;

	public void setInstanceTemplateChunks(int[][][] instanceTemplateChunks) {
		this.instanceTemplateChunks = instanceTemplateChunks;
	}

	@Override
	public boolean isInstance() {
		return instanceTemplateChunks != null;
	}

	@Nullable
	@Override
	public int[][][] getInstanceTemplateChunks() {
		return instanceTemplateChunks;
	}

	@Override
	public int[] getMapRegions() {
		return mapRegions;
	}

	@Override
	public byte getOverrideAmount() {
		return 0;
	}

	@Override
	public byte getOverrideHue() {
		return 0;
	}

	@Override
	public byte getOverrideSaturation() {
		return 0;
	}

	@Override
	public byte getOverrideLuminance() {
		return 0;
	}

	@Override
	public Model getSkybox() {
		return null;
	}


	@Override
	public Model getModel() {
		return null;
	}

	@Override
	public int getModelHeight() {
		return modelHeight;
	}

	@Override
	public void setModelHeight(int modelHeight) {
		this.modelHeight = modelHeight;
	}

	@Override
	public int getAnimationHeightOffset() {
		return 0;
	}

	@Override
	public int getRenderMode() {
		return RENDERMODE_DEFAULT;
	}


	@Override
	public Node getNext() {
		return null;
	}

	@Override
	public Node getPrevious() {
		return null;
	}

	@Override
	public long getHash() {
		return 0;
	}
}
