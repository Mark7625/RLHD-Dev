package rs117.hd.dev.sceneloader.map;

public final class MapProjection {
	public static final int TILE_SIZE = 256;
	public static final int MIN_ZOOM = 4;
	public static final int MAX_ZOOM = 11;
	public static final int WORLD_TILE_PX = 32;
	public static final int REGION_SIZE = 64;
	public static final int REGION_PX = REGION_SIZE * WORLD_TILE_PX;

	private static final int VERTICAL_ORIGIN_PX = 364544;
	private static final int WORLD_X_ORIGIN = 960;
	private static final int WORLD_Y_ORIGIN = 6208;

	public static final int MIN_WORLD_X = WORLD_X_ORIGIN;
	public static final int MAX_WORLD_X = 4224;
	public static final int MIN_WORLD_Y = 1216;
	public static final int MAX_WORLD_Y = 12608;

	private MapProjection() {}

	public static int pixelX(int worldX) {
		return (worldX - WORLD_X_ORIGIN) * WORLD_TILE_PX;
	}

	public static int pixelY(int worldY) {
		return VERTICAL_ORIGIN_PX - (worldY - WORLD_Y_ORIGIN) * WORLD_TILE_PX;
	}

	public static int worldX(double pixelX) {
		return (int) Math.floor(pixelX / WORLD_TILE_PX) + WORLD_X_ORIGIN;
	}

	public static int worldY(double pixelY) {
		return (int) Math.floor((VERTICAL_ORIGIN_PX - pixelY) / WORLD_TILE_PX) + WORLD_Y_ORIGIN;
	}

	public static int scale(int zoom) {
		return 1 << (MAX_ZOOM - zoom);
	}

	public static int regionPx(int zoom) {
		return REGION_PX / scale(zoom);
	}

	public static int regionIdAt(int worldX, int worldY) {
		return (worldX / REGION_SIZE) << 8 | worldY / REGION_SIZE;
	}

	public static int regionX(int regionId) {
		return regionId >> 8 & 0xFF;
	}

	public static int regionY(int regionId) {
		return regionId & 0xFF;
	}

	public static boolean isOnMap(int regionId) {
		int worldX = regionX(regionId) * REGION_SIZE;
		int worldY = regionY(regionId) * REGION_SIZE;
		return worldX >= MIN_WORLD_X && worldX < MAX_WORLD_X
			   && worldY >= MIN_WORLD_Y && worldY < MAX_WORLD_Y;
	}

	public static int tileIndex(double pixel, int zoom) {
		return (int) Math.floor(pixel / scale(zoom) / TILE_SIZE);
	}

	public static int flipTileY(int tileY, int zoom) {
		return (1 << zoom) - 1 - tileY;
	}
}
