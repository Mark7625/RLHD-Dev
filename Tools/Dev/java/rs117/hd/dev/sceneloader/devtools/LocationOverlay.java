package rs117.hd.dev.sceneloader.devtools;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import rs117.hd.HdPlugin;
import rs117.hd.dev.sceneloader.SceneLoader;
import rs117.hd.utils.HDUtils;

import static net.runelite.api.Constants.CHUNK_SIZE;

/**
 * RuneLite's Location panel, for the scene that is on screen.
 *
 * <p>The same rows in the same order, read the same way. What they describe is the camera rather than the
 * player, since a cache scene has no player in it: the player is still standing wherever the game left
 * them, usually in another part of the world entirely, and reporting that while looking at somewhere else
 * is how the original goes wrong here.
 */
@Singleton
public class LocationOverlay extends Overlay {
	private static final int TILE_SIZE = 128;

	@Inject
	private Client client;

	@Inject
	private HdPlugin plugin;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SceneLoader tools;

	@Inject
	private DevToolBridge devTools;

	private final PanelComponent panel = new PanelComponent();
	private boolean added;

	public LocationOverlay() {
		setPosition(OverlayPosition.TOP_LEFT);
	}

	public void startUp() {
		if (added)
			return;
		added = true;
		overlayManager.add(this);
	}

	public void shutDown() {
		if (!added)
			return;
		added = false;
		overlayManager.remove(this);
	}

	@Override
	public Dimension render(Graphics2D g) {
		var scene = tools.getLoaded();
		if (scene == null || !devTools.isStandingIn() || !devTools.isActive("location"))
			return null;

		int localX = Math.round(plugin.cameraPosition[0]);
		int localY = Math.round(plugin.cameraPosition[2]);
		int sceneX = Math.floorDiv(localX, TILE_SIZE);
		int sceneY = Math.floorDiv(localY, TILE_SIZE);
		int plane = client.getPlane();
		int[] world = scene.isInstance()
			? HDUtils.sceneToWorld(scene, sceneX, sceneY, plane)
			: new int[] { scene.getBaseX() + sceneX, scene.getBaseY() + sceneY, plane };

		panel.getChildren().clear();
		if (scene.isInstance())
			panel.getChildren().add(LineComponent.builder().left("Instance").build());

		panel.getChildren().add(LineComponent.builder()
			.left("Local")
			.right(localX + ", " + localY)
			.build());
		panel.getChildren().add(LineComponent.builder()
			.left("World")
			.right(world[0] + ", " + world[1] + ", " + world[2])
			.build());
		panel.getChildren().add(LineComponent.builder()
			.left("Scene")
			.right(sceneX + ", " + sceneY)
			.build());

		if (scene.isInstance()) {
			int chunk = HDUtils.templateChunkAt(scene, plane, sceneX, sceneY);
			panel.getChildren().add(LineComponent.builder()
				.left("Chunk")
				.right(chunk == -1
					? "none"
					: (chunk >> 14 & 0x3FF) * CHUNK_SIZE + ", " + (chunk >> 3 & 0x7FF) * CHUNK_SIZE
					  + ", " + (chunk >> 24 & 0x3) + " rot " + (chunk >> 1 & 0x3))
				.build());
		}

		panel.getChildren().add(LineComponent.builder()
			.left("Base")
			.right(scene.getBaseX() + ", " + scene.getBaseY())
			.build());
		panel.getChildren().add(LineComponent.builder()
			.left("Map regions")
			.right(Arrays.stream(scene.getMapRegions())
				.mapToObj(String::valueOf)
				.collect(Collectors.joining(", ")))
			.build());

		return panel.render(g);
	}
}
