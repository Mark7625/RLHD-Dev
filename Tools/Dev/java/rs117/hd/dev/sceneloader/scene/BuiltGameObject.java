package rs117.hd.dev.sceneloader.scene;

import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.api.Renderable;

public class BuiltGameObject extends BuiltTileObject implements GameObject {
	private static final int ROTATION_UNIT = 512;

	private Renderable renderable;
	private final int modelOrientation;
	private final int minSceneX, minSceneY, maxSceneX, maxSceneY;

	public BuiltGameObject(
		BuiltScene scene, long hash, int config, int x, int y, int z, int plane,
		Renderable renderable, int modelOrientation,
		int minSceneX, int minSceneY, int maxSceneX, int maxSceneY
	) {
		super(scene, hash, config, x, y, z, plane);
		this.renderable = renderable;
		this.modelOrientation = modelOrientation;
		this.minSceneX = minSceneX;
		this.minSceneY = minSceneY;
		this.maxSceneX = maxSceneX;
		this.maxSceneY = maxSceneY;
	}

	@Override
	public int sizeX() {
		return maxSceneX - minSceneX + 1;
	}

	@Override
	public int sizeY() {
		return maxSceneY - minSceneY + 1;
	}

	@Override
	public Point getSceneMinLocation() {
		return new Point(minSceneX, minSceneY);
	}

	@Override
	public Point getSceneMaxLocation() {
		return new Point(maxSceneX, maxSceneY);
	}

	@Override
	public int getOrientation() {
		return getRotation() * ROTATION_UNIT + modelOrientation;
	}

	@Override
	public Renderable getRenderable() {
		return renderable;
	}

	public void setRenderable(Renderable renderable) {
		this.renderable = renderable;
	}

	@Override
	public int getModelOrientation() {
		return modelOrientation;
	}
}
