package rs117.hd.dev.sceneloader.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.models.VertexNormal;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.MAX_Z;

public class NormalMerger {
	private static final int TILE_SIZE = 128;
	private static final int HALF_TILE = TILE_SIZE / 2;

	private static final int MIN_SHARED_TO_HIDE = 3;
	private static final byte FACE_HIDDEN = 2;

	public enum Kind {
		WALL_A,
		WALL_B,
		GAME,
		GROUND
	}

	public static final class Pending {
		final ModelDefinition model;
		final ObjectDefinition object;
		final Kind kind;
		final int plane;
		final int extendedX, extendedY;
		final int sizeX, sizeY;
		final Consumer<BuiltModel> assign;
		/**
		 * Which of the scene's regions put it down.
		 *
		 * <p>Normals are merged between neighbours so the light runs smoothly over a join, which is right
		 * for two things that really do stand next to each other. Regions laid side by side only touch by
		 * arrangement, so anything on one side of the join is lit by its own region alone.
		 */
		int region;

		final ModelBounds bounds;
		VertexNormal[] merged;
		final int[] marks;
		boolean lit;

		public Pending(
			ModelDefinition model, ObjectDefinition object, Kind kind,
			int plane, int extendedX, int extendedY, int sizeX, int sizeY,
			Consumer<BuiltModel> assign
		) {
			this.model = model;
			this.object = object;
			this.kind = kind;
			this.plane = plane;
			this.extendedX = extendedX;
			this.extendedY = extendedY;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.assign = assign;

			model.computeNormals();
			this.bounds = ModelBounds.of(model);
			this.marks = new int[model.vertexCount];
		}
	}

	private final List<Pending> pending = new ArrayList<>();
	private final Map<Integer, List<Pending>> byTile = new HashMap<>();
	private int pass;

	public boolean isEmpty() {
		return pending.isEmpty();
	}

	public void add(Pending entry) {
		pending.add(entry);
		for (int x = entry.extendedX; x < entry.extendedX + entry.sizeX; x++)
			for (int y = entry.extendedY; y < entry.extendedY + entry.sizeY; y++)
				if (x >= 0 && y >= 0 && x < EXTENDED_SCENE_SIZE && y < EXTENDED_SCENE_SIZE)
					byTile.computeIfAbsent(key(entry.plane, x, y), k -> new ArrayList<>()).add(entry);
	}

	public void run(int[][][] heights) {
		pending.sort(Comparator
			.comparingInt((Pending p) -> p.plane)
			.thenComparingInt(p -> p.extendedX)
			.thenComparingInt(p -> p.extendedY)
			.thenComparingInt(p -> p.kind.ordinal()));

		for (Pending entry : pending) {
			if (entry.lit)
				continue;

			if (entry.kind == Kind.GROUND) {
				mergeGroundNeighbours(entry, heights);
			} else {
				mergeNeighbours(entry, heights);
			}
		}

		for (Pending entry : pending) {
			if (entry.kind != Kind.WALL_A)
				continue;
			Pending other = wallPartner(entry);
			if (other != null)
				merge(entry, other, 0, 0, 0, false);
		}

		for (Pending entry : pending)
			light(entry);
	}

	private Pending wallPartner(Pending wall) {
		for (Pending other : at(wall.plane, wall.extendedX, wall.extendedY))
			if (other.kind == Kind.WALL_B)
				return other;
		return null;
	}

	private void mergeNeighbours(Pending entry, int[][][] heights) {
		boolean sameTileRules = true;
		int xStart = entry.extendedX;
		int xEnd = entry.extendedX + entry.sizeX;
		int yStart = entry.extendedY - 1;
		int yEnd = entry.extendedY + entry.sizeY;

		int ownHeight = tileHeight(heights, entry.plane, entry.extendedX, entry.extendedY);

		for (int plane = entry.plane; plane <= entry.plane + 1; plane++) {
			if (plane >= MAX_Z)
				continue;

			for (int x = xStart; x <= xEnd; x++) {
				if (x < 0 || x >= EXTENDED_SCENE_SIZE)
					continue;

				for (int y = yStart; y <= yEnd; y++) {
					if (y < 0 || y >= EXTENDED_SCENE_SIZE)
						continue;

					if (sameTileRules && !(x >= xEnd || y >= yEnd || y < entry.extendedY && entry.extendedX != x))
						continue;

					int dy = tileHeight(heights, plane, x, y) - ownHeight;

					for (Pending other : at(plane, x, y)) {
						if (other == entry || other.region != entry.region)
							continue;

						if (other.kind == Kind.WALL_A || other.kind == Kind.WALL_B) {
							merge(
								entry, other,
								(x - entry.extendedX) * TILE_SIZE + (1 - entry.sizeX) * HALF_TILE,
								dy,
								(y - entry.extendedY) * TILE_SIZE + (1 - entry.sizeY) * HALF_TILE,
								sameTileRules
							);
						} else if (other.kind == Kind.GAME) {
							merge(
								entry, other,
								(other.extendedX - entry.extendedX) * TILE_SIZE + (other.sizeX - entry.sizeX) * HALF_TILE,
								dy,
								(other.extendedY - entry.extendedY) * TILE_SIZE + (other.sizeY - entry.sizeY) * HALF_TILE,
								sameTileRules
							);
						}
					}
				}
			}

			xStart--;
			sameTileRules = false;
		}
	}

