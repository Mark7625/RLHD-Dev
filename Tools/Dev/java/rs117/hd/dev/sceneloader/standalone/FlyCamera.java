package rs117.hd.dev.sceneloader.standalone;

import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FlyCamera {
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

	private static final float VIEWING_DISTANCE = 6 * 128;
	private static final float AIM_ABOVE_GROUND = 128;

	private final float[] position = new float[3];
	private float yaw, pitch = 1024 * RADIANS_PER_UNIT;
	private float speed = 1;

	private final Set<Integer> heldKeys = ConcurrentHashMap.newKeySet();

	private float travelSpeed;
	private int travelHeading = -1;
	private int travelVertical;
	private float yawSpeed, pitchSpeed;

	private float sinYaw, cosYaw = 1, sinPitch, cosPitch = 1;

	public float[] getPosition() {
		return position;
	}

	public float getYaw() {
		return yaw;
	}

	public float getPitch() {
		return pitch;
	}

	public void setSpeed(float speed) {
		this.speed = speed;
	}

	public void placeAt(float x, float y, float z, float yaw, float pitch) {
		position[0] = x;
		position[1] = y;
		position[2] = z;
		this.yaw = yaw;
		this.pitch = clampPitch(pitch);
		stop();
	}

	public void goTo(float x, float y, float z) {
		float level = (float) Math.cos(pitch);
		float forwardX = -(float) Math.sin(yaw) * level;
		float forwardZ = (float) Math.cos(yaw) * level;
		float forwardY = (float) Math.sin(pitch);

		float aimY = y - AIM_ABOVE_GROUND;
		position[0] = x - forwardX * VIEWING_DISTANCE;
		position[1] = aimY - forwardY * VIEWING_DISTANCE;
		position[2] = z - forwardZ * VIEWING_DISTANCE;
		stop();
	}

	private void stop() {
		travelSpeed = 0;
		travelHeading = -1;
		travelVertical = 0;
		yawSpeed = 0;
		pitchSpeed = 0;
	}

	public void keyPressed(int keyCode) {
		heldKeys.add(keyCode);
	}

	public void keyReleased(int keyCode) {
		heldKeys.remove(keyCode);
	}

	public void releaseEverything() {
		heldKeys.clear();
		stop();
	}

	public void drag(int dx, int dy) {
		turnUnits(-dx * DRAG_UNITS_PER_PIXEL, dy * DRAG_UNITS_PER_PIXEL);
	}

	public void step(float seconds) {
		if (seconds <= 0)
			return;

		float frames = Math.min(seconds, MAX_STEP_SECONDS) * CLIENT_FRAMES_PER_SECOND;
		turnWithKeys(frames);
		travel(frames);
	}

	public void beginFrame() {
		sinYaw = (float) Math.sin(yaw);
		cosYaw = (float) Math.cos(yaw);
		sinPitch = (float) Math.sin(pitch);
		cosPitch = (float) Math.cos(pitch);
	}

	public void toCamera(float x, float y, float z, float[] out) {
		float dx = x - position[0];
		float dy = y - position[1];
		float dz = z - position[2];

		float turnedX = dx * cosYaw + dz * sinYaw;
		float turnedZ = dz * cosYaw - dx * sinYaw;

		out[0] = turnedX;
		out[1] = dy * cosPitch - turnedZ * sinPitch;
		out[2] = dy * sinPitch + turnedZ * cosPitch;
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

	public static boolean isCameraKey(int keyCode) {
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
			case KeyEvent.VK_SHIFT:
			case KeyEvent.VK_CONTROL:
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_UP:
			case KeyEvent.VK_DOWN:
				return true;
			default:
				return false;
		}
	}

	public String describe() {
		return String.format(
			"%.0f, %.0f, %.0f   yaw %.0f°  pitch %.0f°",
			position[0], position[1], position[2],
			Math.toDegrees(yaw) % 360, Math.toDegrees(pitch)
		);
	}
}
