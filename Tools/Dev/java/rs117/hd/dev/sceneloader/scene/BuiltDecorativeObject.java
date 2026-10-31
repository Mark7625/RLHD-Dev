package rs117.hd.dev.sceneloader.scene;

import net.runelite.api.DecorativeObject;
import net.runelite.api.Renderable;

public class BuiltDecorativeObject extends BuiltTileObject implements DecorativeObject {
	private Renderable renderable, renderable2;
	private int xOffset, yOffset;
	private final int xOffset2, yOffset2;
	private final int wallSides;
	private final int secondOrientation;

	public BuiltDecorativeObject(
		BuiltScene scene, long hash, int config, int x, int y, int z, int plane,
		Renderable renderable, Renderable renderable2,
		int wallSides, int secondOrientation,
		int xOffset, int yOffset, int xOffset2, int yOffset2
	) {
		super(scene, hash, config, x, y, z, plane);
		this.renderable = renderable;
		this.renderable2 = renderable2;
		this.wallSides = wallSides;
		this.secondOrientation = secondOrientation;
		this.xOffset = xOffset;
		this.yOffset = yOffset;
		this.xOffset2 = xOffset2;
		this.yOffset2 = yOffset2;
	}

	public int getWallSides() {
		return wallSides;
	}

	public int getSecondOrientation() {
		return secondOrientation;
	}

	public void displace(int displacement) {
		if (xOffset != 0)
			xOffset = xOffset < 0 ? -displacement : displacement;
		if (yOffset != 0)
			yOffset = yOffset < 0 ? -displacement : displacement;
	}

	@Override
	public Renderable getRenderable() {
		return renderable;
	}

	@Override
	public Renderable getRenderable2() {
		return renderable2;
	}

	public void setRenderable(Renderable renderable) {
		this.renderable = renderable;
	}

	public void setRenderable2(Renderable renderable) {
		renderable2 = renderable;
	}

	@Override
	public int getXOffset() {
		return xOffset;
	}

	@Override
	public int getYOffset() {
		return yOffset;
	}

	@Override
	public int getXOffset2() {
		return xOffset2;
	}

	@Override
	public int getYOffset2() {
		return yOffset2;
	}
}
