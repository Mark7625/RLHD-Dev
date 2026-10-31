package rs117.hd.dev.sceneloader.standalone.headless;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.dev.sceneloader.scene.BuiltScene;
import rs117.hd.dev.sceneloader.scene.Definitions;
import rs117.hd.dev.sceneloader.scene.SceneBuilder;
import rs117.hd.dev.sceneloader.scene.VariantOptions;
import rs117.hd.overlays.FrameTimings;
import rs117.hd.overlays.Timer;

import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;
import rs117.hd.dev.sceneloader.standalone.FlyCamera;
import rs117.hd.dev.sceneloader.standalone.HdRuntime;
import rs117.hd.dev.sceneloader.standalone.HdSettings;
import rs117.hd.dev.sceneloader.standalone.Textures;

@Slf4j
public final class HdBenchmark {
	private static final String USAGE =
		"Usage: HdBenchmark [--x 3222] [--y 3218] [--chunks 0] [--region 12850] [--plane 0] [--roofs]\n"
		+ "                   [--cache <dir>] [--width 1280] [--height 800] [--config name=value,...]\n"
		+ "                   [--out <file.csv>] [--warmup 3] [--spin 12] [--hold 3]\n"
		+ "                   [--above 2400] [--pitch 50] [--hidden] [--name <label>]\n"
		+ "\n"
		+ "  --warmup  seconds of drawing before anything is written down, for shaders and zones\n"
		+ "  --spin    seconds the camera takes to turn a full circle\n"
		+ "  --hold    seconds it then stands still for\n"
		+ "  --above   how far above the ground the camera is lifted\n"
		+ "  --hidden  puts the window off screen, for running while something else is in front\n"
		+ "  --name    a label written into the CSV, to tell runs apart";

	private int worldX = 3222;
	private int worldY = 3218;
	private int chunks;
	private int plane;
	private boolean roofs;
	private int width = 1280;
	private int height = 800;
	private boolean hidden;
	private String label = "";

	private float warmupSeconds = 3;
	private float spinSeconds = 12;
	private float holdSeconds = 3;
	private float above = 2400;
	private float pitchDegrees = 50;

	@Nullable
	private Path cache;
	@Nullable
	private Path out;
	private final Map<String, String> settings = new LinkedHashMap<>();

	private final HdSettings hdSettings = new HdSettings();
	private final FlyCamera camera = new FlyCamera();

	private Canvas canvas;
	private volatile boolean running = true;

	private static final class Frame {
		final String phase;
		final long millisIn;
		final float yawDegrees;
		final float frameMillis;
		final float cpuLoad;
		final long[] timers;

		Frame(String phase, long millisIn, float yawDegrees, float frameMillis, FrameTimings timings) {
			this.phase = phase;
			this.millisIn = millisIn;
			this.yawDegrees = yawDegrees;
			this.frameMillis = frameMillis;
			this.cpuLoad = timings.cpuLoad;
			this.timers = timings.timers;
		}
	}

	private final List<Frame> frames = new ArrayList<>();

	public static void main(String[] args) {
		var benchmark = new HdBenchmark();
		try {
			benchmark.parse(args);
		} catch (IllegalArgumentException ex) {
			System.err.println(ex.getMessage());
			System.err.println(USAGE);
			System.exit(2);
			return;
		}

		if (GraphicsEnvironment.isHeadless()) {
			System.err.println(
				"There is no display. The plugin draws through OpenGL on a real canvas, so one is needed "
				+ "even when nothing is watching; use --hidden to keep the window out of the way."
			);
			System.exit(3);
			return;
		}

		SwingUtilities.invokeLater(benchmark::open);
	}

	private void open() {
		var frame = new JFrame("117 HD — benchmark");
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.setLayout(new BorderLayout());

		canvas = new Canvas();
		canvas.setPreferredSize(new Dimension(width, height));
		canvas.setIgnoreRepaint(true);
		frame.add(canvas, BorderLayout.CENTER);

		frame.pack();
		if (hidden) {
			frame.setLocation(-width - 200, -height - 200);
		} else {
			frame.setLocationRelativeTo(null);
		}
		frame.setVisible(true);

		var thread = new Thread(this::run, "117 HD benchmark");
		thread.setDaemon(false);
		thread.start();
	}

