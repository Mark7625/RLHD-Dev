package rs117.hd.dev.sceneloader.scene;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.OverlayDefinition;
import net.runelite.cache.definitions.UnderlayDefinition;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.Region;
import rs117.hd.dev.sceneloader.ModelPreview;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.MAX_Z;
import static net.runelite.api.Constants.SCENE_SIZE;
import static rs117.hd.dev.sceneloader.scene.ModelFactory.LOC_OBJECT;

public class SceneBuilder {
	public static final int EXTENDED = EXTENDED_SCENE_SIZE;
	public static final int OFFSET = BuiltScene.SCENE_OFFSET;
	private static final int HEIGHT_GRID = EXTENDED + 1;

	public static final int NO_COLOR = 12345678;
	private static final int HIDDEN_OVERLAY = 0xFF00FF;

	private static final int LIGHT_X = -50;
	private static final int LIGHT_Y = -10;
	private static final int LIGHT_Z = -50;
	private static final int LIGHT_MAGNITUDE = (int) Math.sqrt(5100.0) * 768 >> 8;
	private static final int AMBIENT_LIGHT = 96;

	private static final int BLUR_RADIUS = 5;

	private static final int BLANK_SHADOW = 127;

	private static final int BASE_CHUNK_OFFSET = 6;
	private static final int BLANK_FILL_CHUNK_LIMIT = 800;

	private final Definitions defs;
	private final HslPalette palette;
	private final ObjectPlacer placer;
	private final ModelFactory factory;

	public SceneBuilder(Definitions defs) {
		this(defs, new HslPalette(HslPalette.DEFAULT_BRIGHTNESS));
	}

	public SceneBuilder(Definitions defs, HslPalette palette) {
		this.defs = defs;
		this.palette = palette;
		this.factory = new ModelFactory(defs);
		this.placer = new ObjectPlacer(defs, factory);
	}

	@Nullable
	public ModelPreview.Shape preview(int objectId, int type) throws IOException {
		ObjectDefinition object = defs.object(objectId);
		if (object == null)
			return null;

		int[] types = object.getObjectTypes();
		if (type < 0)
			type = types == null || types.length == 0 ? LOC_OBJECT : types[0];

		BuiltModel model = factory.model(object, type, 0);
		if (model == null || model.getFaceCount() == 0)
			return null;

		int faceCount = model.getFaceCount();
		int[] colors1 = model.getFaceColors1();
		int[] colors2 = model.getFaceColors2();
		int[] colors3 = model.getFaceColors3();
		short[] textures = model.getFaceTextures();
		byte[] transparencies = model.getFaceTransparencies();

		var faceColors = new int[faceCount];
		for (int f = 0; f < faceCount; f++) {
			if (colors3[f] == -2 || transparencies != null && (transparencies[f] & 0xFF) == 0xFF) {
				faceColors[f] = -1;
				continue;
			}

			int texture = textures == null ? -1 : textures[f];
			if (texture != -1) {
				faceColors[f] = palette.toRgb(defs.textureColor(texture));
			} else if (colors3[f] == -1) {
				faceColors[f] = palette.toRgb(colors1[f]);
			} else {
				int a = palette.toRgb(colors1[f]);
				int b = palette.toRgb(colors2[f]);
				int c = palette.toRgb(colors3[f]);
				faceColors[f] =
					(((a >> 16 & 0xFF) + (b >> 16 & 0xFF) + (c >> 16 & 0xFF)) / 3) << 16 |
					(((a >> 8 & 0xFF) + (b >> 8 & 0xFF) + (c >> 8 & 0xFF)) / 3) << 8 |
					((a & 0xFF) + (b & 0xFF) + (c & 0xFF)) / 3;
			}
		}

		return new ModelPreview.Shape(model, faceColors);
	}

	public BuiltScene build(int worldX, int worldY) throws IOException {
		return build(worldX, worldY, 0, 0, new VariantOptions());
	}

	public BuiltScene build(int worldX, int worldY, int expandedChunks, int worldViewId) throws IOException {
		return build(worldX, worldY, expandedChunks, worldViewId, new VariantOptions());
	}

	private static int baseOf(int worldTile) {
		return ((worldTile >> 3) - BASE_CHUNK_OFFSET) * 8;
	}

	public static int[] sceneTiles(int worldX, int worldY, int expandedChunks) {
		int baseX = baseOf(worldX);
		int baseY = baseOf(worldY);
		int minTile = -expandedChunks * 8;
		int maxTile = SCENE_SIZE + expandedChunks * 8;
		return new int[] {
			baseX + minTile,
			baseY + minTile,
			baseX + maxTile - 1,
			baseY + maxTile - 1,
		};
	}

