package rs117.hd.dev.sceneloader.scene;

import java.io.IOException;
import javax.annotation.Nullable;
import net.runelite.api.Model;
import net.runelite.api.Renderable;
import net.runelite.cache.definitions.FrameDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static rs117.hd.dev.sceneloader.scene.SceneBuilder.OFFSET;

public class ObjectPlacer {
	private static final int TILE_SIZE = 128;
	private static final int HALF_TILE = TILE_SIZE / 2;

	private static final int MAX_OBJECTS_PER_TILE = 5;

	private static final int WALL = 0;
	private static final int WALL_CORNER_A = 1;
	private static final int WALL_JOINT = 2;
	private static final int WALL_CORNER_B = 3;
	private static final int DECOR_AGAINST_WALL = 4;
	private static final int DECOR_OFFSET_WALL = 5;
	private static final int DECOR_DIAGONAL = 6;
	private static final int DECOR_DIAGONAL_OPPOSITE = 7;
	private static final int DECOR_DIAGONAL_BOTH = 8;
	private static final int WALL_DIAGONAL = 9;
	private static final int OBJECT = 10;
	private static final int OBJECT_DIAGONAL = 11;
	private static final int ROOF_FIRST = 12;
	private static final int GROUND_DECORATION = 22;

	private static final int[] WALL_OFFSET_X = { 1, 0, -1, 0 };
	private static final int[] WALL_OFFSET_Y = { 0, -1, 0, 1 };
	private static final int[] CORNER_OFFSET_X = { 1, -1, -1, 1 };
	private static final int[] CORNER_OFFSET_Y = { -1, -1, 1, 1 };

	private static final int NO_DISPLACEMENT = 16;
	private static final int ASSUMED_WALL_DISPLACEMENT = 16;
	private static final int ASSUMED_OBJECT_DISPLACEMENT = 8;

	private static final int HASH_TYPE_LOC = 2;
	private static final long HASH_NOT_A_DOOR = 0x80000L;
	private static final int CONFIG_SUPPORTS_ITEMS = 256;

	private static final int DECOR_ON_CORNER = 256;

	private static final int WALL_SHADOW = 50;
	private static final int MAX_OBJECT_SHADOW = 30;
	private static final int DEFAULT_OBJECT_SHADOW = 15;

	private final Definitions defs;
	private final ModelFactory models;

	public ObjectPlacer(Definitions defs, ModelFactory models) {
		this.defs = defs;
		this.models = models;
	}

