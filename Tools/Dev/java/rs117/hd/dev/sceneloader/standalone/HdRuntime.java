package rs117.hd.dev.sceneloader.standalone;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import java.awt.Canvas;
import java.awt.Image;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Projection;
import net.runelite.api.WorldView;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.overlay.OverlayManager;
import rs117.hd.HdPlugin;
import rs117.hd.HdPluginConfig;
import rs117.hd.overlays.FrameTimer;
import rs117.hd.dev.sceneloader.scene.BuiltScene;
import rs117.hd.dev.sceneloader.scene.Definitions;
import rs117.hd.renderer.Renderer;
import rs117.hd.renderer.zone.ZoneRenderer;

import static net.runelite.api.Constants.MAX_Z;
import static rs117.hd.renderer.zone.SceneManager.NUM_ZONES;
import rs117.hd.dev.sceneloader.standalone.client.CacheTextureProvider;
import rs117.hd.dev.sceneloader.standalone.client.Fake;
import rs117.hd.dev.sceneloader.standalone.client.FakeClient;
import rs117.hd.dev.sceneloader.standalone.client.FakeObjects;

@Slf4j
public final class HdRuntime {
	private final Canvas canvas;
	private final Definitions defs;
	private final Textures textures;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
		var thread = new Thread(runnable, "117 HD standalone");
		thread.setDaemon(true);
		return thread;
	});

	private final FakeClient clientState;
	private final Client client;
	private final InlineClientThread clientThread = new InlineClientThread();
	private final EventBus eventBus = new EventBus();
	private final DrawManager drawManager = new DrawManager();
	private final HdSettings settings;

	private Injector injector;
	private HdPlugin plugin;

	@Nullable
	private BuiltScene scene;
	@Nullable
	private Renderer handedTo;

	private int level;
	private boolean planesBelow = true;
	private boolean showRoofs;
	private Set<Integer> roofIds = Set.of();

	public HdRuntime(Canvas canvas, Definitions defs, Textures textures, HdSettings settings) {
		this.canvas = canvas;
		this.defs = defs;
		this.textures = textures;
		this.settings = settings;

		var provider = new CacheTextureProvider(defs, textures);
		clientState = new FakeClient(canvas, provider.asProvider(), new FakeObjects(defs));
		client = Fake.of(Client.class, clientState);
	}

	public HdPlugin getPlugin() {
		return plugin;
	}

	public FrameTimer frameTimer() {
		return injector.getInstance(FrameTimer.class);
	}

	public void nextFrameTo(Consumer<Image> whenDrawn) {
		drawManager.requestNextFrameListener(whenDrawn);
	}

	public boolean isReady() {
		return plugin != null && plugin.renderer != null && clientThread.watchedFinished();
	}

	public void setLevels(int level, boolean planesBelow, boolean showRoofs) {
		this.level = Math.max(0, Math.min(MAX_Z - 1, level));
		this.planesBelow = planesBelow;
		this.showRoofs = showRoofs;
	}

	public void start() throws Exception {
		Thread owner = Thread.currentThread();
		clientState.ownedBy(owner);
		clientThread.ownedBy(owner);

		plugin = new HdPlugin();
		injector = Guice.createInjector(new Bindings());
		injector.injectMembers(plugin);

		var injectorField = Plugin.class.getDeclaredField("injector");
		injectorField.setAccessible(true);
		injectorField.set(plugin, injector);

		eventBus.register(plugin);
		settings.whenChanged(this::settingChanged);

		var startUp = HdPlugin.class.getDeclaredMethod("startUp");
		startUp.setAccessible(true);
		clientThread.watchNext();
		startUp.invoke(plugin);

		eventBus.post(gameStateChanged(GameState.LOGGED_IN));
	}

	private void settingChanged(String key) {
		var event = new ConfigChanged();
		event.setGroup(HdPluginConfig.CONFIG_GROUP);
		event.setKey(key);
		eventBus.post(event);
	}

	public void stop() {
		if (plugin == null)
			return;
		try {
			var shutDown = HdPlugin.class.getDeclaredMethod("shutDown");
			shutDown.setAccessible(true);
			shutDown.invoke(plugin);
		} catch (Exception ex) {
			log.warn("Could not stop the plugin cleanly", ex);
		}
		executor.shutdownNow();
	}

	public void setScene(@Nullable BuiltScene scene) {
		this.scene = scene;
		handedTo = null;
		roofIds = scene == null ? Set.of() : roofIdsOf(scene);
		clientState.setScene(scene);
	}

	public void frame(float[] cameraPosition, float yaw, float pitch) {
		clientThread.runPending();
		clientState.sizeUi(Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()));
		clientState.setCamera(cameraPosition, yaw, pitch, level);
		clientState.tick();

		eventBus.post(new ClientTick());
		eventBus.post(new BeforeRender());

		if (!isReady())
			return;

		Renderer renderer = plugin.renderer;
		var scene = this.scene;
		if (scene == null) {
			renderer.draw(0);
			return;
		}

		if (handedTo != renderer) {
			handOver(renderer, scene);
			handedTo = renderer;
		}

		if (clientState.getGameCycle() % 30 == 0)
			eventBus.post(new GameTick());

		Projection projection = renderer instanceof ZoneRenderer ? ((ZoneRenderer) renderer).sceneCamera : null;
		int minLevel = planesBelow ? 0 : level;
		var hiddenRoofs = showRoofs ? Set.<Integer>of() : roofIds;

		renderer.preSceneDraw(
			scene, null,
			cameraPosition[0], cameraPosition[1], cameraPosition[2], pitch, yaw,
			minLevel, level, MAX_Z - 1, hiddenRoofs
		);

		if (plugin.sceneViewport != null) {
			drawDynamics(renderer, projection, scene, minLevel, hiddenRoofs);

			for (int zx = 0; zx < NUM_ZONES; zx++)
				for (int zz = 0; zz < NUM_ZONES; zz++)
					renderer.drawZoneOpaque(projection, scene, zx, zz);
			renderer.drawPass(projection, scene, DrawCallbacks.PASS_OPAQUE);

			renderer.drawPass(projection, scene, DrawCallbacks.PRE_PASS_ALPHA);
			int[] order = zonesFurthestFirst(cameraPosition);
			for (int at = minLevel; at < MAX_Z; at++)
				for (int zone : order)
					renderer.drawZoneAlpha(projection, scene, at, zone >> 8, zone & 0xFF);
			renderer.drawPass(projection, scene, DrawCallbacks.PASS_ALPHA);

			renderer.postSceneDraw(scene);
		}

		renderer.draw(0);
	}

	private void handOver(Renderer renderer, BuiltScene scene) {
		WorldView worldView = client.getTopLevelWorldView();
		renderer.loadScene(worldView, scene);
		renderer.swapScene(scene);

		for (int x = 0; x < NUM_ZONES; x++)
			for (int z = 0; z < NUM_ZONES; z++)
				renderer.invalidateZone(scene, x, z);

		log.debug("Handed over a scene at {}, {}", scene.getBaseX(), scene.getBaseY());
	}

	private void drawDynamics(
		Renderer renderer, @Nullable Projection projection, BuiltScene scene,
		int minLevel, Set<Integer> hiddenRoofs
	) {
		if (projection == null || scene.getDynamics().isEmpty())
			return;

		int cycle = clientState.getGameCycle();
		int[][][] roofs = scene.getRoofs();

		for (var dynamic : scene.getDynamics()) {
			if (!isShown(dynamic, roofs, minLevel, hiddenRoofs))
				continue;

			var model = dynamic.renderable.modelAt(cycle);
			if (model == null)
				continue;
			renderer.drawDynamic(
				-1, projection, scene, dynamic.tileObject, dynamic.renderable, model,
				dynamic.orientation, dynamic.x, dynamic.y, dynamic.z
			);
		}
	}

	private boolean isShown(BuiltScene.Dynamic dynamic, int[][][] roofs, int minLevel, Set<Integer> hiddenRoofs) {
		int objectLevel = dynamic.tileObject.getPlane();
		if (objectLevel < minLevel)
			return false;
		if (objectLevel <= level || objectLevel >= MAX_Z || hiddenRoofs.isEmpty())
			return true;

		int tileX = (dynamic.x >> 7) + BuiltScene.SCENE_OFFSET;
		int tileZ = (dynamic.z >> 7) + BuiltScene.SCENE_OFFSET;
		var plan = roofs[objectLevel];
		if (tileX < 0 || tileX >= plan.length || tileZ < 0 || tileZ >= plan[tileX].length)
			return true;

		int roofId = plan[tileX][tileZ];
		return roofId == 0 || !hiddenRoofs.contains(roofId);
	}

	private int[] zoneOrder;
	private long[] zoneDistance;

	private int[] zonesFurthestFirst(float[] cameraPosition) {
		int count = NUM_ZONES * NUM_ZONES;
		if (zoneOrder == null) {
			zoneOrder = new int[count];
			zoneDistance = new long[count];
		}

		int offset = BuiltScene.SCENE_OFFSET;
		for (int zx = 0, at = 0; zx < NUM_ZONES; zx++) {
			for (int zz = 0; zz < NUM_ZONES; zz++, at++) {
				float x = (zx * 8 - offset) * 128 + 512;
				float z = (zz * 8 - offset) * 128 + 512;
				float dx = x - cameraPosition[0];
				float dz = z - cameraPosition[2];
				zoneOrder[at] = zx << 8 | zz;
				zoneDistance[at] = (long) (dx * dx + dz * dz);
			}
		}

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

	private static Set<Integer> roofIdsOf(BuiltScene scene) {
		var ids = new HashSet<Integer>();
		for (int[][] plane : scene.getRoofs())
			for (int[] column : plane)
				for (int roofId : column)
					if (roofId > 0)
						ids.add(roofId);
		return ids;
	}

	private static GameStateChanged gameStateChanged(GameState state) {
		var event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}

	private final class Bindings extends AbstractModule {
		@Override
		protected void configure() {
			bind(HdPlugin.class).toInstance(plugin);
			bind(Client.class).toInstance(client);

			bindConstant().annotatedWith(Names.named("developerMode")).to(true);
			bindConstant().annotatedWith(Names.named("safeMode")).to(false);
			bindConstant().annotatedWith(Names.named("disableTelemetry")).to(true);
			bindConstant().annotatedWith(Names.named("insecureWriteCredentials")).to(false);
			bind(ClientThread.class).toInstance(clientThread);
			bind(EventBus.class).toInstance(eventBus);
			bind(DrawManager.class).toInstance(drawManager);
			bind(RenderCallbackManager.class).toInstance(new RenderCallbackManager());
			bind(HdPluginConfig.class).toInstance(settings.asConfig());
			bind(Gson.class).toInstance(new Gson());
			bind(ScheduledExecutorService.class).toInstance(executor);

			bind(ConfigManager.class).toInstance(nothing(ConfigManager.class));
			bind(ClientUI.class).toInstance(nothing(ClientUI.class));

			var plugins = nothing(PluginManager.class);
			org.mockito.Mockito.when(plugins.isPluginEnabled(org.mockito.ArgumentMatchers.any())).thenReturn(true);
			bind(PluginManager.class).toInstance(plugins);
			bind(OverlayManager.class).toInstance(nothing(OverlayManager.class));
			bind(KeyManager.class).toInstance(nothing(KeyManager.class));
			bind(MouseManager.class).toInstance(nothing(MouseManager.class));
			bind(ColorPickerManager.class).toInstance(nothing(ColorPickerManager.class));
			bind(ChatMessageManager.class).toInstance(nothing(ChatMessageManager.class));
		}
	}

	private static <T> T nothing(Class<T> type) {
		return org.mockito.Mockito.mock(type);
	}
}
