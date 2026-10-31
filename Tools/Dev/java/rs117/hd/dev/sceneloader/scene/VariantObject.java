package rs117.hd.dev.sceneloader.scene;

import javax.annotation.Nullable;

public class VariantObject {
	public static final class Placed {
		public final int x, y, z;
		public final int plane;
		public final int sizeX, sizeY;

		public Placed(int x, int y, int z, int plane, int sizeX, int sizeY) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.plane = plane;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
		}
	}

	public final java.util.List<Placed> placements = new java.util.ArrayList<>();

	public final int baseId;
	public final String name;
	public int count;
	public int appliedId = -1;

	public final boolean animated;
	public final int animationId;
	public final int[] variantIds;
	public final String[] variantNames;

	@Nullable
	public String gameval;
	@Nullable
	public String[] variantGamevals;

	VariantObject(int baseId, String name, int animationId, int[] variantIds, String[] variantNames) {
		this.baseId = baseId;
		this.name = name;
		this.animationId = animationId;
		this.animated = animationId != -1;
		this.variantIds = variantIds;
		this.variantNames = variantNames;
	}

	public boolean hasVariants() {
		return variantIds.length > 0;
	}

	public int firstVisibleVariant() {
		for (int i = 0; i < variantIds.length; i++)
			if (variantIds[i] != -1)
				return i;
		return -1;
	}

	@Nullable
	public String variantLabel(int index) {
		if (index < 0 || index >= variantNames.length)
			return null;
		String gameval = variantGamevals == null || index >= variantGamevals.length ? null : variantGamevals[index];
		return gameval == null ? variantNames[index] : gameval + " (" + variantIds[index] + ")";
	}

	public String label() {
		String shown = name == null || name.isEmpty() || "null".equals(name) ? gameval : name;
		if (shown == null || shown.isEmpty())
			shown = "Object";
		return shown + " (" + baseId + ") x" + count;
	}

	@Override
	public String toString() {
		return label();
	}
}
