package rs117.hd.dev.sceneloader.scene;

import net.runelite.api.Renderable;
import net.runelite.api.WallObject;

public class BuiltWallObject extends BuiltTileObject implements WallObject {
	private Renderable renderable1, renderable2;
	private final int orientationA, orientationB;

	public BuiltWallObject(
		BuiltScene scene, long hash, int config, int x, int y, int z, int plane,
		Renderable renderable1, Renderable renderable2, int orientationA, int orientationB
	) {
		super(scene, hash, config, x, y, z, plane);
		this.renderable1 = renderable1;
		this.renderable2 = renderable2;
		this.orientationA = orientationA;
		this.orientationB = orientationB;
	}

	@Override
	public int getOrientationA() {
		return orientationA;
	}

	@Override
	public int getOrientationB() {
		return orientationB;
	}

	@Override
	public Renderable getRenderable1() {
		return renderable1;
	}

	@Override
	public Renderable getRenderable2() {
		return renderable2;
	}

	public void setRenderable1(Renderable renderable) {
		renderable1 = renderable;
	}

	public void setRenderable2(Renderable renderable) {
		renderable2 = renderable;
	}
}
