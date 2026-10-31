package rs117.hd.dev.sceneloader;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.events.BeforeRender;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;

@Slf4j
@Singleton
public class FreeCamera {
	private static final int ANGLE_UNITS = 16384;
	private static final float RADIANS_PER_UNIT = (float) (2 * Math.PI / ANGLE_UNITS);
	private static final int QUARTER_TURN = ANGLE_UNITS / 4;

	private static final float CLIENT_FRAMES_PER_SECOND = 50;

	private static final int NORMAL_SPEED = 12;
	private static final int SLOW_SPEED = 6;
	private static final int SPEED_FRACTION = 16;
	private static final float SPEED_GAINED_PER_FRAME = 1 / 8f;
	private static final float SPEED_KEPT_PER_FRAME = 0.9f;

	private static final float MIN_PITCH = 1 * RADIANS_PER_UNIT;
	private static final float MAX_PITCH = 4160 * RADIANS_PER_UNIT;

	private static final int YAW_TURN_UNITS = 24 << 3;
	private static final int PITCH_TURN_UNITS = 12 << 3;
	private static final float TURN_GAP_CLOSED_PER_FRAME = 0.5f;

	private static final float DRAG_UNITS_PER_PIXEL = 8;

	private static final float MAX_STEP_SECONDS = 0.1f;

	@Inject
	private Client client;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private EventBus eventBus;

	private boolean started;
	private volatile boolean enabled;
	private volatile float speed = 1;

	private final float[] position = new float[3];
	private volatile float yaw, pitch;

	private final float[] gamePosition = new float[3];
	private volatile float gameYaw, gamePitch;
	private volatile boolean placed;

	private final Set<Integer> heldKeys = ConcurrentHashMap.newKeySet();
	private long lastMoveNanos;

	private float travelSpeed;
	private int travelHeading = -1;
	private int travelVertical;
	private float yawSpeed, pitchSpeed;
	private boolean relaxedPitch;

	private static final float VIEWING_DISTANCE = 6 * 128;
	private static final float AIM_ABOVE_GROUND = 128;

	private final KeyEventDispatcher keys = this::dispatchKey;

	private volatile boolean dragging;
	private volatile int dragX, dragY;

	private final MouseAdapter lookAround = new MouseAdapter() {
		@Override
		public MouseEvent mousePressed(MouseEvent event) {
			if (enabled && SwingUtilities.isMiddleMouseButton(event)) {
				dragging = true;
				dragX = event.getX();
				dragY = event.getY();
				event.consume();
			}
			return event;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent event) {
			if (!dragging || !enabled)
				return event;

			int x = event.getX();
			int y = event.getY();
			turnUnits(-(x - dragX) * DRAG_UNITS_PER_PIXEL, (y - dragY) * DRAG_UNITS_PER_PIXEL);
			dragX = x;
			dragY = y;
			event.consume();
			return event;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent event) {
			if (dragging && SwingUtilities.isMiddleMouseButton(event)) {
				dragging = false;
				event.consume();
			}
			return event;
		}
	};

	public void startUp() {
		if (started)
			return;
		started = true;
		eventBus.register(this);
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
		mouseManager.registerMouseListener(lookAround);
	}

	public void shutDown() {
		if (!started)
			return;
		started = false;
		eventBus.unregister(this);
		KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
		mouseManager.unregisterMouseListener(lookAround);
		setEnabled(false);
		restoreGameCamera();
	}

	private void restoreGameCamera() {
		if (!relaxedPitch)
			return;
		relaxedPitch = false;
		client.setCameraPitchRelaxerEnabled(false);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setSpeed(float speed) {
		this.speed = speed;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled)
			return;

		heldKeys.clear();
		lastMoveNanos = 0;
		dragging = false;
		travelSpeed = 0;
		travelHeading = -1;
		travelVertical = 0;
		yawSpeed = 0;
		pitchSpeed = 0;

		this.enabled = enabled;
		if (enabled) {
			placed = false;
			takeKeyboardFocus();
		}
	}

	public void goTo(float x, float y, float z) {
		if (!enabled)
			setEnabled(true);

		float cosPitch = (float) Math.cos(pitch);
		float forwardX = -(float) Math.sin(yaw) * cosPitch;
		float forwardZ = (float) Math.cos(yaw) * cosPitch;
		float forwardY = (float) Math.sin(pitch);

		float aimY = y - AIM_ABOVE_GROUND;
		position[0] = x - forwardX * VIEWING_DISTANCE;
		position[1] = aimY - forwardY * VIEWING_DISTANCE;
		position[2] = z - forwardZ * VIEWING_DISTANCE;

		placed = true;
		travelSpeed = 0;
		travelHeading = -1;
		travelVertical = 0;
	}

