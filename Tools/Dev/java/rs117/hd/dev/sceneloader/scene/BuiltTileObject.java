package rs117.hd.dev.sceneloader.scene;

import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public abstract class BuiltTileObject implements TileObject {
	public static final int HASH_ID_SHIFT = 20;

	protected final BuiltScene scene;
	protected final long hash;
	protected final int config;
	protected final int x, y, z;
	protected int plane;

	protected BuiltTileObject(BuiltScene scene, long hash, int config, int x, int y, int z, int plane) {
		this.scene = scene;
		this.hash = hash;
		this.config = config;
		this.x = x;
		this.y = y;
		this.z = z;
		this.plane = plane;
	}

	public int getConfig() {
		return config;
	}

	public int getType() {
		return config & 0x3F;
	}

	public int getRotation() {
		return config >> 6 & 3;
	}

	@Override
	public long getHash() {
		return hash;
	}

	@Override
	public int getId() {
		return (int) (hash >>> HASH_ID_SHIFT & 0xFFFFFFFFL);
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public int getY() {
		return y;
	}

	@Override
	public int getZ() {
		return z;
	}

	@Override
	public int getPlane() {
		return plane;
	}

	public void setPlane(int plane) {
		this.plane = plane;
	}

	@Override
	public WorldView getWorldView() {
		return null;
	}

	@Nonnull
	@Override
	public WorldPoint getWorldLocation() {
		var local = getLocalLocation();
		return new WorldPoint(
			scene.getBaseX() + local.getSceneX(),
			scene.getBaseY() + local.getSceneY(),
			plane
		);
	}

	@Nonnull
	@Override
	public LocalPoint getLocalLocation() {
		return new LocalPoint(x, y);
	}


	@Nullable
	@Override
	public Point getCanvasLocation() {
		return null;
	}

	@Nullable
	@Override
	public Point getCanvasLocation(int zOffset) {
		return null;
	}

	@Nullable
	@Override
	public Polygon getCanvasTilePoly() {
		return null;
	}

	@Nullable
	@Override
	public Point getCanvasTextLocation(Graphics2D graphics, String text, int zOffset) {
		return null;
	}

	@Nullable
	@Override
	public Point getMinimapLocation() {
		return null;
	}

	@Nullable
	@Override
	public Shape getClickbox() {
		return null;
	}

	@Nullable
	@Override
	public String getOpOverride(int index) {
		return null;
	}

	@Override
	public boolean isOpShown(int index) {
		return false;
	}

	@Nullable
	public Shape getConvexHull() {
		return null;
	}

	@Nullable
	public Shape getConvexHull2() {
		return null;
	}
}
