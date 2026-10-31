package rs117.hd.dev.sceneloader;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Projection;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameTick;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import rs117.hd.HdPlugin;
import rs117.hd.dev.sceneloader.devtools.CameraOverlay;
import rs117.hd.dev.sceneloader.devtools.DevToolBridge;
import rs117.hd.dev.sceneloader.devtools.LocationOverlay;
import rs117.hd.dev.sceneloader.devtools.SceneToolsOverlay;
import rs117.hd.dev.sceneloader.devtools.SelectionHighlight;
import rs117.hd.dev.sceneloader.scene.BuiltScene;
import rs117.hd.dev.sceneloader.scene.Definitions;
import rs117.hd.dev.sceneloader.scene.SceneBuilder;
import rs117.hd.dev.sceneloader.scene.VariantObject;
import rs117.hd.dev.sceneloader.scene.VariantOptions;
import rs117.hd.renderer.zone.ZoneRenderer;
import rs117.hd.scene.GamevalManager;

import static net.runelite.api.Constants.MAX_Z;
import static rs117.hd.dev.sceneloader.scene.SceneBuilder.OFFSET;
import static rs117.hd.renderer.zone.SceneManager.NUM_ZONES;

@Slf4j
@Singleton
public class SceneLoader implements RenderCallback {
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private EventBus eventBus;

	@Inject
	private HdPlugin plugin;

	@Inject
	private GamevalManager gamevalManager;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private FreeCamera camera;

	@Inject
	private SelectionHighlight highlight;

	@Inject
	private DevToolBridge runeliteDevTools;

	@Inject
	private SceneToolsOverlay runeliteDevOverlay;

	@Inject
	private LocationOverlay runeliteLocation;

	@Inject
	private CameraOverlay runeliteCamera;

	private boolean started;
	private NavigationButton button;
	private SceneLoaderPanel panel;

	private ExecutorService worker;

	@Nullable
	private volatile Definitions defs;
	@Nullable
	private volatile SceneBuilder builder;

	@Nullable
	private volatile BuiltScene loaded;
	private volatile boolean keepLoaded = true;
	private volatile boolean loading;

	private volatile int plane;
	private volatile boolean planesBelow = true;
	private volatile boolean showRoofs;
	private volatile Set<Integer> allRoofIds = Set.of();
	private volatile boolean wantFreeCamera = true;

	private final VariantOptions variants = new VariantOptions();
	private volatile int lastWorldX = 3222, lastWorldY = 3218, lastChunks;
	@Nullable
	private volatile int[] lastStitched;

	@Nullable
	private DrawCallbacks wrapped;
	@Nullable
	private DrawCallbacks wrapper;