	public static int[] regionBlock(int worldX, int worldY, int expandedChunks) {
		int[] tiles = sceneTiles(worldX, worldY, expandedChunks);
		return new int[] { tiles[0] >> 6, tiles[1] >> 6, tiles[2] >> 6, tiles[3] >> 6 };
	}

	public BuiltScene build(
		int worldX, int worldY, int expandedChunks, int worldViewId, VariantOptions variants
	) throws IOException {
		variants.startBuild();

		int baseX = baseOf(worldX);
		int baseY = baseOf(worldY);

		int minTile = -expandedChunks * 8;
		int maxTile = SCENE_SIZE + expandedChunks * 8;

		List<Region> regions = new ArrayList<>();
		List<int[]> origins = new ArrayList<>();
		List<Integer> regionIds = new ArrayList<>();

		int[] block = regionBlock(worldX, worldY, expandedChunks);
		int firstRegionX = block[0];
		int firstRegionY = block[1];
		int lastRegionX = block[2];
		int lastRegionY = block[3];

		for (int regionX = firstRegionX; regionX <= lastRegionX; regionX++) {
			for (int regionY = firstRegionY; regionY <= lastRegionY; regionY++) {
				regions.add(defs.region(regionX, regionY));
				origins.add(new int[] { (regionX << 6) - baseX, (regionY << 6) - baseY });
				regionIds.add(regionX << 8 | regionY);
			}
		}

		return assemble(
			regions, origins, regionIds, baseX, baseY, worldViewId, variants, minTile, maxTile,
			(worldY >> 3) < BLANK_FILL_CHUNK_LIMIT, true, null, null
		);
	}

	/**
	 * How much space to leave between regions that are not neighbours, in tiles.
	 *
	 * <p>Taken off the far side of the region itself rather than added between them. That keeps every
	 * region on the same sixty four tile pitch, so two that really do adjoin in the world can still be laid
	 * against each other and drawn whole, while anything unrelated is set apart by this much. A whole
	 * number of chunks, because a region says where it really is through the instance template, which names
	 * a whole chunk at a time.
	 */
	public static final int REGION_GAP = 8;

	/**
	 * How many regions fit across the scene.
	 *
	 * <p>Three rather than the two that whole regions would allow. A region is sixty four tiles and the
	 * scene a hundred and eighty four, so three of them only fit if the last gives up the eight tiles it
	 * cannot have: two at full width and one clipped comes to exactly the scene.
	 */
	public static final int REGIONS_PER_SIDE = (EXTENDED + REGION_GAP) / Region.X;

	/**
	 * How many regions may be picked at once.
	 *
	 * <p>Fewer than the nine the scene has room for. Nine is a lot of ground to build and most of them end
	 * up giving away their edges to make space, so this is held lower on purpose. Raising it to
	 * {@code REGIONS_PER_SIDE * REGIONS_PER_SIDE} is all that is needed to fill the grid.
	 */
	public static final int MAX_STITCHED_REGIONS = 5;

	/** One region in a stitched scene: where it was put, and whether its neighbours there are the real ones */
	private static final class Stitched {
		final int id;
		final int originX, originY;
		/** How much ground it covers, before anything beside it is taken into account */
		/** How many of its sixty four tiles are drawn, which is fewer where something unrelated is next to it */
		final int drawnX, drawnY;
		/** Which run of adjoining regions it belongs to, so lighting runs across one but not between two */
		final int group;

		Stitched(int id, int originX, int originY, int drawnX, int drawnY, int group) {
			this.id = id;
			this.originX = originX;
			this.originY = originY;
			this.drawnX = drawnX;
			this.drawnY = drawnY;
			this.group = group;
		}
	}

	public BuiltScene buildStitched(int[] wantedRegions, int worldViewId, VariantOptions variants)
		throws IOException {
		variants.startBuild();

		int minTile = -OFFSET;
		int maxTile = SCENE_SIZE + OFFSET;
		List<Stitched> placed = layOut(wantedRegions);
		if (placed.isEmpty())
			return build(3222, 3218, 0, worldViewId, variants);

		List<Region> regions = new ArrayList<>();
		List<int[]> origins = new ArrayList<>();
		List<Integer> regionIds = new ArrayList<>();
		var drawn = new ArrayList<int[]>();
		var groups = new ArrayList<Integer>();

		for (Stitched entry : placed) {
			regions.add(defs.region(entry.id >> 8, entry.id & 0xFF));
			origins.add(new int[] { entry.originX, entry.originY });
			regionIds.add(entry.id);
			drawn.add(new int[] { entry.drawnX, entry.drawnY });
			groups.add(entry.group);
		}

		int[] first = origins.get(0);
		Region firstRegion = regions.get(0);
		int baseX = (placed.get(0).id >> 8 << 6) - first[0];
		int baseY = ((placed.get(0).id & 0xFF) << 6) - first[1];
		if (firstRegion != null) {
			baseX = firstRegion.getBaseX() - first[0];
			baseY = firstRegion.getBaseY() - first[1];
		}

		BuiltScene scene = assemble(
			regions, origins, regionIds, baseX, baseY, worldViewId, variants, minTile, maxTile,
			true, true, drawn, groups);
		scene.setInstanceTemplateChunks(templateChunks(regionIds, origins));
		return scene;
	}

