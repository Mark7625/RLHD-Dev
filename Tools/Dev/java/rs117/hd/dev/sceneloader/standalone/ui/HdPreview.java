package rs117.hd.dev.sceneloader.standalone.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.JTextComponent;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.laf.RuneLiteLAF;
import rs117.hd.dev.sceneloader.scene.BuiltScene;
import rs117.hd.dev.sceneloader.scene.Definitions;
import rs117.hd.dev.sceneloader.scene.SceneBuilder;
import rs117.hd.dev.sceneloader.scene.VariantOptions;

import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;
import rs117.hd.dev.sceneloader.standalone.FlyCamera;
import rs117.hd.dev.sceneloader.standalone.HdRuntime;
import rs117.hd.dev.sceneloader.standalone.HdSettings;
import rs117.hd.dev.sceneloader.standalone.Textures;

@Slf4j
public final class HdPreview {
	private static final String USAGE =
		"Usage: HdPreview [--x 3222] [--y 3218] [--chunks 0] [--region 12850]\n"
		+ "                 [--plane 0] [--roofs] [--cache <dir>] [--width 1280] [--height 800]\n"
		+ "                 [--shot <file.png>] [--camera x,y,z,yaw,pitch] [--settle 60]\n"
		+ "                 [--config name=value,...]\n"
		+ "\n"
		+ "  --shot    draws a frame to a PNG once the scene has settled, then stops\n"
		+ "  --camera  where to draw it from: scene coordinates, then yaw and pitch in degrees\n"
		+ "  --config  any of 117 HD's own settings, by the name of the method that reads it,\n"
		+ "            such as daylightCycle=DAY. Everything else is left at its default.";

	private static final String KEYS =
		"WASD flies · E/Q or Space/Shift up & down · ctrl slows · arrows or a drag look · F2 saves a PNG";

	private int worldX = 3222;
	private int worldY = 3218;
	private int chunks;
	private int plane;
	private boolean roofs;
	private int width = 1280;
	private int height = 800;
	@Nullable
	private Path cache;
	@Nullable
	private Path shot;
	private int settleFrames = 60;
	@Nullable
	private float[] cameraAt;
	private final Map<String, String> settings = new LinkedHashMap<>();

	private final HdSettings hdSettings = new HdSettings();
	private final FlyCamera camera = new FlyCamera();
	private final KeyEventDispatcher keys = this::dispatchKey;

	private Canvas canvas;
	private Loading loading;
	private CardLayout cards;
	private JPanel view;
	private JLabel status;

	private JTextField worldXField, worldYField;
	private JSpinner chunksField;

	private volatile int wantedPlane;
	private volatile boolean wantedPlanesBelow = true;
	private volatile boolean wantedRoofs;

	private volatile boolean running = true;
	private volatile boolean building;
	private volatile boolean showingScene;

	@Nullable
	private volatile BuiltScene pendingScene;
	@Nullable
	private volatile HdRuntime runtime;
	@Nullable
	private volatile SceneBuilder builder;

	private volatile boolean saveRequested;

	public static void main(String[] args) {
		try {
			RuneLiteLAF.setup();
		} catch (Throwable ex) {
			log.warn("Carrying on with the system look, since RuneLite's would not load", ex);
		}

		var preview = new HdPreview();
		try {
			preview.parse(args);
		} catch (IllegalArgumentException ex) {
			System.err.println(ex.getMessage());
			System.err.println(USAGE);
			System.exit(2);
			return;
		}
		SwingUtilities.invokeLater(preview::open);
	}

