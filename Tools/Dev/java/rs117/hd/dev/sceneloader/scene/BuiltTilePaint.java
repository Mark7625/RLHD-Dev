package rs117.hd.dev.sceneloader.scene;

import net.runelite.api.SceneTilePaint;

public class BuiltTilePaint implements SceneTilePaint {
	private final int rgb;
	private int swColor, seColor, neColor, nwColor;
	private int texture;
	private final boolean flat;

	private int bufferOffset, uvBufferOffset, bufferLen;

	public BuiltTilePaint(int swColor, int seColor, int neColor, int nwColor, int texture, int rgb, boolean flat) {
		this.swColor = swColor;
		this.seColor = seColor;
		this.neColor = neColor;
		this.nwColor = nwColor;
		this.texture = texture;
		this.rgb = rgb;
		this.flat = flat;
	}

	@Override
	public int getRBG() {
		return rgb;
	}

	@Override
	public int getSwColor() {
		return swColor;
	}

	@Override
	public void setSwColor(int color) {
		swColor = color;
	}

	@Override
	public int getSeColor() {
		return seColor;
	}

	@Override
	public void setSeColor(int color) {
		seColor = color;
	}

	@Override
	public int getNwColor() {
		return nwColor;
	}

	@Override
	public void setNwColor(int color) {
		nwColor = color;
	}

	@Override
	public int getNeColor() {
		return neColor;
	}

	@Override
	public void setNeColor(int color) {
		neColor = color;
	}

	@Override
	public int getTexture() {
		return texture;
	}

	@Override
	public void setTexture(int texture) {
		this.texture = texture;
	}

	@Override
	public boolean isFlat() {
		return flat;
	}

	@Override
	public int getBufferOffset() {
		return bufferOffset;
	}

	@Override
	public void setBufferOffset(int bufferOffset) {
		this.bufferOffset = bufferOffset;
	}

	@Override
	public int getUvBufferOffset() {
		return uvBufferOffset;
	}

	@Override
	public void setUvBufferOffset(int bufferOffset) {
		uvBufferOffset = bufferOffset;
	}

	@Override
	public int getBufferLen() {
		return bufferLen;
	}

	@Override
	public void setBufferLen(int bufferLen) {
		this.bufferLen = bufferLen;
	}
}