	/**
	 * Works out where each picked region goes.
	 *
	 * <p>Regions that adjoin each other in the world are gathered into one group and laid out at their true
	 * offsets, so the group reads as one continuous piece of map. Groups are then packed into the scene with
	 * a gap between them, since there is nothing continuous about two places that merely got picked
	 * together. Everything lands on a chunk boundary, which the instance template needs.
	 *
	 * <p>Anything that will not fit is left out rather than overlapped.
	 */
	/**
	 * Which of the picked regions will actually fit, in the order they would be laid out.
	 *
	 * <p>Worth asking before building, since whether they fit depends on their shape and not just how many
	 * there are. Two regions side by side next to two more stacked one above the other needs a hundred and
	 * ninety two tiles whichever way round it is put, and the scene is a hundred and eighty four.
	 */
	public static int[] regionsThatFit(int[] wantedRegions) {
		var placed = layOut(wantedRegions);
		var fitting = new int[placed.size()];
		for (int i = 0; i < fitting.length; i++)
			fitting[i] = placed.get(i).id;
		return fitting;
	}

	private static List<Stitched> layOut(int[] wantedRegions) {
		int minTile = -OFFSET;
		int span = EXTENDED;
		var picked = new ArrayList<Integer>();
		for (int id : wantedRegions)
			if (!picked.contains(id) && picked.size() < MAX_STITCHED_REGIONS)
				picked.add(id);

		var pending = new ArrayDeque<int[]>(groupNeighbours(picked));
		var groups = new ArrayList<int[]>();
		var offsets = new ArrayList<int[]>();

		// Packed left to right, starting a new row when a group will not fit on this one. A group that
		// will not fit anywhere is broken up and its regions placed one at a time: they lose the join
		// between them, which is a shame, but it beats not showing them at all. A pair side by side next
		// to a pair stacked up needs a hundred and ninety two tiles and the scene has a hundred and
		// eighty four, so without this the second pair would simply be missing.
		int cursorX = 0, cursorY = 0, rowHeight = 0, widest = 0;
		while (!pending.isEmpty()) {
			int[] group = pending.poll();
			// What it must have: every region at full pitch but the last, which can give up its far edge
			int width = group[2] * Region.X - REGION_GAP;
			int height = group[3] * Region.Y - REGION_GAP;

			int wrapX = cursorX, wrapY = cursorY, wrapRow = rowHeight;
			if (wrapX > 0 && wrapX + width > span) {
				wrapX = 0;
				wrapY += wrapRow;
				wrapRow = 0;
			}

			if (width > span || wrapY + height > span) {
				var apart = members(picked, group);
				if (apart.size() > 1)
					for (int i = apart.size() - 1; i >= 0; i--)
						pending.addFirst(new int[] { apart.get(i) >> 8, apart.get(i) & 0xFF, 1, 1 });
				continue;
			}

			cursorX = wrapX;
			cursorY = wrapY;
			rowHeight = wrapRow;

			groups.add(group);
			offsets.add(new int[] { cursorX, cursorY });
			widest = Math.max(widest, cursorX + width);
			rowHeight = Math.max(rowHeight, group[3] * Region.Y);
			// On to the next cell, so whatever follows starts a whole region along
			cursorX += group[2] * Region.X;
		}
		int tall = Math.min(cursorY + rowHeight, span);
		widest = Math.min(widest, span);

		int leftPad = alignToChunk(minTile + (span - widest) / 2);
		int topPad = alignToChunk(minTile + (span - tall) / 2);

		var placed = new ArrayList<Stitched>();
		for (int i = 0; i < groups.size(); i++) {
			int[] group = groups.get(i);
			int[] offset = offsets.get(i);
			int groupX = leftPad + offset[0];
			// Packed downwards, but the scene counts upwards, so the rows are read back the other way
			int groupY = topPad + tall - offset[1] - group[3] * Region.Y;

			for (int id : members(picked, group)) {
				placed.add(new Stitched(
					id,
					groupX + ((id >> 8) - group[0]) * Region.X,
					groupY + ((id & 0xFF) - group[1]) * Region.Y,
					Region.X, Region.Y, i
				));
			}
		}
		return joinUp(placed, minTile + span);
	}