	public void place(SceneBuilder.Work work, int plane, int sceneX, int sceneY, int id, int rotation, int type)
		throws IOException {
		ObjectDefinition object = defs.object(id);
		if (object == null)
			return;

		ObjectDefinition modelObject = object;
		BuiltAnimation animation = null;
		FrameDefinition[] animationFrames = null;
		boolean decidedAtRuntime = object.getConfigChangeDest() != null || object.getAnimationID() != -1;

		if (decidedAtRuntime) {
			VariantOptions.Resolution resolution = work.variants.record(object, variantDefs(object));
			if (resolution.hidden)
				return;
			if (resolution.modelObjectId != -1) {
				modelObject = defs.object(resolution.modelObjectId);
				if (modelObject == null)
					return;
			}

			animation = animationFor(object);
			if (animation != null) {
				animationFrames = framesOf(animation);
				if (animationFrames == null)
					animation = null;
			}
		}

		BuiltScene scene = work.scene;
		int[][] heights = work.heights[plane];

		int sizeX = object.getSizeX();
		int sizeY = object.getSizeY();
		if (rotation == 1 || rotation == 3) {
			int swap = sizeX;
			sizeX = sizeY;
			sizeY = swap;
		}

		int extendedX = OFFSET + sceneX;
		int extendedY = OFFSET + sceneY;

		int sampleWestX, sampleEastX;
		if (sceneX + sizeX <= work.maxTile) {
			sampleWestX = OFFSET + sceneX + (sizeX >> 1);
			sampleEastX = OFFSET + sceneX + (sizeX + 1 >> 1);
		} else {
			sampleWestX = extendedX;
			sampleEastX = extendedX + 1;
		}
		int sampleSouthY, sampleNorthY;
		if (sceneY + sizeY <= work.maxTile) {
			sampleSouthY = OFFSET + sceneY + (sizeY >> 1);
			sampleNorthY = OFFSET + sceneY + (sizeY + 1 >> 1);
		} else {
			sampleSouthY = extendedY;
			sampleNorthY = extendedY + 1;
		}
		int height = heights[sampleWestX][sampleSouthY] + heights[sampleWestX][sampleNorthY]
			+ heights[sampleEastX][sampleSouthY] + heights[sampleEastX][sampleNorthY] >> 2;

		int contourX = (sizeX << 6) + (extendedX << 7);
		int contourY = (sizeY << 6) + (extendedY << 7);

		boolean interactableWall = object.getWallOrDoor() != 0
			&& sceneX >= 0 && sceneX < 128 && sceneY >= 0 && sceneY < 128;
		long hash = hash(plane, sceneX, sceneY, HASH_TYPE_LOC, !interactableWall, id, scene.getWorldViewId());

		int config = (rotation << 6) + type;
		if (object.getSupportsItems() == 1)
			config += CONFIG_SUPPORTS_ITEMS;

		boolean smoothSeams = !decidedAtRuntime && modelObject.isMergeNormals();

		var at = new Placement(work, object, modelObject, animation, animationFrames,
			smoothSeams, plane, sceneX, sceneY,
			extendedX, extendedY, height, contourX, contourY, rotation, type, hash, config, sizeX, sizeY);

		if (decidedAtRuntime)
			work.variants.noteLocation(
				object.getId(),
				at.sceneX * TILE_SIZE + sizeX * HALF_TILE, at.height, at.sceneY * TILE_SIZE + sizeY * HALF_TILE,
				plane, sizeX, sizeY
			);

		if (type == GROUND_DECORATION) {
			placeGroundDecoration(at);
		} else if (type >= ROOF_FIRST) {
			addGameObject(at, model(at, type, rotation), 1, 1, 0);
		} else if (type == OBJECT || type == OBJECT_DIAGONAL) {
			placeObject(at);
		} else if (type == WALL) {
			placeWall(at);
		} else if (type == WALL_CORNER_A || type == WALL_CORNER_B) {
			placeWallCorner(at);
		} else if (type == WALL_JOINT) {
			placeWallJoint(at);
		} else if (type == WALL_DIAGONAL) {
			placeDiagonalWall(at);
		} else if (type >= DECOR_AGAINST_WALL && type <= DECOR_DIAGONAL_BOTH) {
			placeDecoration(at);
		}
	}

	private void placeGroundDecoration(Placement at) throws IOException {
		Slot slot = model(at, GROUND_DECORATION, at.rotation);
		if (slot.missing())
			return;

		var object = new BuiltGroundObject(
			at.work.scene, at.hash, at.config,
			at.sceneX * TILE_SIZE + HALF_TILE, at.sceneY * TILE_SIZE + HALF_TILE, at.height, at.plane,
			slot.ready
		);
		tileFor(at).setGroundObject(object);
		noteDynamic(at, object, slot.ready, object.getX(), object.getZ(), object.getY());
		defer(at, slot, NormalMerger.Kind.GROUND, 1, 1, object::setRenderable);
	}

	private void placeObject(Placement at) throws IOException {
		Slot slot = model(at, OBJECT, at.rotation);
		int modelOrientation = at.type == OBJECT_DIAGONAL ? DECOR_ON_CORNER : 0;

		if (addGameObject(at, slot, at.sizeX, at.sizeY, modelOrientation) && at.object.isShadow())
			castObjectShadow(at, slot);
	}

	private void placeWall(Placement at) throws IOException {
		addWallObject(at, model(at, WALL, at.rotation), Slot.NONE, 1 << at.rotation, 0);

		if (at.object.isShadow())
			castWallShadow(at, true);
		displaceDecoration(at, at.object.getDecorDisplacement() + 1);
	}

	private void placeWallCorner(Placement at) throws IOException {
		addWallObject(at, model(at, at.type, at.rotation), Slot.NONE, 16 << at.rotation, 0);

		if (at.object.isShadow())
			castWallShadow(at, false);
	}

