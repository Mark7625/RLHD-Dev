package rs117.hd.dev.sceneloader.scene;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.Point;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;

public class BuiltTile implements Tile {
	public static final int MAX_GAME_OBJECTS = 5;

	private final BuiltScene scene;
	private final int extendedX, extendedY;
	private int plane;
	private boolean onBridge;

	private SceneTilePaint paint;
	private SceneTileModel model;

	private final GameObject[] gameObjects = new GameObject[MAX_GAME_OBJECTS];
	private WallObject wallObject;
	private DecorativeObject decorativeObject;
	private GroundObject groundObject;

	@Nullable
	private BuiltTile bridge;

	public BuiltTile(BuiltScene scene, int plane, int extendedX, int extendedY) {
		this.scene = scene;
		this.plane = plane;
		this.extendedX = extendedX;
		this.extendedY = extendedY;
	}

	public int getExtendedX() {
		return extendedX;
	}

	public int getExtendedY() {
		return extendedY;
	}

	public int getSceneX() {
		return extendedX - scene.getSceneOffset();
	}

	public int getSceneY() {
		return extendedY - scene.getSceneOffset();
	}

	@Override
	public DecorativeObject getDecorativeObject() {
		return decorativeObject;
	}

	public void setDecorativeObject(DecorativeObject decorativeObject) {
		this.decorativeObject = decorativeObject;
	}

	@Override
	public GameObject[] getGameObjects() {
		return gameObjects;
	}

	public int gameObjectCount() {
		int count = 0;
		for (GameObject object : gameObjects)
			if (object != null)
				count++;
		return count;
	}

	public boolean addGameObject(GameObject gameObject) {
		for (int i = 0; i < gameObjects.length; i++) {
			if (gameObjects[i] == null) {
				gameObjects[i] = gameObject;
				return true;
			}
		}
		return false;
	}

	@Override
	public ItemLayer getItemLayer() {
		return null;
	}

	@Override
	public GroundObject getGroundObject() {
		return groundObject;
	}

	@Override
	public void setGroundObject(GroundObject groundObject) {
		this.groundObject = groundObject;
	}

	@Override
	public WallObject getWallObject() {
		return wallObject;
	}

	public void setWallObject(WallObject wallObject) {
		this.wallObject = wallObject;
	}

	@Override
	public SceneTilePaint getSceneTilePaint() {
		return paint;
	}

	@Override
	public void setSceneTilePaint(SceneTilePaint paint) {
		this.paint = paint;
	}

	@Override
	public SceneTileModel getSceneTileModel() {
		return model;
	}

	@Override
	public void setSceneTileModel(SceneTileModel model) {
		this.model = model;
	}

	@Override
	public WorldPoint getWorldLocation() {
		return new WorldPoint(scene.getBaseX() + getSceneX(), scene.getBaseY() + getSceneY(), plane);
	}

	@Override
	public Point getSceneLocation() {
		return new Point(getSceneX(), getSceneY());
	}

	@Override
	public LocalPoint getLocalLocation() {
		int half = LOCAL_TILE_SIZE / 2;
		return new LocalPoint(getSceneX() * LOCAL_TILE_SIZE + half, getSceneY() * LOCAL_TILE_SIZE + half);
	}

	@Override
	public int getPlane() {
		return plane;
	}

	public void setPlane(int plane) {
		this.plane = plane;
	}

	@Override
	public int getRenderLevel() {
		return plane + (onBridge ? 1 : 0) & 3;
	}

	public void setOnBridge(boolean onBridge) {
		this.onBridge = onBridge;
	}

	@Override
	public List<TileItem> getGroundItems() {
		return Collections.emptyList();
	}

	@Override
	public Tile getBridge() {
		return bridge;
	}

	public void setBridge(@Nullable BuiltTile bridge) {
		this.bridge = bridge;
	}
}