	/**
	 * Works out how much of each region to draw, once everything has been placed.
	 *
	 * <p>A region gives up the far eight tiles of itself to make a space, unless what sits there is the
	 * region that really does adjoin it, in which case the two are drawn whole and meet seamlessly. Being
	 * neighbours in the world is not enough on its own: a group that had to be broken up to fit has its
	 * regions scattered about, and two halves of a pair sitting apart do not share a join. So it is asked
	 * of the layout rather than of the picking.
	 *
	 * <p>A region with nothing beyond it is drawn whole as well, since there is nothing to be apart from,
	 * except where the scene runs out first and it is clipped to what is left.
	 */
	private static List<Stitched> joinUp(List<Stitched> placed, int lastTile) {
		var measured = new ArrayList<Stitched>(placed.size());
		for (Stitched entry : placed) {
			int drawnX = Region.X, drawnY = Region.Y;

			for (Stitched other : placed) {
				boolean east = other.originX == entry.originX + Region.X && other.originY == entry.originY;
				boolean north = other.originY == entry.originY + Region.Y && other.originX == entry.originX;
				if (east && other.id != entry.id + (1 << 8))
					drawnX = Region.X - REGION_GAP;
				if (north && other.id != entry.id + 1)
					drawnY = Region.Y - REGION_GAP;
			}

			drawnX = Math.min(drawnX, lastTile - entry.originX);
			drawnY = Math.min(drawnY, lastTile - entry.originY);
			measured.add(new Stitched(entry.id, entry.originX, entry.originY, drawnX, drawnY, entry.group));
		}
		return measured;
	}

	/**
	 * Gathers the picked regions into groups that adjoin in the world.
	 *
	 * @return one entry per group: its lowest region x and y, then how many regions it spans each way
	 */
	private static List<int[]> groupNeighbours(List<Integer> picked) {
		var groups = new ArrayList<int[]>();
		var taken = new ArrayList<Integer>();

		for (int id : picked) {
			if (taken.contains(id))
				continue;

			var queue = new ArrayDeque<Integer>();
			queue.add(id);
			taken.add(id);
			int minX = id >> 8, maxX = minX, minY = id & 0xFF, maxY = minY;

			while (!queue.isEmpty()) {
				int at = queue.poll();
				minX = Math.min(minX, at >> 8);
				maxX = Math.max(maxX, at >> 8);
				minY = Math.min(minY, at & 0xFF);
				maxY = Math.max(maxY, at & 0xFF);

				for (int side : new int[] { 1 << 8, -(1 << 8), 1, -1 }) {
					int next = at + side;
					if (picked.contains(next) && !taken.contains(next)) {
						taken.add(next);
						queue.add(next);
					}
				}
			}

			// No more of a group than the scene has room for, since the rest could not be drawn anyway
			maxX = Math.min(maxX, minX + REGIONS_PER_SIDE - 1);
			maxY = Math.min(maxY, minY + REGIONS_PER_SIDE - 1);
			groups.add(new int[] { minX, minY, maxX - minX + 1, maxY - minY + 1 });
		}
		return groups;
	}

	/** The picked regions inside a group's bounds, bottom row first */
	private static List<Integer> members(List<Integer> picked, int[] group) {
		var found = new ArrayList<Integer>();
		for (int id : picked) {
			int regionX = id >> 8, regionY = id & 0xFF;
			if (regionX >= group[0] && regionX < group[0] + group[2]
				&& regionY >= group[1] && regionY < group[1] + group[3])
				found.add(id);
		}
		return found;
	}

	/** The nearest chunk boundary at or before a scene tile, counted from the extended scene's own corner */
	private static int alignToChunk(int sceneTile) {
		return Math.floorDiv(sceneTile + OFFSET, 8) * 8 - OFFSET;
	}

	private static int[][][] templateChunks(List<Integer> regionIds, List<int[]> origins) {
		int side = BuiltScene.CHUNKS_PER_SIDE;
		var chunks = new int[MAX_Z][side][side];
		for (int[][] plane : chunks)
			for (int[] column : plane)
				java.util.Arrays.fill(column, -1);

		int chunksPerRegion = Region.X / 8;
		for (int i = 0; i < regionIds.size(); i++) {
			int id = regionIds.get(i);
			int regionChunkX = (id >> 8) * chunksPerRegion;
			int regionChunkY = (id & 0xFF) * chunksPerRegion;
			int[] origin = origins.get(i);

			for (int plane = 0; plane < MAX_Z; plane++) {
				for (int cx = 0; cx < chunksPerRegion; cx++) {
					for (int cy = 0; cy < chunksPerRegion; cy++) {
						int sceneChunkX = (origin[0] + OFFSET) / 8 + cx;
						int sceneChunkY = (origin[1] + OFFSET) / 8 + cy;
						if (sceneChunkX < 0 || sceneChunkY < 0 || sceneChunkX >= side || sceneChunkY >= side)
							continue;

						chunks[plane][sceneChunkX][sceneChunkY] =
							plane << 24 | (regionChunkX + cx) << 14 | (regionChunkY + cy) << 3;
					}
				}
			}
		}
		return chunks;
	}