	private void placeWallJoint(Placement at) throws IOException {
		int otherRotation = at.rotation + 1 & 3;
		addWallObject(
			at,
			model(at, WALL_JOINT, at.rotation + 4),
			model(at, WALL_JOINT, otherRotation),
			1 << at.rotation, 1 << otherRotation
		);
		displaceDecoration(at, at.object.getDecorDisplacement() + 1);
	}

	private void placeDiagonalWall(Placement at) throws IOException {
		addGameObject(at, model(at, WALL_DIAGONAL, at.rotation), 1, 1, 0);
		displaceDecoration(at, at.object.getDecorDisplacement() / 2 + 1);
	}

	private void placeDecoration(Placement at) throws IOException {
		int rotation = at.rotation;

		switch (at.type) {
			case DECOR_AGAINST_WALL:
				addDecorativeObject(at, model(at, DECOR_AGAINST_WALL, rotation), Slot.NONE, 1 << rotation, 0,
					WALL_OFFSET_X[rotation], WALL_OFFSET_Y[rotation], 0, 0);
				break;

			case DECOR_OFFSET_WALL: {
				int displacement = ASSUMED_WALL_DISPLACEMENT;
				var wall = tileFor(at).getWallObject();
				if (wall != null) {
					var wallDef = defs.object(wall.getId());
					if (wallDef != null)
						displacement = wallDef.getDecorDisplacement();
				}
				displacement++;

				addDecorativeObject(at, model(at, DECOR_AGAINST_WALL, rotation), Slot.NONE, 1 << rotation, 0,
					WALL_OFFSET_X[rotation] * displacement, WALL_OFFSET_Y[rotation] * displacement, 0, 0);
				break;
			}

			case DECOR_DIAGONAL: {
				int displacement = objectDisplacement(at) + 1;
				addDecorativeObject(at, model(at, DECOR_AGAINST_WALL, rotation + 4), Slot.NONE, DECOR_ON_CORNER, rotation,
					CORNER_OFFSET_X[rotation] * displacement, CORNER_OFFSET_Y[rotation] * displacement, 0, 0);
				break;
			}

			case DECOR_DIAGONAL_OPPOSITE: {
				int otherRotation = rotation + 2 & 3;
				addDecorativeObject(at, model(at, DECOR_AGAINST_WALL, otherRotation + 4), Slot.NONE,
					DECOR_ON_CORNER, otherRotation,
					CORNER_OFFSET_X[otherRotation], CORNER_OFFSET_Y[otherRotation], 0, 0);
				break;
			}

			case DECOR_DIAGONAL_BOTH: {
				int displacement = objectDisplacement(at) + 1;
				int otherRotation = rotation + 2 & 3;
				addDecorativeObject(
					at,
					model(at, DECOR_AGAINST_WALL, rotation + 4),
					model(at, DECOR_AGAINST_WALL, otherRotation + 4),
					DECOR_ON_CORNER, rotation,
					CORNER_OFFSET_X[rotation] * displacement, CORNER_OFFSET_Y[rotation] * displacement,
					CORNER_OFFSET_X[otherRotation], CORNER_OFFSET_Y[otherRotation]
				);
				break;
			}

			default:
				break;
		}
	}

	private int objectDisplacement(Placement at) {
		int displacement = ASSUMED_OBJECT_DISPLACEMENT;
		for (var object : tileFor(at).getGameObjects()) {
			if (object == null)
				continue;
			var objectDef = defs.object(object.getId());
			if (objectDef != null)
				displacement = objectDef.getDecorDisplacement() / 2;
			break;
		}
		return displacement;
	}

	private void displaceDecoration(Placement at, int displacement) {
		if (at.object.getDecorDisplacement() == NO_DISPLACEMENT)
			return;
		var decoration = tileFor(at).getDecorativeObject();
		if (decoration instanceof BuiltDecorativeObject)
			((BuiltDecorativeObject) decoration).displace(displacement);
	}

