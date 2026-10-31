package rs117.hd.dev.sceneloader.standalone.client;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.ObjectComposition;
import rs117.hd.dev.sceneloader.scene.Definitions;

public final class FakeObjects {
	private final Definitions defs;
	private final Map<Integer, ObjectComposition> byId = new HashMap<>();

	public FakeObjects(Definitions defs) {
		this.defs = defs;
	}

	@Nullable
	public ObjectComposition get(int id) {
		if (byId.containsKey(id))
			return byId.get(id);

		var definition = defs.object(id);
		ObjectComposition composition = definition == null ? null : Fake.of(ObjectComposition.class, new OneObject(id));
		byId.put(id, composition);
		return composition;
	}

	private final class OneObject {
		private final int id;

		OneObject(int id) {
			this.id = id;
		}

		public int getId() {
			return id;
		}

		public String getName() {
			var definition = defs.object(id);
			return definition == null ? "null" : definition.getName();
		}

		public int getSizeX() {
			var definition = defs.object(id);
			return definition == null ? 1 : definition.getSizeX();
		}

		public int getSizeY() {
			var definition = defs.object(id);
			return definition == null ? 1 : definition.getSizeY();
		}

		public int getVarbitId() {
			var definition = defs.object(id);
			return definition == null ? -1 : definition.getVarbitID();
		}

		public int getVarPlayerId() {
			var definition = defs.object(id);
			return definition == null ? -1 : definition.getVarpID();
		}

		@Nullable
		public int[] getImpostorIds() {
			var definition = defs.object(id);
			return definition == null ? null : definition.getConfigChangeDest();
		}

		@Nullable
		public ObjectComposition getImpostor() {
			int[] forms = getImpostorIds();
			if (forms == null)
				return null;

			for (int form : forms)
				if (form != -1)
					return FakeObjects.this.get(form);
			return null;
		}
	}
}