	private BuiltScene assemble(
		List<Region> regions, List<int[]> origins, List<Integer> regionIds,
		int baseX, int baseY, int worldViewId, VariantOptions variants,
		int minTile, int maxTile, boolean fillBlanks, boolean hillskew,
		@Nullable List<int[]> drawn, @Nullable List<Integer> groups
	) throws IOException {
		int[] mapRegions = new int[regionIds.size()];
		for (int i = 0; i < mapRegions.length; i++)
			mapRegions[i] = regionIds.get(i);

		Work work = new Work(new BuiltScene(baseX, baseY, worldViewId, mapRegions), minTile, maxTile, variants);
		work.hillskew = hillskew;
		work.isolateRegions = drawn != null;

		for (int i = 0; i < regions.size(); i++) {
			Region region = regions.get(i);
			if (region == null)
				continue;
			int[] size = drawn == null ? null : drawn.get(i);
			readTerrain(
				work, region, origins.get(i)[0], origins.get(i)[1],
				size == null ? Region.X : size[0], size == null ? Region.Y : size[1]
			);
		}
		if (fillBlanks)
			for (int i = 0; i < regions.size(); i++)
				if (regions.get(i) == null)
					blank(work, origins.get(i)[0], origins.get(i)[1]);

		for (int i = 0; i < regions.size(); i++) {
			Region region = regions.get(i);
			if (region == null)
				continue;
			work.region = groups == null ? 0 : groups.get(i);
			int[] size = drawn == null ? null : drawn.get(i);
			placeObjects(
				work, region, origins.get(i)[0], origins.get(i)[1],
				size == null ? Region.X : size[0], size == null ? Region.Y : size[1]
			);
		}

		if (!work.merger.isEmpty())
			work.merger.run(work.heights);

		shade(work);
		linkBridges(work);
		work.scene.buildRoofs();

		return work.scene;
	}

	/**
	 * Reads one region's terrain into the scene at the place it has been given.
	 *
	 * <p>A tile is drawn between its own corner and the next one along, so the tiles down a region's far
	 * side read a corner belonging to whatever lies beyond. Where that really is the next region, the
	 * ground is continuous and the corner holds the one height both of them want, so there is nothing to
	 * do. Where it is a region from somewhere else entirely, or empty scene, those tiles would be stretched
	 * from one landscape to the other, which is the wall that appears along such a join. Since the two
	 * cannot both own the corner, that one row of tiles is left undrawn instead.
	 *
	 * <p>The heights are read either way, one row past the region, so the row behind an undrawn one has a
	 * corner of its own to sit flat against rather than dropping away to nothing.
	 *
	 * @param joinedEast  whether the region that really adjoins it to the east is in the scene beside it
	 * @param joinedNorth the same to the north
	 */
	private void readTerrain(
		Work work, Region region, int originX, int originY, int drawnX, int drawnY
	) {
		int edge = work.isolateRegions ? 1 : 0;
		boolean joinedEast = drawnX >= Region.X;
		boolean joinedNorth = drawnY >= Region.Y;

		for (int plane = 0; plane < MAX_Z; plane++) {
			for (int localX = 0; localX < Region.X + edge; localX++) {
				int sceneX = originX + localX;
				if (sceneX < work.minTile || sceneX >= work.maxTile)
					continue;
				int x = OFFSET + sceneX;
				int sampleX = Math.min(localX, Region.X - 1);

				for (int localY = 0; localY < Region.Y + edge; localY++) {
					int sceneY = originY + localY;
					if (sceneY < work.minTile || sceneY >= work.maxTile)
						continue;
					int y = OFFSET + sceneY;
					int sampleY = Math.min(localY, Region.Y - 1);

					// Never over a real neighbour's own corner, which it has already read or will read
					if (localX < Region.X && localY < Region.Y
						|| !(joinedEast && localX >= Region.X)
						&& !(joinedNorth && localY >= Region.Y))
						work.heights[plane][x][y] = region.getTileHeight(plane, sampleX, sampleY);

					if (localX >= drawnX || localY >= drawnY)
						continue;

					work.settings[plane][x][y] = region.getTileSetting(plane, localX, localY);
					work.underlayIds[plane][x][y] = (short) region.getUnderlayId(plane, localX, localY);
					work.overlayIds[plane][x][y] = (short) region.getOverlayId(plane, localX, localY);
					work.shapes[plane][x][y] = region.getOverlayPath(plane, localX, localY);
					work.rotations[plane][x][y] = region.getOverlayRotation(plane, localX, localY);
				}
			}
		}
	}