	private Slot model(Placement at, int modelType, int modelRotation) throws IOException {
		int contour = at.work.hillskew ? at.modelObject.getContouredGround() : -1;

		if (at.smoothSeams) {
			ModelDefinition unlit = models.unlitModel(at.modelObject, modelType, modelRotation);
			if (unlit == null)
				return Slot.NONE;
			if (contour >= 0)
				ModelFactory.contourDefinition(unlit, at.work.heights[at.plane], at.contourX, at.height, at.contourY, contour);
			return Slot.deferred(unlit);
		}

		if (at.animation != null)
			return animated(at, modelType, modelRotation, contour);

		BuiltModel model = models.model(at.modelObject, modelType, modelRotation);
		if (model == null)
			return Slot.NONE;
		return Slot.ready(contoured(at, model, contour));
	}

	private Slot animated(Placement at, int modelType, int modelRotation, int contour) throws IOException {
		FrameDefinition[] frames = at.animationFrames;
		assert frames != null && at.animation != null;

		var models = new Model[frames.length];
		boolean any = false;
		for (int i = 0; i < frames.length; i++) {
			BuiltModel posed = this.models.model(at.modelObject, modelType, modelRotation, frames[i]);
			if (posed == null)
				continue;
			models[i] = contoured(at, posed, contour);
			any = true;
		}
		if (!any)
			return Slot.NONE;

		return Slot.ready(new BuiltDynamicObject(at.object.getId(), at.animation, models));
	}

	private BuiltModel contoured(Placement at, BuiltModel model, int contour) {
		if (contour < 0)
			return model;
		return ModelFactory.contour(
			model, at.work.heights[at.plane], at.contourX, at.height, at.contourY, contour
		);
	}

	private void noteDynamic(
		Placement at, net.runelite.api.TileObject owner, @Nullable Renderable renderable, int x, int y, int z
	) {
		if (!(renderable instanceof BuiltDynamicObject))
			return;
		var dynamic = (BuiltDynamicObject) renderable;
		if (!dynamic.hasModel())
			return;
		at.work.scene.addDynamic(new BuiltScene.Dynamic(owner, dynamic, 0, x, y, z));
	}

	private void defer(
		Placement at, Slot slot, NormalMerger.Kind kind, int sizeX, int sizeY, java.util.function.Consumer<BuiltModel> assign
	) {
		if (slot.deferred == null)
			return;
		var pending = new NormalMerger.Pending(
			slot.deferred, at.modelObject, kind, at.plane, at.extendedX, at.extendedY, sizeX, sizeY, assign
		);
		pending.region = at.work.region;
		at.work.merger.add(pending);
	}

	private BuiltTile tileFor(Placement at) {
		return at.work.scene.tile(at.plane, at.extendedX, at.extendedY);
	}

	private void ensureColumn(BuiltScene scene, int plane, int x, int y) {
		for (int below = plane; below >= 0; below--)
			scene.tile(below, x, y);
	}

	private boolean addGameObject(Placement at, Slot slot, int sizeX, int sizeY, int modelOrientation) {
		if (slot.missing())
			return true;

		BuiltScene scene = at.work.scene;

		for (int x = at.extendedX; x < at.extendedX + sizeX; x++) {
			for (int y = at.extendedY; y < at.extendedY + sizeY; y++) {
				if (x < 0 || y < 0 || x >= EXTENDED_SCENE_SIZE || y >= EXTENDED_SCENE_SIZE)
					return false;
				BuiltTile tile = scene.tileOrNull(at.plane, x, y);
				if (tile != null && tile.gameObjectCount() >= MAX_OBJECTS_PER_TILE)
					return false;
			}
		}

		var object = new BuiltGameObject(
			scene, at.hash, at.config,
			at.sceneX * TILE_SIZE + sizeX * HALF_TILE,
			at.sceneY * TILE_SIZE + sizeY * HALF_TILE,
			at.height, at.plane,
			slot.ready, modelOrientation,
			at.sceneX, at.sceneY, at.sceneX + sizeX - 1, at.sceneY + sizeY - 1
		);

		for (int x = at.extendedX; x < at.extendedX + sizeX; x++) {
			for (int y = at.extendedY; y < at.extendedY + sizeY; y++) {
				ensureColumn(scene, at.plane, x, y);
				scene.tile(at.plane, x, y).addGameObject(object);
			}
		}

		noteDynamic(at, object, slot.ready, object.getX(), object.getZ(), object.getY());
		defer(at, slot, NormalMerger.Kind.GAME, sizeX, sizeY, object::setRenderable);
		return true;
	}

