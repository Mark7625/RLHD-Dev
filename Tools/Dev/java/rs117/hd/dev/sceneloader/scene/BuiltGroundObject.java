package rs117.hd.dev.sceneloader.scene;

import net.runelite.api.GroundObject;
import net.runelite.api.Renderable;

public class BuiltGroundObject extends BuiltTileObject implements GroundObject {
	private Renderable renderable;

	public BuiltGroundObject(
		BuiltScene scene, long hash, int config, int x, int y, int z, int plane, Renderable renderable
	) {
		super(scene, hash, config, x, y, z, plane);
		this.renderable = renderable;
	}

	@Override
	public Renderable getRenderable() {
		return renderable;
	}

	public void setRenderable(Renderable renderable) {
		this.renderable = renderable;
	}
}