	private void takeKeyboardFocus() {
		SwingUtilities.invokeLater(() -> {
			var canvas = client.getCanvas();
			if (canvas != null)
				canvas.requestFocusInWindow();
		});
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event) {
		if (relaxedPitch != enabled) {
			relaxedPitch = enabled;
			client.setCameraPitchRelaxerEnabled(enabled);
		}

		long now = System.nanoTime();
		float seconds = lastMoveNanos == 0 ? 0 : Math.min((now - lastMoveNanos) / 1e9f, MAX_STEP_SECONDS);
		lastMoveNanos = now;

		if (!enabled || seconds <= 0)
			return;

		if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == null) {
			heldKeys.clear();
			dragging = false;
		}

		float frames = seconds * CLIENT_FRAMES_PER_SECOND;
		turnWithKeys(frames);
		travel(frames);
		pointGameCameraAlong();
	}

	private void pointGameCameraAlong() {
		client.setCameraYawTarget(toJagexUnits(yaw) & 2047);
		client.setCameraPitchTarget(toJagexUnits(pitch));
	}

	private static int toJagexUnits(float radians) {
		return Math.round(radians / (float) (2 * Math.PI) * 2048);
	}

	private void turnWithKeys(float frames) {
		int wantedYaw = heldKeys.contains(KeyEvent.VK_LEFT) ? -YAW_TURN_UNITS
			: heldKeys.contains(KeyEvent.VK_RIGHT) ? YAW_TURN_UNITS : 0;
		int wantedPitch = heldKeys.contains(KeyEvent.VK_UP) ? PITCH_TURN_UNITS
			: heldKeys.contains(KeyEvent.VK_DOWN) ? -PITCH_TURN_UNITS : 0;

		float closed = 1 - (float) Math.pow(1 - TURN_GAP_CLOSED_PER_FRAME, frames);
		yawSpeed += (wantedYaw - yawSpeed) * closed;
		pitchSpeed += (wantedPitch - pitchSpeed) * closed;

		turnUnits(yawSpeed / 2 * frames, pitchSpeed / 2 * frames);
	}

	private void travel(float frames) {
		int heading = headingOf(heldKeys);
		int vertical = verticalOf(heldKeys);

		float wanted = 0;
		if (heading >= 0 || vertical != 0) {
			int perFrame = heldKeys.contains(KeyEvent.VK_CONTROL) ? SLOW_SPEED : NORMAL_SPEED;
			wanted = perFrame * speed * SPEED_FRACTION;
			travelHeading = heading;
			travelVertical = vertical;
		}

		if (travelSpeed < wanted) {
			travelSpeed = Math.min(wanted, travelSpeed + wanted * SPEED_GAINED_PER_FRAME * frames);
		} else if (travelSpeed > wanted) {
			travelSpeed *= (float) Math.pow(SPEED_KEPT_PER_FRAME, frames);
			if (travelSpeed < 1) {
				travelSpeed = 0;
				travelHeading = -1;
				travelVertical = 0;
			}
		}
		if (travelSpeed <= 0)
			return;

		float distance = travelSpeed / SPEED_FRACTION * frames;
		if (travelHeading >= 0) {
			float angle = (travelHeading - yaw / RADIANS_PER_UNIT) * RADIANS_PER_UNIT;
			position[0] += distance * (float) Math.sin(angle);
			position[2] += distance * (float) Math.cos(angle);
		}
		position[1] += travelVertical * distance;
	}

	static int headingOf(Set<Integer> held) {
		int heading = -1;
		if (held.contains(KeyEvent.VK_W)) {
			heading = 0;
		} else if (held.contains(KeyEvent.VK_S)) {
			heading = QUARTER_TURN * 2;
		}

		if (held.contains(KeyEvent.VK_A)) {
			heading = heading == 0 ? QUARTER_TURN * 7 / 2
				: heading == QUARTER_TURN * 2 ? QUARTER_TURN * 5 / 2
				: QUARTER_TURN * 3;
		} else if (held.contains(KeyEvent.VK_D)) {
			heading = heading == 0 ? QUARTER_TURN / 2
				: heading == QUARTER_TURN * 2 ? QUARTER_TURN * 3 / 2
				: QUARTER_TURN;
		}
		return heading;
	}

	static int verticalOf(Set<Integer> held) {
		boolean up = held.contains(KeyEvent.VK_R) || held.contains(KeyEvent.VK_E)
					 || held.contains(KeyEvent.VK_SPACE);
		boolean down = held.contains(KeyEvent.VK_F) || held.contains(KeyEvent.VK_Q)
					   || held.contains(KeyEvent.VK_SHIFT);
		if (up == down)
			return 0;
		return up ? -1 : 1;
	}

	private void turnUnits(float yawUnits, float pitchUnits) {
		if (yawUnits == 0 && pitchUnits == 0)
			return;
		yaw += yawUnits * RADIANS_PER_UNIT;
		pitch = clampPitch(pitch + pitchUnits * RADIANS_PER_UNIT);
	}

	private static float clampPitch(float pitch) {
		return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
	}

	static int cameraValuesAt(Object[] args) {
		if (args == null || args.length < 6)
			return -1;
		if (args[0] instanceof Scene && ((Scene) args[0]).getWorldViewId() != WorldView.TOPLEVEL)
			return -1;

		for (int i = 0; i + 5 <= args.length; i++)
			if (args[i] instanceof Float)
				return i;
		return -1;
	}

	void interceptPreSceneDraw(Object[] args) {
		int first = cameraValuesAt(args);
		if (first >= 0)
			swapCamera(args, first, true);
	}

	void interceptDrawScene(Object[] args) {
		if (args == null || args.length < 6)
			return;
		swapCamera(args, args.length - 6, false);
	}

	private void swapCamera(Object[] args, int first, boolean asFloat) {
		gamePosition[0] = ((Number) args[first]).floatValue();
		gamePosition[1] = ((Number) args[first + 1]).floatValue();
		gamePosition[2] = ((Number) args[first + 2]).floatValue();
		gamePitch = ((Number) args[first + 3]).floatValue();
		gameYaw = ((Number) args[first + 4]).floatValue();

		if (!enabled)
			return;

		if (!placed) {
			System.arraycopy(gamePosition, 0, position, 0, 3);
			yaw = gameYaw;
			pitch = clampPitch(gamePitch);
			placed = true;
			log.debug(
				"Flying from {}, {}, {} facing yaw {} pitch {}",
				position[0], position[1], position[2], yaw, pitch
			);
		}

		if (asFloat) {
			args[first] = position[0];
			args[first + 1] = position[1];
			args[first + 2] = position[2];
			args[first + 3] = pitch;
			args[first + 4] = yaw;
		} else {
			args[first] = (double) position[0];
			args[first + 1] = (double) position[1];
			args[first + 2] = (double) position[2];
			args[first + 3] = (double) pitch;
			args[first + 4] = (double) yaw;
		}
	}

	private boolean dispatchKey(KeyEvent event) {
		if (!enabled || isTyping())
			return false;

		int id = event.getID();
		int code = id == KeyEvent.KEY_TYPED
			? KeyEvent.getExtendedKeyCodeForChar(event.getKeyChar())
			: event.getKeyCode();

		if (isModifier(code)) {
			hold(id, code);
			return false;
		}
		if (!isCameraKey(code))
			return false;

		hold(id, code);
		return true;
	}

	private void hold(int eventId, int keyCode) {
		if (eventId == KeyEvent.KEY_PRESSED) {
			if (heldKeys.isEmpty())
				log.debug("Flying on {}", KeyEvent.getKeyText(keyCode));
			heldKeys.add(keyCode);
		} else if (eventId == KeyEvent.KEY_RELEASED) {
			heldKeys.remove(keyCode);
		}
	}

	private static boolean isModifier(int keyCode) {
		return keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL;
	}

	private static boolean isTyping() {
		return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent;
	}

	private static boolean isCameraKey(int keyCode) {
		switch (keyCode) {
			case KeyEvent.VK_W:
			case KeyEvent.VK_A:
			case KeyEvent.VK_S:
			case KeyEvent.VK_D:
			case KeyEvent.VK_Q:
			case KeyEvent.VK_E:
			case KeyEvent.VK_R:
			case KeyEvent.VK_F:
			case KeyEvent.VK_SPACE:
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_UP:
			case KeyEvent.VK_DOWN:
				return true;
			default:
				return false;
		}
	}
}
