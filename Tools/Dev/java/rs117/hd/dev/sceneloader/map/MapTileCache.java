package rs117.hd.dev.sceneloader.map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.HdPlugin;

@Slf4j
public class MapTileCache {
	private static final String REPO = "Mark7625/osrs_map_tiles";
	private static final String BRANCH = "master";
	private static final String RAW_URL = "https://raw.githubusercontent.com/" + REPO + "/%s/%d/%d/%d/%d.png";
	private static final String COMMIT_URL = "https://api.github.com/repos/" + REPO + "/commits/" + BRANCH;
	private static final String USER_AGENT = "117hd-dev-cache-scene";

	private static final long RECHECK_MILLIS = 12 * 60 * 60 * 1000L;
	private static final int CONNECT_TIMEOUT_MILLIS = 8000;
	private static final int READ_TIMEOUT_MILLIS = 20000;

	private static final int IMAGES_IN_MEMORY = 160;

	private final Path root = HdPlugin.PLUGIN_DIR.resolve("map-tiles").toPath();
	private final ExecutorService downloads = Executors.newFixedThreadPool(4, runnable -> {
		Thread thread = new Thread(runnable, "cache-scene-map-tiles");
		thread.setDaemon(true);
		return thread;
	});

	private final Map<Long, BufferedImage> images = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> eldest) {
			return size() > IMAGES_IN_MEMORY;
		}
	};

	private final Set<Long> pending = ConcurrentHashMap.newKeySet();
	private final Set<Long> missing = ConcurrentHashMap.newKeySet();

	private volatile String commit;

	private final Runnable onLoaded;

	public MapTileCache(Runnable onLoaded) {
		this.onLoaded = onLoaded;
	}

	public void shutDown() {
		downloads.shutdownNow();
		synchronized (images) {
			images.clear();
		}
	}

	@Nullable
	public BufferedImage get(int plane, int zoom, int tileX, int tileY) {
		long key = key(plane, zoom, tileX, tileY);

		BufferedImage image;
		synchronized (images) {
			image = images.get(key);
		}
		if (image != null || missing.contains(key))
			return image;

		if (pending.add(key))
			downloads.submit(() -> load(key, plane, zoom, tileX, tileY));
		return null;
	}

	private void load(long key, int plane, int zoom, int tileX, int tileY) {
		try {
			String commit = commit();
			Path file = root.resolve(commit).resolve(plane + "/" + zoom + "/" + tileX + "/" + tileY + ".png");

			byte[] bytes;
			if (Files.isRegularFile(file)) {
				bytes = Files.readAllBytes(file);
			} else {
				bytes = download(String.format(RAW_URL, commit, plane, zoom, tileX, tileY));
				if (bytes != null) {
					Files.createDirectories(file.getParent());
					Path partial = file.resolveSibling(file.getFileName() + ".part");
					Files.write(partial, bytes);
					Files.move(partial, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			}

			if (bytes == null) {
				missing.add(key);
				return;
			}

			BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
			if (image == null) {
				missing.add(key);
				return;
			}

			synchronized (images) {
				images.put(key, image);
			}
			onLoaded.run();
		} catch (Exception ex) {
			missing.add(key);
			log.debug("Could not load map tile {}/{}/{}/{}", plane, zoom, tileX, tileY, ex);
		} finally {
			pending.remove(key);
		}
	}

	@Nullable
	private byte[] download(String url) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
		connection.setReadTimeout(READ_TIMEOUT_MILLIS);
		try {
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
				return null;
			try (InputStream in = connection.getInputStream()) {
				return in.readAllBytes();
			}
		} finally {
			connection.disconnect();
		}
	}

	private synchronized String commit() {
		if (commit == null) {
			try {
				commit = resolveCommit();
			} catch (Exception ex) {
				log.debug("Could not work out which map tiles are current, using whatever is cached", ex);
				commit = BRANCH;
			}
		}
		return commit;
	}

	private String resolveCommit() throws IOException {
		Path marker = root.resolve("version");
		String stored = null;
		long checkedAt = 0;
		if (Files.isRegularFile(marker)) {
			String[] lines = Files.readString(marker, StandardCharsets.UTF_8).split("\n");
			stored = lines[0].trim();
			if (lines.length > 1) {
				try {
					checkedAt = Long.parseLong(lines[1].trim());
				} catch (NumberFormatException ignored) {
				}
			}
		}

		boolean due = System.currentTimeMillis() - checkedAt > RECHECK_MILLIS;
		if (stored != null && !due)
			return stored;

		String current = fetchCommit();
		if (current == null)
			return stored != null ? stored : BRANCH;

		if (!current.equals(stored)) {
			log.debug("Map tiles have changed, fetching them again at {}", current);
			deleteOldTiles(current);
		}

		Files.createDirectories(root);
		Files.writeString(marker, current + "\n" + System.currentTimeMillis(), StandardCharsets.UTF_8);
		return current;
	}

	@Nullable
	private String fetchCommit() throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(COMMIT_URL).openConnection();
		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.setRequestProperty("Accept", "application/vnd.github+json");
		connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
		connection.setReadTimeout(READ_TIMEOUT_MILLIS);
		try {
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
				return null;
			try (var reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
				var json = new Gson().fromJson(reader, JsonObject.class);
				return json != null && json.has("sha") ? json.get("sha").getAsString() : null;
			}
		} finally {
			connection.disconnect();
		}
	}

	private void deleteOldTiles(String keep) {
		if (!Files.isDirectory(root))
			return;
		try (Stream<Path> entries = Files.list(root)) {
			entries
				.filter(Files::isDirectory)
				.filter(path -> !path.getFileName().toString().equals(keep))
				.forEach(MapTileCache::deleteTree);
		} catch (IOException ex) {
			log.debug("Could not clear out the old map tiles", ex);
		}
	}

	private static void deleteTree(Path path) {
		try (Stream<Path> tree = Files.walk(path)) {
			tree.sorted(Comparator.reverseOrder()).forEach(entry -> {
				try {
					Files.deleteIfExists(entry);
				} catch (IOException ignored) {
				}
			});
		} catch (IOException ex) {
			log.debug("Could not delete {}", path, ex);
		}
	}

	private static long key(int plane, int zoom, int tileX, int tileY) {
		return (long) plane << 56 | (long) zoom << 48 | (long) (tileX & 0xFFFFFF) << 24 | tileY & 0xFFFFFF;
	}
}
