package rs117.hd.dev.sceneloader.scene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.runelite.cache.definitions.ObjectDefinition;

public class VariantOptions {
	public static final int HIDDEN = -1;
	public static final int DEFAULT = -2;

	private final Map<Integer, Integer> choices = new ConcurrentHashMap<>();
	private final Map<Integer, VariantObject> found = new LinkedHashMap<>();

	public int choiceFor(int baseId) {
		return choices.getOrDefault(baseId, DEFAULT);
	}

	public void setChoice(int baseId, int choice) {
		choices.put(baseId, choice);
	}

	public void clearChoices() {
		choices.clear();
	}

	public synchronized List<VariantObject> getFound() {
		var sorted = new ArrayList<VariantObject>();
		for (VariantObject entry : found.values())
			if (entry.hasVariants())
				sorted.add(entry);
		sorted.sort((a, b) -> b.count - a.count);
		return sorted;
	}

	synchronized void noteLocation(int baseId, int x, int y, int z, int plane, int sizeX, int sizeY) {
		VariantObject entry = found.get(baseId);
		if (entry != null)
			entry.placements.add(new VariantObject.Placed(x, y, z, plane, sizeX, sizeY));
	}

	synchronized void startBuild() {
		found.clear();
	}

	synchronized Resolution record(ObjectDefinition object, @Nullable ObjectDefinition[] variantDefs) {
		int baseId = object.getId();
		VariantObject entry = found.get(baseId);

		if (entry == null) {
			int[] variantIds = object.getConfigChangeDest() == null ? new int[0] : object.getConfigChangeDest();
			var names = new String[variantIds.length];
			for (int i = 0; i < variantIds.length; i++) {
				if (variantIds[i] == -1) {
					names[i] = "Nothing";
				} else {
					ObjectDefinition def = variantDefs == null ? null : variantDefs[i];
					String name = def == null ? null : def.getName();
					boolean unnamed = name == null || name.isEmpty() || "null".equals(name);
					names[i] = (unnamed ? "Form" : name) + " (" + variantIds[i] + ")";
				}
			}
			entry = new VariantObject(baseId, object.getName(), object.getAnimationID(), variantIds, names);
			found.put(baseId, entry);
		}
		entry.count++;

		int choice = choiceFor(baseId);
		if (choice == HIDDEN) {
			entry.appliedId = -1;
			return Resolution.HIDE;
		}

		if (!entry.hasVariants()) {
			entry.appliedId = baseId;
			return Resolution.OWN_MODELS;
		}

		int index = choice == DEFAULT ? entry.firstVisibleVariant() : choice;
		if (index < 0 || index >= entry.variantIds.length)
			index = entry.firstVisibleVariant();
		if (index < 0 || entry.variantIds[index] == -1) {
			entry.appliedId = -1;
			return Resolution.HIDE;
		}

		entry.appliedId = entry.variantIds[index];
		return Resolution.variant(entry.appliedId);
	}

	static final class Resolution {
		static final Resolution HIDE = new Resolution(true, -1);
		static final Resolution OWN_MODELS = new Resolution(false, -1);

		final boolean hidden;
		final int modelObjectId;

		private Resolution(boolean hidden, int modelObjectId) {
			this.hidden = hidden;
			this.modelObjectId = modelObjectId;
		}

		static Resolution variant(int objectId) {
			return new Resolution(false, objectId);
		}
	}
}