	private void open() {
		var frame = new JFrame("117 HD — a cache scene drawn by the plugin (no RuneLite)");
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.setLayout(new BorderLayout());

		canvas = new Canvas();
		canvas.setBackground(ColorScheme.DARK_GRAY_COLOR);
		canvas.setIgnoreRepaint(true);
		canvas.setFocusable(true);

		loading = new Loading();

		cards = new CardLayout();
		view = new JPanel(cards);
		view.setPreferredSize(new Dimension(width, height));
		view.add(loading, "loading");
		view.add(canvas, "scene");

		for (var setting : settings.entrySet())
			hdSettings.set(setting.getKey(), setting.getValue());

		status = new JLabel(" ");
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		status.setOpaque(true);
		status.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

		frame.add(controls(), BorderLayout.NORTH);
		frame.add(view, BorderLayout.CENTER);
		frame.add(new HdConfigPanel(hdSettings.asConfig(), this::settingChanged), BorderLayout.EAST);
		frame.add(status, BorderLayout.SOUTH);

		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		var look = new MouseAdapter() {
			private int lastX, lastY;

			@Override
			public void mousePressed(MouseEvent event) {
				lastX = event.getX();
				lastY = event.getY();
				canvas.requestFocusInWindow();
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				camera.drag(event.getX() - lastX, event.getY() - lastY);
				lastX = event.getX();
				lastY = event.getY();
			}
		};
		canvas.addMouseListener(look);
		canvas.addMouseMotionListener(look);

		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				running = false;
				KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
			}
		});

		var thread = new Thread(this::run, "117 HD standalone render");
		thread.setDaemon(true);
		thread.start();
	}

	private JPanel controls() {
		var bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)
		));

		worldXField = new JTextField(String.valueOf(worldX), 5);
		worldYField = new JTextField(String.valueOf(worldY), 5);
		chunksField = new JSpinner(new SpinnerNumberModel(chunks, 0, 10, 1));

		var build = new JButton("Build");
		build.addActionListener(event -> rebuild());

		var planes = new JComboBox<>(new String[] { "Plane 0", "Plane 1", "Plane 2", "Plane 3" });
		planes.setSelectedIndex(plane);
		wantedPlane = plane;
		planes.addActionListener(event -> wantedPlane = planes.getSelectedIndex());

		var planesBelow = new JCheckBox("Planes below", true);
		planesBelow.addActionListener(event -> wantedPlanesBelow = planesBelow.isSelected());

		var roofsBox = new JCheckBox("Roofs", roofs);
		wantedRoofs = roofs;
		roofsBox.addActionListener(event -> wantedRoofs = roofsBox.isSelected());

		var screenshot = new JButton("Save PNG");
		screenshot.addActionListener(event -> saveRequested = true);

		bar.add(new JLabel("X"));
		bar.add(worldXField);
		bar.add(new JLabel("Y"));
		bar.add(worldYField);
		bar.add(new JLabel("Extra chunks"));
		bar.add(chunksField);
		bar.add(build);
		bar.add(Box.createHorizontalStrut(8));
		bar.add(planes);
		bar.add(planesBelow);
		bar.add(roofsBox);
		bar.add(screenshot);

		return bar;
	}

	private void settingChanged(java.lang.reflect.Method method, String value) {
		var item = method.getAnnotation(net.runelite.client.config.ConfigItem.class);
		String key = item == null ? method.getName() : item.keyName();
		hdSettings.change(method.getName(), key, value);
		say(item == null ? method.getName() : item.name() + " is now " + value);
	}

	private void rebuild() {
		var sceneBuilder = builder;
		if (sceneBuilder == null || building)
			return;

		int x, y;
		try {
			x = Integer.parseInt(worldXField.getText().trim());
			y = Integer.parseInt(worldYField.getText().trim());
		} catch (NumberFormatException ex) {
			say("X and Y have to be world coordinates");
			return;
		}

		int extra = (Integer) chunksField.getValue();
		worldX = x;
		worldY = y;
		chunks = extra;
		canvas.requestFocusInWindow();

		var worker = new Thread(() -> build(sceneBuilder, x, y, extra), "cache scene");
		worker.setDaemon(true);
		worker.start();
	}

	private void build(SceneBuilder sceneBuilder, int x, int y, int extra) {
		building = true;
		try {
			loading.stage("Building " + x + ", " + y, "Reading the map and placing what stands on it");
			say("Building " + x + ", " + y);

			long start = System.nanoTime();
			BuiltScene scene = sceneBuilder.build(x, y, extra, 0, new VariantOptions());
			long millis = (System.nanoTime() - start) / 1_000_000;

			pendingScene = scene;
			say("Built " + x + ", " + y + " in " + millis + "ms");
		} catch (Throwable ex) {
			log.error("Could not build a scene from the cache", ex);
			say("Could not build: " + ex);
		} finally {
			building = false;
		}
	}

	private void run() {
		Definitions defs = null;
		HdRuntime started = null;
		try {
			loading.stage("Opening the cache", "Reading the game's own files");
			defs = new Definitions(cache == null ? Definitions.findDefaultCache() : cache);

			loading.stage("Reading the textures", "Every texture the game has, built from its sprites");
			var textures = new Textures(defs);
			builder = new SceneBuilder(defs);

			build(builder, worldX, worldY, chunks);
			BuiltScene scene = pendingScene;
			if (scene == null)
				throw new IllegalStateException("No scene was built");

			if (cameraAt == null) {
				placeCamera(scene);
			} else {
				camera.placeAt(
					cameraAt[0], cameraAt[1], cameraAt[2],
					(float) Math.toRadians(cameraAt[3]), (float) Math.toRadians(cameraAt[4])
				);
			}

			loading.stage("Starting 117 HD", "Shaders, textures and buffers");
			say("Starting 117 HD");
			showCanvas();
			loading.paintOnto(canvas);

			started = new HdRuntime(canvas, defs, textures, hdSettings);
			runtime = started;
			started.start();

			long previous = System.nanoTime();
			float smoothed = 0;
			long lastSaid = 0;
			int frames = 0;
			var taken = new AtomicReference<Image>();

			while (running) {
				long now = System.nanoTime();
				float seconds = (now - previous) / 1e9f;
				previous = now;

				BuiltScene waiting = pendingScene;
				if (waiting != null) {
					pendingScene = null;
					started.setScene(waiting);
					placeCamera(waiting);
				}

				started.setLevels(wantedPlane, wantedPlanesBelow, wantedRoofs);

				if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == null)
					camera.releaseEverything();

				camera.step(seconds);
				started.frame(camera.getPosition(), camera.getYaw(), camera.getPitch());
				frames++;

				if (started.isReady() && !showingScene)
					showingScene = true;

				if (saveRequested && started.isReady()) {
					saveRequested = false;
					started.nextFrameTo(this::saveToRenders);
				}

				if (shot != null && started.isReady()) {
					if (frames == settleFrames)
						started.nextFrameTo(taken::set);
					if (taken.get() != null) {
						save(taken.get(), shot);
						running = false;
					} else if (frames > settleFrames + 120) {
						say("117 HD never handed over a frame");
						running = false;
					}
				}

				float instant = seconds > 0 ? 1 / seconds : 0;
				smoothed = smoothed == 0 ? instant : smoothed * 0.9f + instant * 0.1f;

				if (System.currentTimeMillis() - lastSaid > 250) {
					lastSaid = System.currentTimeMillis();
					say(String.format(
						"%.0f fps   %s   %s   %s",
						smoothed, camera.describe(),
						started.isReady() ? "117 HD is drawing" : "waiting for 117 HD to start",
						KEYS
					));
				}
			}
		} catch (Throwable ex) {
			log.error("The standalone renderer stopped", ex);
			say("Stopped: " + ex);
			loading.stage("Stopped", String.valueOf(ex));
			showLoading();
		} finally {
			if (started != null)
				started.stop();
			if (defs != null) {
				try {
					defs.close();
				} catch (Exception ignored) {
				}
			}
		}
	}

	private void showCanvas() {
		onSwing(() -> cards.show(view, "scene"));
	}

	private void showLoading() {
		onSwing(() -> cards.show(view, "loading"));
	}

	private static void onSwing(Runnable work) {
		try {
			SwingUtilities.invokeAndWait(work);
		} catch (Exception ex) {
		}
	}

	private void saveToRenders(Image image) {
		String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		save(image, new File(System.getProperty("user.dir"), "renders").toPath().resolve("hd-" + stamp + ".png"));
	}

	private void save(Image image, Path to) {
		try {
			Path folder = to.toAbsolutePath().getParent();
			if (folder != null)
				Files.createDirectories(folder);

			var buffered = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
			var g = buffered.getGraphics();
			g.drawImage(image, 0, 0, null);
			g.dispose();

			ImageIO.write(buffered, "png", to.toFile());
			say("Wrote " + to.toAbsolutePath() + " (" + buffered.getWidth() + "x" + buffered.getHeight() + ")");
		} catch (Exception ex) {
			log.error("Could not save the frame", ex);
			say("Could not save: " + ex);
		}
	}

	private void placeCamera(BuiltScene scene) {
		int sceneX = worldX - scene.getBaseX();
		int sceneY = worldY - scene.getBaseY();
		int ground = scene.getTileHeights()[0]
			[Math.max(0, Math.min(184, sceneX + BuiltScene.SCENE_OFFSET))]
			[Math.max(0, Math.min(184, sceneY + BuiltScene.SCENE_OFFSET))];

		camera.goTo(
			sceneX * LOCAL_TILE_SIZE + LOCAL_TILE_SIZE / 2f,
			ground,
			sceneY * LOCAL_TILE_SIZE + LOCAL_TILE_SIZE / 2f
		);
	}

	private void say(String text) {
		var label = status;
		if (label != null)
			SwingUtilities.invokeLater(() -> label.setText(" " + text.trim()));
	}

	private boolean dispatchKey(KeyEvent event) {
		if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent)
			return false;

		int code = event.getKeyCode();
		if (event.getID() == KeyEvent.KEY_PRESSED && code == KeyEvent.VK_F2) {
			saveRequested = true;
			return true;
		}
		if (!FlyCamera.isCameraKey(code))
			return false;

		if (event.getID() == KeyEvent.KEY_PRESSED) {
			camera.keyPressed(code);
		} else if (event.getID() == KeyEvent.KEY_RELEASED) {
			camera.keyReleased(code);
		}
		return code != KeyEvent.VK_SHIFT && code != KeyEvent.VK_CONTROL;
	}

	private static final class Loading extends JPanel {
		private volatile String what = "Starting";
		private volatile String detail = "";

		Loading() {
			setBackground(ColorScheme.DARK_GRAY_COLOR);
		}

		void stage(String what, String detail) {
			this.what = what;
			this.detail = detail;
			repaint();
		}

		void paintOnto(Canvas canvas) {
			onSwing(() -> {
				Graphics g = canvas.getGraphics();
				if (g == null)
					return;
				try {
					draw(g, canvas.getWidth(), canvas.getHeight());
				} finally {
					g.dispose();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			draw(graphics, getWidth(), getHeight());
		}

		private void draw(Graphics graphics, int width, int height) {
			var g = (Graphics2D) graphics;
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g.setColor(ColorScheme.DARK_GRAY_COLOR);
			g.fillRect(0, 0, width, height);

			int middle = height / 2;
			g.setFont(FontManager.getRunescapeBoldFont().deriveFont(28f));
			g.setColor(ColorScheme.BRAND_ORANGE);
			centre(g, "117 HD", width, middle - 46);

			g.setFont(FontManager.getDefaultFont().deriveFont(14f));
			g.setColor(ColorScheme.TEXT_COLOR);
			centre(g, what + "…", width, middle);

			g.setFont(FontManager.getRunescapeSmallFont());
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			centre(g, detail, width, middle + 22);

			int barWidth = Math.min(320, width - 80);
			int left = (width - barWidth) / 2;
			g.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g.fillRect(left, middle + 42, barWidth, 5);
			g.setColor(ColorScheme.BRAND_ORANGE);
			g.fillRect(left, middle + 42, barWidth / 3, 5);
		}

		private static void centre(Graphics2D g, String text, int width, int y) {
			if (text == null || text.isEmpty())
				return;
			int textWidth = g.getFontMetrics().stringWidth(text);
			g.drawString(text, (width - textWidth) / 2, y);
		}
	}

	private void parse(String[] args) {
		for (int i = 0; i < args.length; i++) {
			String flag = args[i];
			if ("--roofs".equals(flag)) {
				roofs = true;
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
				case "--shot":
					shot = Paths.get(value);
					break;
				case "--settle":
					settleFrames = Integer.parseInt(value);
					break;
				case "--camera": {
					String[] parts = value.split("[,\\s]+");
					if (parts.length != 5)
						throw new IllegalArgumentException("--camera wants x,y,z,yaw,pitch");
					cameraAt = new float[5];
					for (int at = 0; at < 5; at++)
						cameraAt[at] = Float.parseFloat(parts[at]);
					break;
				}
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
