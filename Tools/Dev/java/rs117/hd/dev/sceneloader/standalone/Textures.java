package rs117.hd.dev.sceneloader.standalone;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.providers.SpriteProvider;
import rs117.hd.dev.sceneloader.scene.Definitions;

@Slf4j
public final class Textures {
	public static final int SIZE = 128;

	private final Definitions defs;
	private final SpriteManager sprites;
	private final Map<Double, Map<Integer, int[]>> atBrightness = new HashMap<>();

	public Textures(Definitions defs) throws IOException {
		this.defs = defs;
		sprites = new SpriteManager(defs.getStore());
		sprites.load();
	}

	@Nullable
	public int[] get(int textureId, double brightness) {
		var byId = atBrightness.computeIfAbsent(brightness, b -> new HashMap<>());
		if (byId.containsKey(textureId))
			return byId.get(textureId);

		int[] built = null;
		try {
			built = build(textureId, brightness);
		} catch (Exception | Error ex) {
			log.debug("Ignoring texture {}, which would not build", textureId, ex);
		}
		byId.put(textureId, built);
		return built;
	}

	@Nullable
	private int[] build(int textureId, double brightness) {
		TextureDefinition texture = defs.texture(textureId);
		if (texture == null)
			return null;

		var lender = new Lender();
		try {
			texture.method2680(brightness, SIZE, lender);
			return texture.pixels == null ? null : texture.pixels.clone();
		} finally {
			lender.putBack();
			texture.pixels = null;
		}
	}

	public List<Integer> ids() {
		var ids = new ArrayList<Integer>();
		for (var texture : defs.textures())
			ids.add(texture.getId());
		return ids;
	}

	private final class Lender implements SpriteProvider {
		private final List<SpriteDefinition> lent = new ArrayList<>();
		private final List<int[]> palettes = new ArrayList<>();

		@Override
		public SpriteDefinition provide(int spriteId, int frameId) {
			SpriteDefinition sprite = sprites.findSprite(spriteId, frameId);
			if (sprite != null && sprite.palette != null) {
				lent.add(sprite);
				palettes.add(sprite.palette.clone());
			}
			return sprite;
		}

		void putBack() {
			for (int i = 0; i < lent.size(); i++) {
				int[] was = palettes.get(i);
				System.arraycopy(was, 0, lent.get(i).palette, 0, was.length);
			}
		}
	}
}