	private void mergeGroundNeighbours(Pending entry, int[][][] heights) {
		int xEnd = entry.extendedX + 1;
		int yEnd = entry.extendedY + 1;
		int ownHeight = tileHeight(heights, entry.plane, entry.extendedX, entry.extendedY);

		for (int x = entry.extendedX; x <= xEnd; x++) {
			if (x < 0 || x >= EXTENDED_SCENE_SIZE)
				continue;

			for (int y = entry.extendedY - 1; y <= yEnd; y++) {
				if (y < 0 || y >= EXTENDED_SCENE_SIZE)
					continue;
				if (x < xEnd && y < yEnd)
					continue;

				int dy = tileHeight(heights, entry.plane, x, y) - ownHeight;
				for (Pending other : at(entry.plane, x, y)) {
					if (other == entry || other.kind != Kind.GROUND || other.region != entry.region)
						continue;
					merge(
						entry, other,
						(x - entry.extendedX) * TILE_SIZE, dy, (y - entry.extendedY) * TILE_SIZE,
						true
					);
				}
			}
		}
	}

	private void merge(Pending a, Pending b, int dx, int dy, int dz, boolean hideSharedFaces) {
		pass++;
		int shared = 0;

		for (int v = 0; v < a.model.vertexCount; v++) {
			VertexNormal aNormal = a.model.vertexNormals[v];
			if (aNormal == null || aNormal.magnitude == 0)
				continue;

			int y = a.model.vertexY[v] - dy;
			if (y > b.bounds.maxY)
				continue;
			int x = a.model.vertexX[v] - dx;
			if (x < b.bounds.minX || x > b.bounds.maxX)
				continue;
			int z = a.model.vertexZ[v] - dz;
			if (z < b.bounds.minZ || z > b.bounds.maxZ)
				continue;

			for (int w = 0; w < b.model.vertexCount; w++) {
				VertexNormal bNormal = b.model.vertexNormals[w];
				if (bNormal == null || bNormal.magnitude == 0)
					continue;
				if (b.model.vertexX[w] != x || b.model.vertexZ[w] != z || b.model.vertexY[w] != y)
					continue;

				if (a.merged == null)
					a.merged = new VertexNormal[a.model.vertexCount];
				if (b.merged == null)
					b.merged = new VertexNormal[b.model.vertexCount];

				VertexNormal aMerged = a.merged[v];
				if (aMerged == null)
					aMerged = a.merged[v] = copyOf(aNormal);
				VertexNormal bMerged = b.merged[w];
				if (bMerged == null)
					bMerged = b.merged[w] = copyOf(bNormal);

				aMerged.x += bNormal.x;
				aMerged.y += bNormal.y;
				aMerged.z += bNormal.z;
				aMerged.magnitude += bNormal.magnitude;

				bMerged.x += aNormal.x;
				bMerged.y += aNormal.y;
				bMerged.z += aNormal.z;
				bMerged.magnitude += aNormal.magnitude;

				shared++;
				a.marks[v] = pass;
				b.marks[w] = pass;
			}
		}

		if (shared < MIN_SHARED_TO_HIDE || !hideSharedFaces)
			return;

		hideFullySharedFaces(a);
		hideFullySharedFaces(b);
	}

	private void hideFullySharedFaces(Pending entry) {
		ModelDefinition model = entry.model;
		for (int face = 0; face < model.faceCount; face++) {
			if (entry.marks[model.faceIndices1[face]] != pass
				|| entry.marks[model.faceIndices2[face]] != pass
				|| entry.marks[model.faceIndices3[face]] != pass)
				continue;

			if (model.faceRenderTypes == null)
				model.faceRenderTypes = new byte[model.faceCount];
			model.faceRenderTypes[face] = FACE_HIDDEN;
		}
	}

	private void light(Pending entry) {
		if (entry.lit)
			return;
		entry.lit = true;

		BuiltModel model = ModelFactory.light(
			entry.model,
			entry.object.getAmbient() + 64,
			entry.object.getContrast() + 768,
			entry.merged
		);
		entry.assign.accept(model);
	}

	private List<Pending> at(int plane, int x, int y) {
		List<Pending> found = byTile.get(key(plane, x, y));
		return found == null ? java.util.Collections.emptyList() : found;
	}

	private static int tileHeight(int[][][] heights, int plane, int x, int y) {
		int[][] plan = heights[plane];
		return plan[x + 1][y + 1] + plan[x][y] + plan[x + 1][y] + plan[x][y + 1] >> 2;
	}

	private static VertexNormal copyOf(VertexNormal normal) {
		var copy = new VertexNormal();
		copy.x = normal.x;
		copy.y = normal.y;
		copy.z = normal.z;
		copy.magnitude = normal.magnitude;
		return copy;
	}

	private static int key(int plane, int x, int y) {
		return (plane * EXTENDED_SCENE_SIZE + x) * EXTENDED_SCENE_SIZE + y;
	}
}