	private void addWallObject(Placement at, Slot first, Slot second, int orientationA, int orientationB) {
		if (first.missing() && second.missing())
			return;

		BuiltScene scene = at.work.scene;
		ensureColumn(scene, at.plane, at.extendedX, at.extendedY);

		var object = new BuiltWallObject(
			scene, at.hash, at.config,
			at.sceneX * TILE_SIZE + HALF_TILE, at.sceneY * TILE_SIZE + HALF_TILE, at.height, at.plane,
			first.ready, second.ready, orientationA, orientationB
		);
		scene.tile(at.plane, at.extendedX, at.extendedY).setWallObject(object);

		noteDynamic(at, object, first.ready, object.getX(), object.getZ(), object.getY());
		noteDynamic(at, object, second.ready, object.getX(), object.getZ(), object.getY());
		defer(at, first, NormalMerger.Kind.WALL_A, 1, 1, object::setRenderable1);
		defer(at, second, NormalMerger.Kind.WALL_B, 1, 1, object::setRenderable2);
	}

	private void addDecorativeObject(
		Placement at, Slot first, Slot second,
		int wallSides, int secondOrientation, int xOffset, int yOffset, int xOffset2, int yOffset2
	) {
		if (first.missing())
			return;

		BuiltScene scene = at.work.scene;
		ensureColumn(scene, at.plane, at.extendedX, at.extendedY);

		var object = new BuiltDecorativeObject(
			scene, at.hash, at.config,
			at.sceneX * TILE_SIZE + HALF_TILE, at.sceneY * TILE_SIZE + HALF_TILE, at.height, at.plane,
			first.ready, second.ready, wallSides, secondOrientation, xOffset, yOffset, xOffset2, yOffset2
		);
		scene.tile(at.plane, at.extendedX, at.extendedY).setDecorativeObject(object);

		noteDynamic(at, object, first.ready, object.getX(), object.getZ(), object.getY());
		noteDynamic(at, object, second.ready, object.getX(), object.getZ(), object.getY());
		defer(at, first, NormalMerger.Kind.GROUND, 1, 1, object::setRenderable);
		defer(at, second, NormalMerger.Kind.GROUND, 1, 1, object::setRenderable2);
	}

	private void castWallShadow(Placement at, boolean straight) {
		byte[][] shadow = at.work.shadow[at.plane];
		int x = at.extendedX;
		int y = at.extendedY;

		if (straight) {
			switch (at.rotation) {
				case 0:
					shadow[x][y] = WALL_SHADOW;
					shadow[x][y + 1] = WALL_SHADOW;
					break;
				case 1:
					shadow[x][y + 1] = WALL_SHADOW;
					shadow[x + 1][y + 1] = WALL_SHADOW;
					break;
				case 2:
					shadow[x + 1][y] = WALL_SHADOW;
					shadow[x + 1][y + 1] = WALL_SHADOW;
					break;
				default:
					shadow[x][y] = WALL_SHADOW;
					shadow[x + 1][y] = WALL_SHADOW;
					break;
			}
		} else {
			switch (at.rotation) {
				case 0:
					shadow[x][y + 1] = WALL_SHADOW;
					break;
				case 1:
					shadow[x + 1][y + 1] = WALL_SHADOW;
					break;
				case 2:
					shadow[x + 1][y] = WALL_SHADOW;
					break;
				default:
					shadow[x][y] = WALL_SHADOW;
					break;
			}
		}
	}

	private void castObjectShadow(Placement at, Slot slot) {
		int amount = DEFAULT_OBJECT_SHADOW;
		if (slot.ready instanceof BuiltModel) {
			var model = (BuiltModel) slot.ready;
			model.calculateBoundsCylinder();
			amount = Math.min(model.xyzMag / 4, MAX_OBJECT_SHADOW);
		}

		byte[][] shadow = at.work.shadow[at.plane];
		for (int dx = 0; dx <= at.sizeX; dx++) {
			for (int dy = 0; dy <= at.sizeY; dy++) {
				int x = at.extendedX + dx;
				int y = at.extendedY + dy;
				if (x < 0 || y < 0 || x >= shadow.length || y >= shadow[0].length)
					continue;
				if (amount > shadow[x][y])
					shadow[x][y] = (byte) amount;
			}
		}
	}