	private void blank(Work work, int originX, int originY) {
		int width = Region.X;
		int height = Region.Y;
		for (int sceneY = originY; sceneY <= originY + height; sceneY++) {
			for (int sceneX = originX; sceneX <= originX + width; sceneX++) {
				if (sceneX < work.minTile || sceneX >= work.maxTile || sceneY < work.minTile || sceneY >= work.maxTile)
					continue;

				int x = OFFSET + sceneX;
				int y = OFFSET + sceneY;
				work.shadow[0][x][y] = BLANK_SHADOW;

				if (sceneX == originX && sceneX > 0)
					work.heights[0][x][y] = work.heights[0][x - 1][y];
				if (sceneX == originX + width && sceneX < EXTENDED - 1)
					work.heights[0][x][y] = work.heights[0][x + 1][y];
				if (sceneY == originY && sceneY > 0)
					work.heights[0][x][y] = work.heights[0][x][y - 1];
				if (sceneY == originY + height && sceneY < EXTENDED - 1)
					work.heights[0][x][y] = work.heights[0][x][y + 1];
			}
		}
	}

	/**
	 * Puts down everything the region has, so long as there is ground under it.
	 *
	 * <p>A region that has given up its far edge to make space keeps its locations there all the same, and
	 * a wall or a tree standing over the gap with nothing beneath it reads as a fault rather than a gap.
	 * So the same edge is kept off both.
	 */
	private void placeObjects(Work work, Region region, int originX, int originY, int drawnX, int drawnY)
		throws IOException {
		for (Location location : region.getLocations()) {
			Position position = location.getPosition();
			int localX = position.getX() - region.getBaseX();
			int localY = position.getY() - region.getBaseY();
			if (localX < 0 || localY < 0 || localX >= drawnX || localY >= drawnY)
				continue;

			int sceneX = localX + originX;
			int sceneY = localY + originY;
			int plane = position.getZ();

			if (sceneX <= work.minTile || sceneY <= work.minTile || sceneX >= work.maxTile - 1 || sceneY >= work.maxTile - 1)
				continue;
			if (plane < 0 || plane >= MAX_Z)
				continue;

			placer.place(work, plane, sceneX, sceneY, location.getId(), location.getOrientation(), location.getType());
		}
	}

	private void light(Work work, int plane) {
		byte[][] shadow = work.shadow[plane];
		int[][] heights = work.heights[plane];

		for (int y = 1; y < EXTENDED - 1; y++) {
			for (int x = 1; x < EXTENDED - 1; x++) {
				int slopeX = heights[x + 1][y] - heights[x - 1][y];
				int slopeY = heights[x][y + 1] - heights[x][y - 1];
				int length = (int) Math.sqrt(slopeX * slopeX + slopeY * slopeY + 65536);

				int normalX = (slopeX << 8) / length;
				int normalY = 65536 / length;
				int normalZ = (slopeY << 8) / length;

				int lit = (normalY * LIGHT_Y + normalX * LIGHT_X + normalZ * LIGHT_Z) / LIGHT_MAGNITUDE + AMBIENT_LIGHT;
				int shaded = (shadow[x][y] >> 1)
					+ (shadow[x + 1][y] >> 3)
					+ (shadow[x][y - 1] >> 2)
					+ (shadow[x][y + 1] >> 3)
					+ (shadow[x - 1][y] >> 2);

				work.lightness[x][y] = lit - shaded;
			}
		}
	}

	private void shade(Work work) {
		for (int plane = 0; plane < MAX_Z; plane++) {
			light(work, plane);

			int[] hues = new int[EXTENDED];
			int[] saturations = new int[EXTENDED];
			int[] lightnesses = new int[EXTENDED];
			int[] multipliers = new int[EXTENDED];
			int[] counts = new int[EXTENDED];

			for (int x = -BLUR_RADIUS; x < EXTENDED + BLUR_RADIUS; x++) {
				for (int y = 0; y < EXTENDED; y++) {
					int entering = x + BLUR_RADIUS;
					if (entering >= 0 && entering < EXTENDED) {
						UnderlayDefinition underlay = underlayAt(work, plane, entering, y);
						if (underlay != null) {
							hues[y] += underlay.getHue();
							saturations[y] += underlay.getSaturation();
							lightnesses[y] += underlay.getLightness();
							multipliers[y] += underlay.getHueMultiplier();
							counts[y]++;
						}
					}

					int leaving = x - BLUR_RADIUS;
					if (leaving >= 0 && leaving < EXTENDED) {
						UnderlayDefinition underlay = underlayAt(work, plane, leaving, y);
						if (underlay != null) {
							hues[y] -= underlay.getHue();
							saturations[y] -= underlay.getSaturation();
							lightnesses[y] -= underlay.getLightness();
							multipliers[y] -= underlay.getHueMultiplier();
							counts[y]--;
						}
					}
				}

				if (x < 1 || x >= EXTENDED - 1)
					continue;

				int hue = 0, saturation = 0, lightness = 0, multiplier = 0, count = 0;
				for (int y = -BLUR_RADIUS; y < EXTENDED + BLUR_RADIUS; y++) {
					int entering = y + BLUR_RADIUS;
					if (entering >= 0 && entering < EXTENDED) {
						hue += hues[entering];
						saturation += saturations[entering];
						lightness += lightnesses[entering];
						multiplier += multipliers[entering];
						count += counts[entering];
					}

					int leaving = y - BLUR_RADIUS;
					if (leaving >= 0 && leaving < EXTENDED) {
						hue -= hues[leaving];
						saturation -= saturations[leaving];
						lightness -= lightnesses[leaving];
						multiplier -= multipliers[leaving];
						count -= counts[leaving];
					}

					if (y < 1 || y >= EXTENDED - 1)
						continue;

					emit(work, plane, x, y, hue, saturation, lightness, multiplier, count);
				}
			}
		}
	}