	private void run() {
		Definitions defs = null;
		HdRuntime runtime = null;
		int exit = 0;

		try {
			say("Opening the cache");
			defs = new Definitions(cache == null ? Definitions.findDefaultCache() : cache);
			var textures = new Textures(defs);
			var builder = new SceneBuilder(defs);

			say("Building " + worldX + ", " + worldY);
			long startedBuild = System.nanoTime();
			BuiltScene scene = builder.build(worldX, worldY, chunks, 0, new VariantOptions());
			long buildMillis = (System.nanoTime() - startedBuild) / 1_000_000;
			say("Built in " + buildMillis + "ms");

			for (var setting : settings.entrySet())
				hdSettings.set(setting.getKey(), setting.getValue());

			runtime = new HdRuntime(canvas, defs, textures, hdSettings);
			runtime.start();
			runtime.setLevels(plane, true, roofs);
			runtime.setScene(scene);

			lookAtScene(scene, 0);

			var latest = new FrameTimings[1];
			var listening = new boolean[1];

			long previous = System.nanoTime();
			long startedRun = 0;
			String phase = "starting";

			while (running) {
				long now = System.nanoTime();
				float seconds = (now - previous) / 1e9f;
				previous = now;

				if (runtime.isReady() && !listening[0]) {
					listening[0] = true;
					runtime.frameTimer().addTimingsListener(timings -> latest[0] = timings);
					startedRun = now;
					phase = "warmup";
					say("Warming up for " + warmupSeconds + "s");
				}

				float elapsed = startedRun == 0 ? 0 : (now - startedRun) / 1e9f;
				float yaw = 0;

				if (listening[0]) {
					if (elapsed < warmupSeconds) {
						phase = "warmup";
					} else if (elapsed < warmupSeconds + spinSeconds) {
						if (!"spin".equals(phase))
							say("Turning a full circle over " + spinSeconds + "s");
						phase = "spin";
						yaw = (elapsed - warmupSeconds) / spinSeconds * 360;
					} else if (elapsed < warmupSeconds + spinSeconds + holdSeconds) {
						if (!"hold".equals(phase))
							say("Holding still for " + holdSeconds + "s");
						phase = "hold";
						yaw = 360;
					} else {
						running = false;
						continue;
					}
					lookAtScene(scene, yaw);
				}

				latest[0] = null;
				runtime.frame(camera.getPosition(), camera.getYaw(), camera.getPitch());

				var timings = latest[0];
				if (timings != null && !"starting".equals(phase))
					frames.add(new Frame(phase, (long) (elapsed * 1000), yaw, seconds * 1000, timings));
			}

			if (frames.isEmpty())
				throw new IllegalStateException("No frames were timed, so there is nothing to write");

			Path csv = out == null
				? Paths.get("renders").resolve("benchmark-" + stamp() + ".csv")
				: out;
			write(csv);
			report(csv);
		} catch (Throwable ex) {
			log.error("The benchmark stopped", ex);
			exit = 1;
		} finally {
			if (runtime != null)
				runtime.stop();
			if (defs != null) {
				try {
					defs.close();
				} catch (Exception ignored) {
				}
			}
		}

		System.exit(exit);
	}

	private void lookAtScene(BuiltScene scene, float yawDegrees) {
		int sceneX = worldX - scene.getBaseX();
		int sceneY = worldY - scene.getBaseY();
		int ground = scene.getTileHeights()[0]
			[Math.max(0, Math.min(184, sceneX + BuiltScene.SCENE_OFFSET))]
			[Math.max(0, Math.min(184, sceneY + BuiltScene.SCENE_OFFSET))];

		float middleX = sceneX * LOCAL_TILE_SIZE + LOCAL_TILE_SIZE / 2f;
		float middleZ = sceneY * LOCAL_TILE_SIZE + LOCAL_TILE_SIZE / 2f;

		float yaw = (float) Math.toRadians(yawDegrees);
		float pitch = (float) Math.toRadians(pitchDegrees);

		float level = (float) Math.cos(pitch);
		float back = above / Math.max(0.2f, (float) Math.sin(pitch));
		float atX = middleX + (float) Math.sin(yaw) * level * back;
		float atZ = middleZ - (float) Math.cos(yaw) * level * back;

		camera.placeAt(atX, ground - above, atZ, yaw, pitch);
	}

	private void write(Path csv) throws Exception {
		Path folder = csv.toAbsolutePath().getParent();
		if (folder != null)
			Files.createDirectories(folder);

		try (BufferedWriter writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			var header = new StringBuilder("run,phase,frame,millis,yaw_degrees,frame_ms,cpu_load");
			for (Timer timer : Timer.TIMERS)
				header.append(',').append(timer.name()).append("_ms");
			writer.write(header.toString());
			writer.newLine();

			for (int i = 0; i < frames.size(); i++) {
				var frame = frames.get(i);
				var row = new StringBuilder();
				row.append(label.isEmpty() ? "run" : label).append(',')
					.append(frame.phase).append(',')
					.append(i).append(',')
					.append(frame.millisIn).append(',')
					.append(round(frame.yawDegrees)).append(',')
					.append(round(frame.frameMillis)).append(',')
					.append(round(frame.cpuLoad));

				for (Timer timer : Timer.TIMERS) {
					int at = timer.ordinal();
					long nanos = at < frame.timers.length ? frame.timers[at] : 0;
					row.append(',').append(round(nanos / 1e6f));
				}

				writer.write(row.toString());
				writer.newLine();
			}
		}
	}

