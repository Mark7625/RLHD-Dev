package rs117.hd.dev.sceneloader.devtools;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Lets RuneLite's own scene tools keep working on a scene they know nothing about.
 *
 * <p>Their buttons are left alone and read rather than disabled, so pressing Walls still means walls. What
 * is taken away is their drawing: every one of them reads the scene straight off the client and draws it
 * through the client's camera, and neither is what is on screen while a cache scene is up. They would
 * outline tiles where nothing stands and name objects belonging to wherever the player happens to be.
 *
 * <p>So their overlays are lifted out for as long as a cache scene is loaded and {@link SceneToolsOverlay}
 * draws the same things from this scene instead. The buttons go on working as they always did; only what
 * answers them changes.
 */
@Slf4j
@Singleton
public class DevToolBridge {
	private static final String DEV_TOOLS_PACKAGE = "net.runelite.client.plugins.devtools.";
	private static final String DEV_TOOLS_PLUGIN = DEV_TOOLS_PACKAGE + "DevToolsPlugin";

	/**
	 * The ones there is nothing here for them to describe.
	 *
	 * <p>A cache scene is ground and scenery and nothing else: nobody is standing in it, nothing is moving
	 * through it, and it carries none of the collision the client walks on. These are switched off and
	 * greyed while it is up, since leaving them pressable would only ever answer about somewhere else.
	 * Everything that is not about the scene at all — the widget, variable, script and inventory
	 * inspectors, the shell and the rest — is left alone.
	 */
	private static final List<String> UNANSWERABLE = List.of(
		"players",
		"npcs",
		"groundItems",
		"graphicsObjects",
		"projectiles",
		"interacting",
		"worldEntities",
		"lineOfSight",
		"validMovement",
		"movementFlags",
		"zoneBorders",
		"loadingLines",
		"worldMapLocation",
		"detachedCamera"
	);

	private static final String WHY_GREYED =
		"Nothing for this to describe while a cache scene is loaded: it is ground and scenery only.";

	@Inject
	private PluginManager pluginManager;

	@Inject
	private OverlayManager overlayManager;

	/** Their overlays while they are lifted out, so the same ones can be put back */
	private final List<Overlay> lifted = new ArrayList<>();
	/** Which of the greyed ones were switched on, so they come back the way they were */
	private final List<String> wasActive = new ArrayList<>();
	private final Map<String, Field> buttons = new HashMap<>();
	private boolean standingIn;

	/** Whether one of RuneLite's tools is switched on, by the name of its button's field */
	public boolean isActive(String name) {
		Field field = button(name);
		if (field == null)
			return false;

		try {
			Object button = field.get(find());
			Method isActive = button.getClass().getDeclaredMethod("isActive");
			isActive.setAccessible(true);
			return (Boolean) isActive.invoke(button);
		} catch (Exception ex) {
			log.debug("Could not read RuneLite's {} tool", name, ex);
			return false;
		}
	}

	@Nullable
	private Field button(String name) {
		if (buttons.containsKey(name))
			return buttons.get(name);

		Field found = null;
		Plugin devTools = find();
		if (devTools != null) {
			try {
				found = devTools.getClass().getDeclaredField(name);
				found.setAccessible(true);
			} catch (Exception ex) {
				log.debug("RuneLite has no {} tool", name, ex);
			}
		}
		buttons.put(name, found);
		return found;
	}

	/** Takes over the drawing, leaving the buttons where they are */
	public void standIn() {
		if (standingIn)
			return;
		standingIn = true;

		// The predicate is what does the lifting, since there is no way to ask which overlays are in there
		overlayManager.removeIf(overlay -> {
			if (!overlay.getClass().getName().startsWith(DEV_TOOLS_PACKAGE))
				return false;
			lifted.add(overlay);
			return true;
		});

		SwingUtilities.invokeLater(() -> greyOut(true));
	}

	/** Hands the drawing back */
	public void standDown() {
		if (!standingIn)
			return;
		standingIn = false;

		for (Overlay overlay : lifted)
			overlayManager.add(overlay);
		lifted.clear();

		SwingUtilities.invokeLater(() -> greyOut(false));
	}

	private void greyOut(boolean grey) {
		for (String name : UNANSWERABLE) {
			Field field = button(name);
			if (field == null)
				continue;

			try {
				Object button = field.get(find());
				if (!(button instanceof AbstractButton))
					continue;

				if (grey) {
					if (isActive(name))
						wasActive.add(name);
					setActive(button, false);
				} else if (wasActive.contains(name)) {
					setActive(button, true);
				}

				((AbstractButton) button).setEnabled(!grey);
				((AbstractButton) button).setToolTipText(grey ? WHY_GREYED : null);
			} catch (Exception ex) {
				log.debug("Could not grey RuneLite's {} tool", name, ex);
			}
		}

		if (!grey)
			wasActive.clear();
	}

	private static void setActive(Object button, boolean active) throws Exception {
		Method setActive = button.getClass().getDeclaredMethod("setActive", boolean.class);
		setActive.setAccessible(true);
		setActive.invoke(button, active);
	}

	public boolean isStandingIn() {
		return standingIn;
	}

	@Nullable
	private Plugin find() {
		for (Plugin plugin : pluginManager.getPlugins())
			if (DEV_TOOLS_PLUGIN.equals(plugin.getClass().getName()))
				return plugin;
		return null;
	}
}