	@Nullable
	private UnderlayDefinition underlayAt(Work work, int plane, int x, int y) {
		int id = work.underlayIds[plane][x][y] & 0x7FFF;
		return id > 0 ? defs.underlay(id - 1) : null;
	}

	private void emit(Work work, int plane, int x, int y, int hue, int saturation, int lightness, int multiplier, int count) {
		int underlayId = work.underlayIds[plane][x][y] & 0x7FFF;
		int overlayId = work.overlayIds[plane][x][y] & 0x7FFF;
		if (underlayId == 0 && overlayId == 0)
			return;

		int[][] heights = work.heights[plane];
		int southWestY = heights[x][y];
		int southEastY = heights[x + 1][y];
		int northEastY = heights[x + 1][y + 1];
		int northWestY = heights[x][y + 1];

		int southWestLight = work.lightness[x][y];
		int southEastLight = work.lightness[x + 1][y];
		int northEastLight = work.lightness[x + 1][y + 1];
		int northWestLight = work.lightness[x][y + 1];

		int underlayHsl = -1;
		if (underlayId > 0 && count > 0 && multiplier > 0)
			underlayHsl = packHsl(hue * 256 / multiplier, saturation / count, lightness / count);

		int underlayRgb = underlayHsl == -1 ? 0 : palette.toRgb(shadeUnderlay(underlayHsl, AMBIENT_LIGHT));

		if (overlayId == 0) {
			addTile(
				work, plane, x, y, 0, 0, -1,
				southWestY, southEastY, northEastY, northWestY,
				shadeUnderlay(underlayHsl, southWestLight),
				shadeUnderlay(underlayHsl, southEastLight),
				shadeUnderlay(underlayHsl, northEastLight),
				shadeUnderlay(underlayHsl, northWestLight),
				0, 0, 0, 0,
				underlayRgb, 0
			);
			return;
		}

		int shape = work.shapes[plane][x][y] + 1;
		int rotation = work.rotations[plane][x][y];

		OverlayDefinition overlay = defs.overlay(overlayId - 1);
		int texture = overlay == null ? -1 : overlay.getTexture();

		int overlayHsl;
		int flatHsl;
		if (texture >= 0) {
			flatHsl = defs.textureColor(texture);
			overlayHsl = -1;
		} else if (overlay == null || overlay.getRgbColor() == HIDDEN_OVERLAY) {
			overlayHsl = -2;
			flatHsl = -2;
			texture = -1;
		} else {
			overlayHsl = packHsl(overlay.getHue(), overlay.getSaturation(), overlay.getLightness());
			flatHsl = overlayHsl;
		}

		int overlayRgb = flatHsl == -2 ? 0 : palette.toRgb(shadeOverlay(flatHsl, AMBIENT_LIGHT));
		if (overlay != null && overlay.getSecondaryRgbColor() != -1) {
			int secondary = packHsl(overlay.getOtherHue(), overlay.getOtherSaturation(), overlay.getOtherLightness());
			overlayRgb = palette.toRgb(shadeOverlay(secondary, AMBIENT_LIGHT));
		}

		addTile(
			work, plane, x, y, shape, rotation, texture,
			southWestY, southEastY, northEastY, northWestY,
			shadeUnderlay(underlayHsl, southWestLight),
			shadeUnderlay(underlayHsl, southEastLight),
			shadeUnderlay(underlayHsl, northEastLight),
			shadeUnderlay(underlayHsl, northWestLight),
			shadeOverlay(overlayHsl, southWestLight),
			shadeOverlay(overlayHsl, southEastLight),
			shadeOverlay(overlayHsl, northEastLight),
			shadeOverlay(overlayHsl, northWestLight),
			underlayRgb, overlayRgb
		);
	}