	private void report(Path csv) {
		var measured = new ArrayList<Frame>();
		for (Frame frame : frames)
			if (!"warmup".equals(frame.phase))
				measured.add(frame);
		if (measured.isEmpty())
			measured.addAll(frames);

		float[] frameTimes = new float[measured.size()];
		for (int i = 0; i < measured.size(); i++)
			frameTimes[i] = measured.get(i).frameMillis;

		System.out.println();
		System.out.println("117 HD benchmark" + (label.isEmpty() ? "" : " — " + label));
		System.out.printf("  scene       %d, %d%s%n", worldX, worldY, chunks > 0 ? " +" + chunks + " chunks" : "");
		System.out.printf("  frames      %d measured, %d in all%n", measured.size(), frames.size());
		System.out.printf(
			"  frame time  %.2f ms average, %.2f median, %.2f at the 95th, %.2f worst%n",
			average(frameTimes), percentile(frameTimes, 50), percentile(frameTimes, 95), percentile(frameTimes, 100)
		);
		System.out.printf("  frame rate  %.0f fps on average%n", 1000 / Math.max(0.001f, average(frameTimes)));

		var totals = new ArrayList<Map.Entry<Timer, Float>>();
		for (Timer timer : Timer.TIMERS) {
			float sum = 0;
			for (Frame frame : measured)
				if (timer.ordinal() < frame.timers.length)
					sum += frame.timers[timer.ordinal()] / 1e6f;
			if (sum > 0)
				totals.add(Map.entry(timer, sum / measured.size()));
		}
		totals.sort(Comparator.comparing(Map.Entry<Timer, Float>::getValue).reversed());

		System.out.println("  where it went, per frame:");
		for (int i = 0; i < Math.min(12, totals.size()); i++) {
			var entry = totals.get(i);
			System.out.printf(
				"    %-28s %7.3f ms%s%n",
				entry.getKey().name, entry.getValue(), entry.getKey().isGpuTimer() ? "  (GPU)" : ""
			);
		}

		System.out.println("  written to  " + csv.toAbsolutePath());
	}

	private static float average(float[] values) {
		float sum = 0;
		for (float value : values)
			sum += value;
		return values.length == 0 ? 0 : sum / values.length;
	}

	private static float percentile(float[] values, int percent) {
		if (values.length == 0)
			return 0;
		float[] sorted = values.clone();
		java.util.Arrays.sort(sorted);
		int at = Math.min(sorted.length - 1, Math.max(0, Math.round((percent / 100f) * (sorted.length - 1))));
		return sorted[at];
	}

	private static String round(float value) {
		return String.format(Locale.ROOT, "%.4f", value);
	}

	private static String stamp() {
		return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
	}

	private void say(String what) {
		log.info(what);
	}

	private void parse(String[] args) {
		for (int i = 0; i < args.length; i++) {
			String flag = args[i];
			if ("--roofs".equals(flag)) {
				roofs = true;
				continue;
			}
			if ("--hidden".equals(flag)) {
				hidden = true;
				continue;
			}
			if (i + 1 >= args.length)
				throw new IllegalArgumentException("Nothing was given for " + flag);

			String value = args[++i];
			switch (flag) {
				case "--x":
					worldX = Integer.parseInt(value);
					break;
				case "--y":
					worldY = Integer.parseInt(value);
					break;
				case "--region": {
					int region = Integer.parseInt(value);
					worldX = ((region >> 8) << 6) + 32;
					worldY = ((region & 0xFF) << 6) + 32;
					break;
				}
				case "--chunks":
					chunks = Integer.parseInt(value);
					break;
				case "--plane":
					plane = Integer.parseInt(value);
					break;
				case "--width":
					width = Integer.parseInt(value);
					break;
				case "--height":
					height = Integer.parseInt(value);
					break;
				case "--cache":
					cache = Paths.get(value);
					break;
				case "--out":
					out = Paths.get(value);
					break;
				case "--name":
					label = value;
					break;
				case "--warmup":
					warmupSeconds = Float.parseFloat(value);
					break;
				case "--spin":
					spinSeconds = Float.parseFloat(value);
					break;
				case "--hold":
					holdSeconds = Float.parseFloat(value);
					break;
				case "--above":
					above = Float.parseFloat(value);
					break;
				case "--pitch":
					pitchDegrees = Float.parseFloat(value);
					break;
				case "--config": {
					for (String pair : value.split(",")) {
						int equals = pair.indexOf('=');
						if (equals < 0)
							throw new IllegalArgumentException("--config wants name=value, not " + pair);
						settings.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
					}
					break;
				}
				default:
					throw new IllegalArgumentException("Unknown option " + flag);
			}
		}
	}
}
