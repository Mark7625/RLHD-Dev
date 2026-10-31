package rs117.hd.dev.sceneloader.standalone.client;

import java.awt.Canvas;
import java.awt.Dimension;
import java.util.Collections;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.TextureProvider;
import net.runelite.api.WorldView;
import net.runelite.api.hooks.DrawCallbacks;
import rs117.hd.dev.sceneloader.scene.BuiltScene;

public final class FakeClient {
	public static final int ZOOM = 512;

	private final Canvas canvas;
	private final TextureProvider textureProvider;
	private final FakeObjects objects;

	private final FakeWorldView topLevel = new FakeWorldView();
	private final WorldView worldView = Fake.of(WorldView.class, topLevel);

	private int[] uiPixels = new int[0];
	private int uiWidth, uiHeight;
	private final BufferProvider bufferProvider = Fake.of(BufferProvider.class, new Object() {
		public int[] getPixels() {
			return uiPixels;
		}

		public int getWidth() {
			return uiWidth;
		}

		public int getHeight() {
			return uiHeight;
		}
	});

	private Thread clientThread;
	private DrawCallbacks drawCallbacks;
	private GameState gameState = GameState.LOGGED_IN;
	private int gpuFlags;
	private int gameCycle;

	private final float[] camera = new float[3];
	private float cameraPitch, cameraYaw;
	private int plane;

	public FakeClient(Canvas canvas, TextureProvider textureProvider, FakeObjects objects) {
		this.canvas = canvas;
		this.textureProvider = textureProvider;
		this.objects = objects;
	}

	public void ownedBy(Thread thread) {
		clientThread = thread;
	}

	public void setScene(@Nullable BuiltScene scene) {
		topLevel.scene = scene;
	}

	public void setCamera(float[] position, float yaw, float pitch, int plane) {
		System.arraycopy(position, 0, camera, 0, 3);
		cameraYaw = yaw;
		cameraPitch = pitch;
		this.plane = plane;
		topLevel.plane = plane;
	}

	public void tick() {
		gameCycle++;
	}

	public void sizeUi(int width, int height) {
		if (uiWidth == width && uiHeight == height)
			return;
		uiWidth = width;
		uiHeight = height;
		uiPixels = new int[Math.max(1, width * height)];
	}

	public DrawCallbacks getDrawCallbacks() {
		return drawCallbacks;
	}

	public Canvas getCanvas() {
		return canvas;
	}

	public int getCanvasWidth() {
		return Math.max(1, canvas.getWidth());
	}

	public int getCanvasHeight() {
		return Math.max(1, canvas.getHeight());
	}

	public boolean isResized() {
		return true;
	}

	public boolean isStretchedEnabled() {
		return false;
	}

	public Dimension getStretchedDimensions() {
		return new Dimension(getCanvasWidth(), getCanvasHeight());
	}

	public int getViewportWidth() {
		return getCanvasWidth();
	}

	public int getViewportHeight() {
		return getCanvasHeight();
	}

	public int getViewportXOffset() {
		return 0;
	}

	public int getViewportYOffset() {
		return 0;
	}

	public int getCenterX() {
		return getCanvasWidth() / 2;
	}

	public int getCenterY() {
		return getCanvasHeight() / 2;
	}

	public int getScale() {
		return ZOOM;
	}

	public int get3dZoom() {
		return ZOOM;
	}

	public int getRasterizer3D_clipNegativeMidX() {
		return -getCenterX();
	}

	public int getRasterizer3D_clipNegativeMidY() {
		return -getCenterY();
	}

	public int getRasterizer3D_clipMidX2() {
		return getCanvasWidth() - getCenterX();
	}

	public int getRasterizer3D_clipMidY2() {
		return getCanvasHeight() - getCenterY();
	}

	public BufferProvider getBufferProvider() {
		return bufferProvider;
	}

	public TextureProvider getTextureProvider() {
		return textureProvider;
	}

	public boolean isClientThread() {
		return Thread.currentThread() == clientThread;
	}

	public GameState getGameState() {
		return gameState;
	}

	public void setGameState(GameState gameState) {
		this.gameState = gameState;
	}

	public int getGameCycle() {
		return gameCycle;
	}

	public WorldView getTopLevelWorldView() {
		return worldView;
	}

	public WorldView getWorldView(int id) {
		return id == WorldView.TOPLEVEL || id == -1 ? worldView : null;
	}

	public Scene getScene() {
		return topLevel.scene;
	}

	public int getPlane() {
		return plane;
	}

	public int[] getMapRegions() {
		return topLevel.scene == null ? new int[0] : topLevel.scene.getMapRegions();
	}

	public int getCameraX() {
		return (int) camera[0];
	}

	public int getCameraY() {
		return (int) camera[1];
	}

	public int getCameraZ() {
		return (int) camera[2];
	}

	public float getCameraFocalPointX() {
		return camera[0];
	}

	public float getCameraFocalPointY() {
		return camera[1];
	}

	public float getCameraFocalPointZ() {
		return camera[2];
	}

	public float getCameraFpX() {
		return camera[0];
	}

	public float getCameraFpY() {
		return camera[1];
	}

	public float getCameraFpZ() {
		return camera[2];
	}

	public float getCameraFpPitch() {
		return cameraPitch;
	}

	public float getCameraFpYaw() {
		return cameraYaw;
	}

	public int getCameraYaw() {
		return (int) (cameraYaw / (2 * Math.PI) * 2048) & 2047;
	}

	public int getCameraPitch() {
		return (int) (cameraPitch / (2 * Math.PI) * 2048);
	}

	public void setDrawCallbacks(DrawCallbacks drawCallbacks) {
		this.drawCallbacks = drawCallbacks;
	}

	public void setGpuFlags(int gpuFlags) {
		this.gpuFlags = gpuFlags;
	}

	public int getGpuFlags() {
		return gpuFlags;
	}

	public Point getMouseCanvasPosition() {
		return new Point(-1, -1);
	}

	@Nullable
	public net.runelite.api.ObjectComposition getObjectDefinition(int id) {
		return objects.get(id);
	}

	public int getSkyboxColor() {
		return 0;
	}

	public java.util.List<?> getDBTableRows(int table) {
		return java.util.List.of();
	}

	public static final IndexedObjectSet<?> NOBODY = new IndexedObjectSet<>() {
		@Override
		public Object byIndex(int index) {
			return null;
		}

		@Override
		public Iterator<Object> iterator() {
			return Collections.emptyIterator();
		}
	};
}