	private void addTile(
		Work work, int plane, int x, int y, int shape, int rotation, int texture,
		int southWestY, int southEastY, int northEastY, int northWestY,
		int southWestU, int southEastU, int northEastU, int northWestU,
		int southWestO, int southEastO, int northEastO, int northWestO,
		int underlayRgb, int overlayRgb
	) {
		for (int below = plane; below >= 0; below--)
			work.scene.tile(below, x, y);

		BuiltTile tile = work.scene.tile(plane, x, y);

		if (shape == 0) {
			tile.setSceneTilePaint(new BuiltTilePaint(
				southWestU, southEastU, northEastU, northWestU, -1, underlayRgb, false));
		} else if (shape == 1) {
			boolean flat = southWestY == southEastY && southWestY == northEastY && southWestY == northWestY;
			tile.setSceneTilePaint(new BuiltTilePaint(
				southWestO, southEastO, northEastO, northWestO, texture, overlayRgb, flat));
		} else {
			tile.setSceneTileModel(new BuiltTileModel(
				shape, rotation, texture, x - OFFSET, y - OFFSET,
				southWestY, southEastY, northEastY, northWestY,
				southWestU, southEastU, northEastU, northWestU,
				southWestO, southEastO, northEastO, northWestO,
				underlayRgb, Math.max(1, overlayRgb)
			));
		}
	}

	private void linkBridges(Work work) {
		for (int x = 0; x < EXTENDED; x++) {
			for (int y = 0; y < EXTENDED; y++) {
				if ((work.settings[1][x][y] & 2) != 2)
					continue;

				BuiltScene scene = work.scene;
				BuiltTile under = scene.tileOrNull(0, x, y);

				for (int plane = 0; plane < MAX_Z - 1; plane++) {
					BuiltTile above = scene.tileOrNull(plane + 1, x, y);
					scene.setTile(plane, x, y, above);
					if (above != null)
						above.setPlane(plane);
				}
				scene.setTile(MAX_Z - 1, x, y, null);

				if (under != null)
					under.setPlane(MAX_Z - 1);

				for (int plane = 0; plane < MAX_Z; plane++) {
					BuiltTile tile = scene.tileOrNull(plane, x, y);
					if (tile != null)
						tile.setOnBridge(true);
				}
				if (under != null)
					under.setOnBridge(true);

				scene.tile(0, x, y).setBridge(under);
			}
		}
	}

	public static int packHsl(int hue, int saturation, int lightness) {
		if (lightness > 179)
			saturation /= 2;
		if (lightness > 192)
			saturation /= 2;
		if (lightness > 217)
			saturation /= 2;
		if (lightness > 243)
			saturation /= 2;
		return (hue / 4 << 10) + (saturation / 32 << 7) + lightness / 2;
	}

	public static int shadeUnderlay(int hsl, int light) {
		if (hsl == -1)
			return NO_COLOR;
		return applyLight(hsl, light);
	}

	public static int shadeOverlay(int hsl, int light) {
		if (hsl == -2)
			return NO_COLOR;
		if (hsl == -1)
			return clampLight(light);
		return applyLight(hsl, light);
	}

	private static int applyLight(int hsl, int light) {
		return (hsl & 0xFF80) + clampLight((hsl & 0x7F) * light / 128);
	}

	private static int clampLight(int light) {
		if (light < 2)
			return 2;
		return Math.min(light, 126);
	}

	static final class Work {
		final BuiltScene scene;
		final int minTile, maxTile;

		final int[][][] heights;
		final byte[][][] settings;
		final short[][][] underlayIds;
		final short[][][] overlayIds;
		final byte[][][] shapes;

		final byte[][][] rotations = new byte[MAX_Z][EXTENDED][EXTENDED];
		final byte[][][] shadow = new byte[MAX_Z][HEIGHT_GRID][HEIGHT_GRID];
		final int[][] lightness = new int[HEIGHT_GRID][HEIGHT_GRID];
		final NormalMerger merger = new NormalMerger();
		final VariantOptions variants;
		/**
		 * Whether models are bent to follow the ground they stand on.
		 *
		 * <p>Off for regions laid side by side. Bending reaches across tile corners, and the corner where
		 * two regions meet belongs to both of them while the ground either side of it does not, so anything
		 * standing near the join gets stretched between two unrelated landscapes.
		 */
		boolean hillskew = true;
		/** Which region's locations are being placed, so their lighting does not reach into another's */
		int region;
		/** Whether the regions are unrelated places laid side by side rather than real neighbours */
		boolean isolateRegions;

		Work(BuiltScene scene, int minTile, int maxTile, VariantOptions variants) {
			this.scene = scene;
			this.minTile = minTile;
			this.maxTile = maxTile;
			this.variants = variants;
			heights = scene.getTileHeights();
			settings = scene.getExtendedTileSettings();
			underlayIds = scene.getUnderlayIds();
			overlayIds = scene.getOverlayIds();
			shapes = scene.getTileShapes();
		}
	}
}
