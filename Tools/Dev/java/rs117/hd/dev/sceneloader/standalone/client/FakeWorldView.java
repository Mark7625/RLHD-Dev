package rs117.hd.dev.sceneloader.standalone.client;

import javax.annotation.Nullable;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import rs117.hd.dev.sceneloader.scene.BuiltScene;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;

public final class FakeWorldView {
	@Nullable
	BuiltScene scene;
	int plane;

	public int getId() {
		return WorldView.TOPLEVEL;
	}

	public boolean isTopLevel() {
		return true;
	}

	@Nullable
	public Scene getScene() {
		return scene;
	}

	public int getPlane() {
		return plane;
	}

	public int getSizeX() {
		return EXTENDED_SCENE_SIZE;
	}

	public int getSizeY() {
		return EXTENDED_SCENE_SIZE;
	}

	public int getBaseX() {
		return scene == null ? 0 : scene.getBaseX();
	}

	public int getBaseY() {
		return scene == null ? 0 : scene.getBaseY();
	}

	public IndexedObjectSet<?> players() {
		return FakeClient.NOBODY;
	}

	public IndexedObjectSet<?> npcs() {
		return FakeClient.NOBODY;
	}

	public IndexedObjectSet<?> worldEntities() {
		return FakeClient.NOBODY;
	}
}
