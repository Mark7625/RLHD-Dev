package rs117.hd.dev.sceneloader.standalone.client;

import javax.annotation.Nullable;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;
import rs117.hd.dev.sceneloader.scene.Definitions;
import rs117.hd.dev.sceneloader.scene.HslPalette;
import rs117.hd.dev.sceneloader.standalone.Textures;

public final class CacheTextureProvider {
	private final Definitions defs;
	private final Textures textures;

	private final Texture[] byId;
	private double brightness = HslPalette.DEFAULT_BRIGHTNESS;

	public CacheTextureProvider(Definitions defs, Textures textures) {
		this.defs = defs;
		this.textures = textures;

		int highest = 0;
		for (int id : textures.ids())
			highest = Math.max(highest, id);

		byId = new Texture[highest + 1];
		for (int id : textures.ids())
			byId[id] = Fake.of(Texture.class, new OneTexture(id));
	}

	public TextureProvider asProvider() {
		return Fake.of(TextureProvider.class, this);
	}

	public double getBrightness() {
		return brightness;
	}

	public void setBrightness(double brightness) {
		this.brightness = brightness;
	}

	public Texture[] getTextures() {
		return byId;
	}

	@Nullable
	public int[] load(int id) {
		return textures.get(id, brightness);
	}

	public int getDefaultColor(int id) {
		return defs.textureColor(id);
	}

	private final class OneTexture {
		private final int id;
		private float u, v;

		OneTexture(int id) {
			this.id = id;
		}

		public int[] getPixels() {
			return load(id);
		}

		public int getAnimationDirection() {
			var definition = defs.texture(id);
			return definition == null ? 0 : definition.animationDirection;
		}

		public int getAnimationSpeed() {
			var definition = defs.texture(id);
			return definition == null ? 0 : definition.animationSpeed;
		}

		public boolean isLoaded() {
			return true;
		}

		public float getU() {
			return u;
		}

		public void setU(float u) {
			this.u = u;
		}

		public float getV() {
			return v;
		}

		public void setV(float v) {
			this.v = v;
		}
	}
}