	public static long hash(int plane, int sceneX, int sceneY, int type, boolean notADoor, int id, int worldViewId) {
		long hash = (sceneX & 127L)
			| (sceneY & 127L) << 7
			| (plane & 3L) << 14
			| (type & 7L) << 16
			| (id & 0xFFFFFFFFL) << 20
			| (worldViewId & 4095L) << 52;
		if (notADoor)
			hash |= HASH_NOT_A_DOOR;
		return hash;
	}

	private static final class Slot {
		static final Slot NONE = new Slot(null, null);

		@Nullable
		final Renderable ready;
		@Nullable
		final ModelDefinition deferred;

		private Slot(@Nullable Renderable ready, @Nullable ModelDefinition deferred) {
			this.ready = ready;
			this.deferred = deferred;
		}

		static Slot ready(Renderable renderable) {
			return new Slot(renderable, null);
		}

		static Slot deferred(ModelDefinition model) {
			return new Slot(null, model);
		}

		boolean missing() {
			return ready == null && deferred == null;
		}
	}

	/**
	 * The animation an object plays, which is always the one the id itself names.
	 *
	 * <p>A transform only decides which object supplies the model. The animation stays with the one that
	 * was placed on the map, even when that one has no model of its own: the game reads the animation once
	 * when it spawns the object and then transforms afresh every frame purely to find the model to pose.
	 * Taking it from the transformed object instead leaves anything whose animation lives on the parent
	 * standing still, which for a model animated by fading its faces also means it never becomes see
	 * through.
	 */
	@Nullable
	private BuiltAnimation animationFor(ObjectDefinition object) {
		int animationId = object.getAnimationID();
		if (animationId == -1)
			return null;

		var sequence = defs.sequence(animationId);
		if (sequence == null || sequence.frameIDs == null || sequence.frameIDs.length == 0)
			return null;

		return new BuiltAnimation(sequence);
	}

	@Nullable
	private FrameDefinition[] framesOf(BuiltAnimation animation) {
		int[] frameIds = animation.definition().frameIDs;
		var frames = new FrameDefinition[frameIds.length];
		for (int i = 0; i < frameIds.length; i++) {
			frames[i] = defs.frame(frameIds[i]);
			if (frames[i] == null)
				return null;
		}
		return frames;
	}

	@Nullable
	private ObjectDefinition[] variantDefs(ObjectDefinition object) {
		int[] variants = object.getConfigChangeDest();
		if (variants == null)
			return null;

		var defsForVariants = new ObjectDefinition[variants.length];
		for (int i = 0; i < variants.length; i++)
			if (variants[i] != -1)
				defsForVariants[i] = defs.object(variants[i]);
		return defsForVariants;
	}

	private static final class Placement {
		final SceneBuilder.Work work;
		final ObjectDefinition object;
		final ObjectDefinition modelObject;
		@Nullable
		final BuiltAnimation animation;
		@Nullable
		final FrameDefinition[] animationFrames;
		final boolean smoothSeams;
		final int plane;
		final int sceneX, sceneY;
		final int extendedX, extendedY;
		final int height;
		final int contourX, contourY;
		final int rotation, type;
		final long hash;
		final int config;
		final int sizeX, sizeY;

		Placement(
			SceneBuilder.Work work, ObjectDefinition object, ObjectDefinition modelObject,
			@Nullable BuiltAnimation animation, @Nullable FrameDefinition[] animationFrames,
			boolean smoothSeams, int plane,
			int sceneX, int sceneY, int extendedX, int extendedY,
			int height, int contourX, int contourY,
			int rotation, int type, long hash, int config, int sizeX, int sizeY
		) {
			this.work = work;
			this.object = object;
			this.modelObject = modelObject;
			this.animation = animation;
			this.animationFrames = animationFrames;
			this.smoothSeams = smoothSeams;
			this.plane = plane;
			this.sceneX = sceneX;
			this.sceneY = sceneY;
			this.extendedX = extendedX;
			this.extendedY = extendedY;
			this.height = height;
			this.contourX = contourX;
			this.contourY = contourY;
			this.rotation = rotation;
			this.type = type;
			this.hash = hash;
			this.config = config;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
		}
	}
}
