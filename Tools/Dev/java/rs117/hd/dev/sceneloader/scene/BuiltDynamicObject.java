package rs117.hd.dev.sceneloader.scene;

import javax.annotation.Nullable;
import net.runelite.api.Animation;
import net.runelite.api.DynamicObject;
import net.runelite.api.Model;
import net.runelite.api.ObjectComposition;

public class BuiltDynamicObject implements net.runelite.api.Renderable, DynamicObject {
	public static final int CYCLE_MILLIS = 20;

	private final int objectId;
	@Nullable
	private final BuiltAnimation animation;
	private final Model[] frames;

	private int frame;
	private int cycleInFrame;
	private int loops;
	private int lastCycle = Integer.MIN_VALUE;

	private int modelHeight;

	public BuiltDynamicObject(int objectId, @Nullable BuiltAnimation animation, Model[] frames) {
		this.objectId = objectId;
		this.animation = animation;
		this.frames = frames;
	}

	public boolean posesDiffer() {
		if (posesChecked)
			return posesMove;
		posesChecked = true;

		Model first = null;
		for (Model model : frames) {
			if (model == null)
				continue;
			if (first == null) {
				first = model;
				continue;
			}
			if (movedFrom(first, model)) {
				posesMove = true;
				break;
			}
		}
		return posesMove;
	}

	private boolean posesChecked;
	private boolean posesMove;

	private static boolean movedFrom(Model a, Model b) {
		if (a == b)
			return false;

		float[] ax = a.getVerticesX(), bx = b.getVerticesX();
		float[] az = a.getVerticesZ(), bz = b.getVerticesZ();
		if (ax.length != bx.length)
			return true;
		for (int i = 0; i < ax.length; i++)
			if (ax[i] != bx[i] || az[i] != bz[i])
				return true;

		byte[] at = a.getFaceTransparencies(), bt = b.getFaceTransparencies();
		if (at == null || bt == null)
			return at != bt;
		if (at.length != bt.length)
			return true;
		for (int i = 0; i < at.length; i++)
			if (at[i] != bt[i])
				return true;
		return false;
	}

	public boolean hasModel() {
		for (Model model : frames)
			if (model != null)
				return true;
		return false;
	}

	public Model modelAt(int cycle) {
		advanceTo(cycle);
		return getModel();
	}

	private void advanceTo(int cycle) {
		if (animation == null || frames.length <= 1)
			return;

		if (lastCycle == Integer.MIN_VALUE) {
			lastCycle = cycle;
			return;
		}

		int elapsed = cycle - lastCycle;
		lastCycle = cycle;
		if (elapsed <= 0)
			return;

		int frameCount = frames.length;
		int frameStep = animation.getFrameStep();
		int duration = animation.getDuration();
		if (frameStep > 0 && duration > 0)
			elapsed %= duration;

		if (frame >= frameCount) {
			frame = 0;
			cycleInFrame = 0;
		}

		int spent = elapsed + cycleInFrame;
		while (spent > animation.frameLength(frame)) {
			spent -= animation.frameLength(frame);
			frame++;

			if (frame >= frameCount) {
				loops++;
				frame -= frameStep;
				if (frame < 0 || frame >= frameCount)
					frame = 0;
			}
		}
		cycleInFrame = spent;
	}


	@Override
	public Model getModel() {
		int at = Math.min(Math.max(frame, 0), frames.length - 1);
		if (frames[at] != null)
			return frames[at];

		for (int away = 1; away < frames.length; away++) {
			int before = at - away;
			if (before >= 0 && frames[before] != null)
				return frames[before];
			int after = at + away;
			if (after < frames.length && frames[after] != null)
				return frames[after];
		}
		return null;
	}

	@Override
	public int getModelHeight() {
		if (modelHeight == 0) {
			Model model = getModel();
			modelHeight = model == null ? 0 : model.getModelHeight();
		}
		return modelHeight;
	}

	@Override
	public void setModelHeight(int modelHeight) {
		this.modelHeight = modelHeight;
	}

	@Override
	public int getRenderMode() {
		return 0;
	}

	@Override
	public int getAnimationHeightOffset() {
		return 0;
	}

	@Override
	public int getAnimFrame() {
		return frame;
	}

	@Override
	public int getAnimCycle() {
		return cycleInFrame;
	}

	@Nullable
	@Override
	public Model getModelZbuf() {
		return animation == null ? getModel() : null;
	}

	@Nullable
	@Override
	public Animation getAnimation() {
		return animation;
	}

	@Nullable
	@Override
	public ObjectComposition getRecordedObjectComposition() {
		return null;
	}

	public int getObjectId() {
		return objectId;
	}


	@Override
	public net.runelite.api.Node getNext() {
		return null;
	}

	@Override
	public net.runelite.api.Node getPrevious() {
		return null;
	}

	@Override
	public long getHash() {
		return 0;
	}

	@Override
	public String toString() {
		return "BuiltDynamicObject(" + objectId
			   + ", animation " + (animation == null ? "none" : animation.getId())
			   + ", " + frames.length + " frames)";
	}
}