	public void startUp() {
		if (started)
			return;
		started = true;
		eventBus.register(this);
		renderCallbackManager.register(this);
		camera.startUp();
		highlight.startUp();
		runeliteDevOverlay.startUp();
		runeliteLocation.startUp();
		runeliteCamera.startUp();
		worker = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "117 HD cache scene");
			thread.setDaemon(true);
			return thread;
		});

		SwingUtilities.invokeLater(() -> {
			if (button != null)
				return;
			panel = new SceneLoaderPanel(this);
			button = NavigationButton.builder()
				.tooltip("117 HD Cache Scene")
				.icon(SceneLoaderPanel.createIcon())
				.priority(2)
				.panel(panel)
				.build();
			clientToolbar.addNavigation(button);
		});
	}

	public void shutDown() {
		if (!started)
			return;
		started = false;
		eventBus.unregister(this);
		renderCallbackManager.unregister(this);
		camera.shutDown();
		highlight.shutDown();
		runeliteDevOverlay.shutDown();
		runeliteLocation.shutDown();
		runeliteCamera.shutDown();

		restoreGameScene();
		releaseDetailDistance();
		runeliteDevTools.standDown();
		clientThread.invoke(this::unwrap);

		SwingUtilities.invokeLater(() -> {
			if (panel != null)
				panel.closeMap();
			if (button != null) {
				clientToolbar.removeNavigation(button);
				button = null;
				panel = null;
			}
		});

		if (worker != null) {
			worker.shutdownNow();
			worker = null;
		}

		var open = defs;
		defs = null;
		builder = null;
		if (open != null) {
			try {
				open.close();
			} catch (Exception ex) {
				log.warn("Failed to close the cache", ex);
			}
		}
	}

	Client client() {
		return client;
	}

	public void setPlane(int plane) {
		this.plane = plane;
	}

	public void setPlanesBelow(boolean planesBelow) {
		this.planesBelow = planesBelow;
	}

	public void setShowRoofs(boolean showRoofs) {
		this.showRoofs = showRoofs;
	}

	private void overrideLevels(Object[] args, int cameraValuesAt) {
		int levels = cameraValuesAt + 5;
		if (levels + 3 >= args.length)
			return;

		int level = Math.max(0, Math.min(MAX_Z - 1, plane));
		args[levels] = planesBelow ? 0 : level;
		args[levels + 1] = level;
		args[levels + 2] = MAX_Z - 1;
		args[levels + 3] = showRoofs ? Set.<Integer>of() : allRoofIds;
	}

	private static final int DETAIL_DISTANCE_WHILE_LOADED = 320;

	private volatile int detailDistanceBefore = -1;

	private void holdDetailDistanceOpen() {
		if (plugin.configDetailDrawDistance == DETAIL_DISTANCE_WHILE_LOADED)
			return;
		if (detailDistanceBefore < 0)
			detailDistanceBefore = plugin.configDetailDrawDistance;
		plugin.configDetailDrawDistance = DETAIL_DISTANCE_WHILE_LOADED;
	}

	private void releaseDetailDistance() {
		if (detailDistanceBefore < 0)
			return;
		plugin.configDetailDrawDistance = detailDistanceBefore;
		detailDistanceBefore = -1;
	}

	private void drawDynamics() {
		var scene = loaded;
		if (scene == null || scene.getDynamics().isEmpty())
			return;

		if (!(plugin.renderer instanceof ZoneRenderer))
			return;
		Projection projection = ((ZoneRenderer) plugin.renderer).sceneCamera;

		int cycle = client.getGameCycle();

		int level = Math.max(0, Math.min(MAX_Z - 1, plane));
		int minLevel = planesBelow ? 0 : level;
		var hiddenRoofIds = showRoofs ? Set.<Integer>of() : allRoofIds;
		int[][][] roofs = scene.getRoofs();

		int drawn = 0;
		for (BuiltScene.Dynamic dynamic : scene.getDynamics()) {
			if (!isDrawnAt(dynamic, roofs, minLevel, level, hiddenRoofIds))
				continue;

			Model model = dynamic.renderable.modelAt(cycle);
			if (model == null)
				continue;

			plugin.renderer.drawDynamic(
				-1, projection, scene, dynamic.tileObject, dynamic.renderable, model,
				dynamic.orientation, dynamic.x, dynamic.y, dynamic.z
			);
			drawn++;
		}
		reportDynamics(scene, cycle, drawn);
	}

	private static boolean isDrawnAt(
		BuiltScene.Dynamic dynamic, int[][][] roofs, int minLevel, int level, Set<Integer> hiddenRoofIds
	) {
		int objectLevel = dynamic.tileObject.getPlane();
		if (objectLevel < minLevel)
			return false;
		if (objectLevel <= level || objectLevel >= MAX_Z || hiddenRoofIds.isEmpty())
			return true;

		int tileX = (dynamic.x >> 7) + OFFSET;
		int tileZ = (dynamic.z >> 7) + OFFSET;
		var plan = roofs[objectLevel];
		if (tileX < 0 || tileX >= plan.length || tileZ < 0 || tileZ >= plan[tileX].length)
			return true;

		int roofId = plan[tileX][tileZ];
		return roofId == 0 || !hiddenRoofIds.contains(roofId);
	}

	/**
	 * Hands 117 HD the scene a zone at a time, solid parts first and see through parts last.
	 *
	 * <p>This is the game's job normally, and it does it by walking its own scene outwards from its own
	 * camera. Neither of those is what is on screen here, so the order it asks for is an order for
	 * somewhere else, and it hands over solid and see through parts of one zone before it reaches the next.
	 *
	 * <p>That order is not a detail. See through surfaces are drawn without writing depth, so nothing
	 * sorts them afterwards: whatever is drawn last is on top. 117 HD says as much where it draws them,
	 * that it relies on the zones arriving back to front. Water is drawn at the start of a zone's see
	 * through pass, so with the whole scene's solid parts laid down first it ends up between the two, which
	 * is where it belongs. Out of order, a river is painted over the bank in front of it.
	 */
	private void drawZones() {
		var scene = loaded;
		if (scene == null || !(plugin.renderer instanceof ZoneRenderer))
			return;
		var renderer = (ZoneRenderer) plugin.renderer;
		Projection projection = renderer.sceneCamera;

		int level = Math.max(0, Math.min(MAX_Z - 1, plane));
		int minLevel = planesBelow ? 0 : level;

		// Solid first, and in any order: it writes depth, so it sorts itself
		for (int zx = 0; zx < NUM_ZONES; zx++)
			for (int zz = 0; zz < NUM_ZONES; zz++)
				renderer.drawZoneOpaque(projection, scene, zx, zz);

		int[] order = zonesFurthestFirst(scene);
		for (int at = minLevel; at <= MAX_Z - 1; at++)
			for (int zone : order)
				renderer.drawZoneAlpha(projection, scene, at, zone >> 8, zone & 0xFF);
	}

	/** The zones, furthest from the camera first, which is the order see through parts have to be drawn in */
	private int[] zonesFurthestFirst(BuiltScene scene) {
		int count = NUM_ZONES * NUM_ZONES;
		if (zoneOrder == null || zoneOrder.length != count) {
			zoneOrder = new int[count];
			zoneDistance = new long[count];
		}

		int offset = scene.getSceneOffset();
		float cameraX = plugin.cameraPosition[0];
		float cameraZ = plugin.cameraPosition[2];

		for (int zx = 0, at = 0; zx < NUM_ZONES; zx++) {
			for (int zz = 0; zz < NUM_ZONES; zz++, at++) {
				// The middle of the zone, in the units the scene is measured in
				float x = (zx * 8 - offset) * 128 + 512;
				float z = (zz * 8 - offset) * 128 + 512;
				float dx = x - cameraX;
				float dz = z - cameraZ;
				zoneOrder[at] = zx << 8 | zz;
				zoneDistance[at] = (long) (dx * dx + dz * dz);
			}
		}

		// A plain insertion sort, since the order barely changes between one frame and the next
		for (int i = 1; i < count; i++) {
			int zone = zoneOrder[i];
			long distance = zoneDistance[i];
			int j = i - 1;
			while (j >= 0 && zoneDistance[j] < distance) {
				zoneOrder[j + 1] = zoneOrder[j];
				zoneDistance[j + 1] = zoneDistance[j];
				j--;
			}
			zoneOrder[j + 1] = zone;
			zoneDistance[j + 1] = distance;
		}
		return zoneOrder;
	}

	private int[] zoneOrder;
	private long[] zoneDistance;

	private volatile int dynamicsDrawnOnFrame = -1;
	private long lastDynamicReport;
	private volatile int acceptedLastFrame;

	private void reportDynamics(BuiltScene scene, int cycle, int drawn) {
		long now = System.currentTimeMillis();
		if (now - lastDynamicReport < 1000)
			return;
		lastDynamicReport = now;

		var first = scene.getDynamics().get(0);
		int posed = 0;
		for (var dynamic : scene.getDynamics()) {
			if (dynamic.renderable.posesDiffer()) {
				if (posed == 0)
					first = dynamic;
				posed++;
			}
		}
		int accepted = acceptedLastFrame;

		var current = panel;
		if (current != null) {
			current.setSummary(String.format(
				"%s  \u2014  animating %d (%d move), drawn %d, frame %d",
				describe(scene), drawn, posed, accepted, first.renderable.getAnimFrame()
			));
		}

		log.debug(
			"Animating: handed over {} of {} ({} actually move), 117 HD drew {}, cycle {}, "
			+ "sampling {} on frame {} at {}, {}, {}",
			drawn, scene.getDynamics().size(), posed, accepted, cycle,
			first.renderable, first.renderable.getAnimFrame(),
			first.x, first.y, first.z
		);
	}

	private void nameFromGamevals(List<VariantObject> found) {
		if (found.isEmpty())
			return;

		try (var gamevals = gamevalManager.obtainHandle()) {
			var byId = new HashMap<Integer, String>();
			for (var gameval : gamevals.getObjects().entrySet())
				byId.putIfAbsent(gameval.getValue(), gameval.getKey());

			for (VariantObject object : found) {
				object.gameval = byId.get(object.baseId);

				var names = new String[object.variantIds.length];
				for (int i = 0; i < names.length; i++)
					if (object.variantIds[i] != -1)
						names[i] = byId.get(object.variantIds[i]);
				object.variantGamevals = names;
			}
		} catch (Exception ex) {
			log.warn("Could not name the variable objects", ex);
		}
	}

	private static Set<Integer> roofIdsOf(BuiltScene scene) {
		var ids = new HashSet<Integer>();
		for (int[][] plane : scene.getRoofs())
			for (int[] column : plane)
				for (int roofId : column)
					if (roofId > 0)
						ids.add(roofId);
		return ids;
	}

	public void setKeepLoaded(boolean keepLoaded) {
		this.keepLoaded = keepLoaded;
	}

	@Nullable
	public String goToPlacement(VariantObject object, int which) {
		var placements = object.placements;
		if (placements.isEmpty())
			return null;

		int index = Math.floorMod(which, placements.size());
		var placed = placements.get(index);
		String label = (index + 1) + " of " + placements.size();

		clientThread.invoke(() -> {
			camera.goTo(placed.x, placed.y, placed.z);
			highlight.mark(placed, label);
		});
		return label;
	}

	public void setFreeCamera(boolean free) {
		wantFreeCamera = free;
		camera.setEnabled(free);
		clientThread.invoke(this::updateHooks);
	}

	public void setCameraSpeed(float speed) {
		camera.setSpeed(speed);
	}

	@Override
	public boolean addEntity(Renderable renderable, boolean drawingUi) {
		return loaded == null || !(renderable instanceof Actor);
	}

	public VariantOptions getVariants() {
		return variants;
	}

	public boolean hasScene() {
		return loaded != null;
	}

	/** The scene on screen, or null while the game's own is being drawn */
	@Nullable
	public BuiltScene getLoaded() {
		return loaded;
	}

	public void reload() {
		var stitched = lastStitched;
		if (stitched != null) {
			loadStitched(stitched);
			return;
		}
		load(lastWorldX, lastWorldY, lastChunks);
	}

	public void loadStitched(int[] regionIds) {
		if (regionIds.length == 0)
			return;
		if (regionIds.length == 1) {
			lastStitched = null;
			load(worldXOfRegion(regionIds[0]), worldYOfRegion(regionIds[0]), lastChunks);
			return;
		}

		var executor = worker;
		if (executor == null)
			return;

		lastStitched = regionIds.clone();
		var wanted = lastStitched;

		executor.submit(() -> {
			try {
				loading = true;
				long start = System.nanoTime();

				var scene = openCache().buildStitched(wanted, 0, variants);
				long ms = (System.nanoTime() - start) / 1_000_000;

				int[] placed = scene.getMapRegions();
				var names = new StringBuilder();
				for (int i = 0; i < placed.length; i++)
					names.append(i == 0 ? "" : ", ").append(placed[i]);

				String status = "Stitched " + names + " in " + ms + "ms";
				if (placed.length < wanted.length)
					status += "   —  " + (wanted.length - placed.length) + " would not fit";
				finish(scene, status);
			} catch (Throwable ex) {
				log.error("Failed to stitch regions", ex);
				setStatus("Could not stitch: " + ex.getMessage(), Color.RED.darker());
			} finally {
				loading = false;
			}
		});
	}

	private static int worldXOfRegion(int region) {
		return ((region >> 8) << 6) + 32;
	}

	private static int worldYOfRegion(int region) {
		return ((region & 0xFF) << 6) + 32;
	}

	private void finish(BuiltScene scene, String status) {
		loaded = scene;
		runeliteDevTools.standIn();
		allRoofIds = roofIdsOf(scene);
		var found = variants.getFound();
		nameFromGamevals(found);
		var current = panel;
		if (current != null)
			current.setVariantObjects(found);

		apply(scene, () -> {
			setStatus(status, Color.GREEN.darker());
			setSummary(describe(scene) + ", " + found.size() + " variable, "
					   + scene.getDynamics().size() + " animating");
			if (wantFreeCamera)
				camera.setEnabled(true);
		});
	}

	public void load(int worldX, int worldY, int expandedChunks) {
		var executor = worker;
		if (executor == null)
			return;

		lastStitched = null;
		lastWorldX = worldX;
		lastWorldY = worldY;
		lastChunks = expandedChunks;

		executor.submit(() -> {
			try {
				loading = true;
				long start = System.nanoTime();

				var sceneBuilder = openCache();
				var scene = sceneBuilder.build(worldX, worldY, expandedChunks, 0, variants);
				long ms = (System.nanoTime() - start) / 1_000_000;

				loaded = scene;
				runeliteDevTools.standIn();
				allRoofIds = roofIdsOf(scene);
				var found = variants.getFound();
				nameFromGamevals(found);
				var current = panel;
				if (current != null)
					current.setVariantObjects(found);

				apply(scene, () -> {
					setStatus("Loaded " + worldX + ", " + worldY + " in " + ms + "ms", Color.GREEN.darker());
					setSummary(describe(scene) + ", " + found.size() + " variable, "
							   + scene.getDynamics().size() + " animating");
					if (wantFreeCamera)
						camera.setEnabled(true);
				});
			} catch (Throwable ex) {
				log.error("Failed to build a scene from the cache", ex);
				setStatus(String.valueOf(ex.getMessage()), Color.RED.brighter());
			} finally {
				loading = false;
			}
		});
	}

	public void restoreGameScene() {
		if (loaded == null)
			return;
		loaded = null;
		releaseDetailDistance();
		runeliteDevTools.standDown();
		camera.setEnabled(false);
		clientThread.invoke(() -> {
			var worldView = client.getTopLevelWorldView();
			if (worldView == null || client.getGameState() != GameState.LOGGED_IN)
				return;
			handOver(worldView, worldView.getScene());
			updateHooks();
		});
		setStatus("Back to the game's scene", Color.LIGHT_GRAY);
		setSummary(" ");
	}

	public void previewObject(int objectId, Consumer<ModelPreview.Shape> onReady) {
		var pool = worker;
		if (pool == null)
			return;
		if (objectId < 0) {
			SwingUtilities.invokeLater(() -> onReady.accept(null));
			return;
		}

		pool.submit(() -> {
			ModelPreview.Shape shape = null;
			try {
				shape = openCache().preview(objectId, -1);
			} catch (Exception ex) {
				log.warn("Could not build a preview of object {}", objectId, ex);
			}

			var built = shape;
			SwingUtilities.invokeLater(() -> onReady.accept(built));
		});
	}

	private SceneBuilder openCache() throws Exception {
		var existing = builder;
		if (existing != null)
			return existing;

		synchronized (this) {
			if (builder == null) {
				var opened = new Definitions(Definitions.findDefaultCache());
				defs = opened;
				builder = new SceneBuilder(opened);
			}
			return builder;
		}
	}

	private void apply(BuiltScene scene, Runnable onApplied) {
		clientThread.invoke(() -> {
			if (client.getGameState() != GameState.LOGGED_IN)
				return false;

			var worldView = client.getTopLevelWorldView();
			if (worldView == null)
				return false;

			handOver(worldView, scene);
			updateHooks();
			onApplied.run();
			return true;
		});
	}

	private void handOver(WorldView worldView, Scene scene) {
		assert client.isClientThread();
		plugin.renderer.loadScene(worldView, scene);
		plugin.renderer.swapScene(scene);

		for (int x = 0; x < NUM_ZONES; x++)
			for (int z = 0; z < NUM_ZONES; z++)
				plugin.renderer.invalidateZone(scene, x, z);
	}

	private static final Set<String> PER_FRAME_DRAWS = Set.of("draw", "drawDynamic", "drawTemp");

	/**
	 * The two calls that say which part of the scene to draw next, and in what order.
	 *
	 * <p>The game makes these as it walks its own scene outwards from its own camera, and neither is this
	 * one. They are dropped and reissued in {@link #drawZones} instead.
	 */
	private static final Set<String> ZONE_DRAWS = Set.of("drawZoneOpaque", "drawZoneAlpha");

	private void updateHooks() {
		assert client.isClientThread();
		if (loaded != null || camera.isEnabled()) {
			wrap();
		} else {
			unwrap();
		}
	}

	private void wrap() {
		DrawCallbacks current = client.getDrawCallbacks();
		if (current == wrapper)
			return;
		if (current == null) {
			log.warn("Nothing is drawing the scene, so there is nothing to step in front of");
			return;
		}

		wrapped = current;
		wrapper = (DrawCallbacks) Proxy.newProxyInstance(
			DrawCallbacks.class.getClassLoader(),
			new Class<?>[] { DrawCallbacks.class },
			new Hooks(current)
		);
		client.setDrawCallbacks(wrapper);
		log.debug("Stepped in front of {}", current.getClass().getSimpleName());
	}

	private void unwrap() {
		if (wrapper != null && client.getDrawCallbacks() == wrapper)
			client.setDrawCallbacks(wrapped);
		wrapper = null;
		wrapped = null;
	}

	private class Hooks implements InvocationHandler {
		private final DrawCallbacks target;

		Hooks(DrawCallbacks target) {
			this.target = target;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String name = method.getName();

			boolean preSceneDraw = "preSceneDraw".equals(name);
			int cameraValuesAt = -1;

			if (preSceneDraw) {
				acceptedLastFrame = plugin.drawnDynamicRenderableCount;
				cameraValuesAt = FreeCamera.cameraValuesAt(args);
				if (cameraValuesAt >= 0) {
					camera.interceptPreSceneDraw(args);
					if (loaded != null)
						overrideLevels(args, cameraValuesAt);
				}
				if (loaded != null)
					holdDetailDistanceOpen();
			} else if ("drawScene".equals(name)) {
				camera.interceptDrawScene(args);
			} else if (loaded != null && ZONE_DRAWS.contains(name)) {
				return null;
			} else if (skipsDraw(name, args, loaded)) {
				return null;
			}

			Object result;
			try {
				result = method.invoke(target, args);
			} catch (InvocationTargetException ex) {
				throw ex.getCause();
			}

			if (preSceneDraw && cameraValuesAt >= 0 && plugin.frame != dynamicsDrawnOnFrame) {
				dynamicsDrawnOnFrame = plugin.frame;
				// Animating things first: they hand their see through parts to the zone they stand in, and
				// a zone sorts what it has been given when it is asked to draw it
				drawDynamics();
				drawZones();
			}
			return result;
		}
	}

	static boolean skipsDraw(String method, @Nullable Object[] args, @Nullable BuiltScene loaded) {
		if (loaded == null || args == null || !PER_FRAME_DRAWS.contains(method))
			return false;

		for (Object arg : args)
			if (arg instanceof Scene)
				return arg != loaded;
		return false;
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		var scene = loaded;
		if (!keepLoaded || scene == null || loading)
			return;

		var context = plugin.renderer.getSceneContext();
		if (context == null || context.scene == scene)
			return;

		log.debug("The game replaced the cache scene, putting it back");
		apply(scene, () -> {});
	}

	private static String describe(BuiltScene scene) {
		int floors = 0;
		int objects = 0;
		var tiles = scene.getExtendedTiles();
		for (int plane = 0; plane < tiles.length; plane++) {
			for (int x = 0; x < tiles[plane].length; x++) {
				for (int y = 0; y < tiles[plane][x].length; y++) {
					var tile = tiles[plane][x][y];
					if (tile == null)
						continue;
					if (tile.getSceneTilePaint() != null || tile.getSceneTileModel() != null)
						floors++;
					for (var object : tile.getGameObjects())
						if (object != null)
							objects++;
					if (tile.getWallObject() != null)
						objects++;
					if (tile.getDecorativeObject() != null)
						objects++;
					if (tile.getGroundObject() != null)
						objects++;
				}
			}
		}
		return floors + " floors, " + objects + " objects";
	}

	private void setStatus(String text, Color color) {
		var current = panel;
		if (current != null)
			current.setStatus(text, color);
	}

	private void setSummary(String text) {
		var current = panel;
		if (current != null)
			current.setSummary(text);
	}
}
