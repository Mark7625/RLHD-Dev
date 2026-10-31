package rs117.hd.dev.sceneloader.devtools;

import java.awt.Dimension;
import java.awt.Graphics2D;
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

import static rs117.hd.utils.MathUtils.JAU_FINE_TO_RAD;

/**
 * RuneLite's Camera panel, for the camera the scene is actually drawn with.
 *
 * <p>The same four rows in the same order and the same units. Only where the numbers come from differs: the
 * client's own camera stays over the player while this one is flown about, so reading it would describe a
 * view nobody is looking through. 117 HD keeps what it was handed each frame, which is what was drawn.
 */
@Singleton
public class CameraOverlay extends Overlay {
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

	public CameraOverlay() {
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
		if (tools.getLoaded() == null || !devTools.isStandingIn() || !devTools.isActive("cameraPosition"))
			return null;

		var position = plugin.cameraPosition;
		panel.getChildren().clear();

		// RuneLite prints x, then height, then northing, which is the order this is already kept in
		panel.getChildren().add(LineComponent.builder()
			.left("Camera")
			.right(Math.round(position[0]) + ", " + Math.round(position[1]) + ", " + Math.round(position[2]))
			.build());
		// Back into the whole units the client counts angles in, so these read like the original's
		panel.getChildren().add(LineComponent.builder()
			.left("Pitch")
			.right(String.valueOf(Math.round(plugin.cameraOrientation[1] / JAU_FINE_TO_RAD)))
			.build());
		panel.getChildren().add(LineComponent.builder()
			.left("Yaw")
			.right(String.valueOf(Math.round(plugin.cameraOrientation[0] / JAU_FINE_TO_RAD)))
			.build());
		panel.getChildren().add(LineComponent.builder()
			.left("Scale")
			.right(String.valueOf(client.getScale()))
			.build());

		return panel.render(g);
	}
}
