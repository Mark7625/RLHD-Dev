package rs117.hd.dev.sceneloader.scene;

import net.runelite.api.Animation;
import net.runelite.cache.definitions.SequenceDefinition;

public class BuiltAnimation implements Animation {
	private final SequenceDefinition sequence;
	private final int duration;

	private int restartMode;

	public BuiltAnimation(SequenceDefinition sequence) {
		this.sequence = sequence;

		int total = 0;
		if (sequence.frameLengths != null)
			for (int length : sequence.frameLengths)
				total += length;
		this.duration = total;
	}

	public SequenceDefinition definition() {
		return sequence;
	}

	public int frameCount() {
		return sequence.frameIDs == null ? 0 : sequence.frameIDs.length;
	}

	public int frameLength(int frame) {
		int[] lengths = sequence.frameLengths;
		if (lengths == null || frame < 0 || frame >= lengths.length)
			return 1;
		return Math.max(1, lengths[frame]);
	}

	@Override
	public int getId() {
		return sequence.getId();
	}

	@Override
	public int getDuration() {
		return duration;
	}

	@Override
	public int getNumFrames() {
		return frameCount();
	}

	@Override
	public int getRestartMode() {
		return restartMode;
	}

	@Override
	public void setRestartMode(int restartMode) {
		this.restartMode = restartMode;
	}

	@Override
	public int[] getFrameLengths() {
		return sequence.frameLengths;
	}

	@Override
	public int getLeftHandItem() {
		return sequence.leftHandItem;
	}

	@Override
	public int getRightHandItem() {
		return sequence.rightHandItem;
	}

	@Override
	public int getFrameStep() {
		return sequence.frameStep;
	}

	@Override
	public boolean isMayaAnim() {
		return sequence.animMayaID != -1;
	}

	public int maxLoops() {
		return sequence.getMaxLoops();
	}
}
