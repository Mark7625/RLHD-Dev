package rs117.hd.dev.sceneloader.scene;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import net.runelite.cache.definitions.FrameDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.models.CircularAngle;
import net.runelite.cache.models.FaceNormal;
import net.runelite.cache.models.VertexNormal;

public class ModelFactory {
	private static final int LIGHT_X = -50;
	private static final int LIGHT_Y = -10;
	private static final int LIGHT_Z = -50;
	private static final int AMBIENT_BASE = 64;
	private static final int CONTRAST_BASE = 768;

	static final int LOC_OBJECT = 10;
	private static final int DIAGONAL_ANGLE = 256;
	private static final int DIAGONAL_OFFSET = 45;
	private static final int TILE_SIZE = 128;

	private final Definitions defs;

	private final Map<Long, BuiltModel> litModels = new HashMap<>();

	public ModelFactory(Definitions defs) {
		this.defs = defs;
	}

	@Nullable
	public BuiltModel model(ObjectDefinition object, int type, int rotation) throws IOException {
		return model(object, type, rotation, null);
	}

	@Nullable
	public BuiltModel model(ObjectDefinition object, int type, int rotation, @Nullable FrameDefinition frame)
		throws IOException {
		long shape = object.getObjectTypes() == null
			? ((long) object.getId() << 10) + rotation
			: ((long) object.getId() << 10) + ((long) type << 3) + rotation;
		long key = shape << 33 | (frame == null ? 0 : (frame.id & 0xFFFFFFFFL) + 1);

		if (litModels.containsKey(key))
			return litModels.get(key);

		ModelDefinition built = build(object, type, rotation);
		BuiltModel lit = null;
		if (built != null) {
			lit = light(built, object.getAmbient() + AMBIENT_BASE, object.getContrast() + CONTRAST_BASE);
			if (frame != null)
				lit = pose(lit, built, frame, rotation);
		}
		litModels.put(key, lit);
		return lit;
	}

	/**
	 * Moves a model into one of its animation's frames.
	 *
	 * <p>The frame is applied with the model turned back to the way it was built, and turned round again
	 * afterwards. An animation's transforms are written in the model's own directions, so applying them to
	 * a model that has already been turned to face the way the map wants turns the animation with it: a
	 * wheel put down facing the other way then turns the other way, which is the one thing about it anyone
	 * would notice. The client does the same thing either side of the frame for the same reason.
	 *
	 * @param rotation the rotation the map asked for, which the model has already been turned by
	 */
	private BuiltModel pose(BuiltModel lit, ModelDefinition def, FrameDefinition frame, int rotation) {
		turnBack(def, rotation & 3);
		applyFrame(def, frame);
		turnForward(def, rotation & 3);

		return lit.withPose(
			toFloats(def.vertexX, def.vertexCount),
			toFloats(def.vertexY, def.vertexCount),
			toFloats(def.vertexZ, def.vertexCount),
			def.faceTransparencies
		);
	}

	private static final int FADE_FACES = 5;

	private static final int SET_PIVOT = 0;
	private static final int MOVE = 1;
	private static final int TURN = 2;
	private static final int SCALE = 3;

	private static final int[] SINE = new int[2048];
	private static final int[] COSINE = new int[2048];

	static {
		for (int i = 0; i < 2048; i++) {
			SINE[i] = (int) (Math.sin(i * 0.0030679615) * 65536);
			COSINE[i] = (int) (Math.cos(i * 0.0030679615) * 65536);
		}
	}

	/** Undoes the turn the map asked for, so an animation is applied the way it was written */
	private static void turnBack(ModelDefinition def, int rotation) {
		switch (rotation) {
			case 1:
				def.rotate3();
				break;
			case 2:
				def.rotate2();
				break;
			case 3:
				def.rotate1();
				break;
			default:
				break;
		}
	}

	/** Puts it back the way the map wanted it */
	private static void turnForward(ModelDefinition def, int rotation) {
		switch (rotation) {
			case 1:
				def.rotate1();
				break;
			case 2:
				def.rotate2();
				break;
			case 3:
				def.rotate3();
				break;
			default:
				break;
		}
	}

	/**
	 * Moves a model into a frame of an animation, if it is a model that can be animated at all.
	 *
	 * <p>Having groups of vertices to move is what makes a model animatable, and a model without them is
	 * left alone entirely, fades included. That is the game's own rule and not an optimisation standing in
	 * for one: {@code Model.method5839} does nothing at all unless the model has vertex groups, so a model
	 * carrying nothing but groups of faces to fade never fades. It reads like an oversight on their part and
	 * there is cache data written as though it fades, but what is on screen is what the game draws, and the
	 * game draws those faces at the colour they were built with. Fading them here instead takes away faces
	 * that are supposed to be there — for the altar ruins, the whole of the mist standing in the arch.
	 */
	public static void applyFrame(ModelDefinition def, FrameDefinition frame) {
		def.computeAnimationTables();
		int[][] vertexGroups = def.getVertexGroups();
		if (vertexGroups == null)
			return;

		int[][] faceGroups = null;
		int pivotX = 0, pivotY = 0, pivotZ = 0;

		var framemap = frame.framemap;
		for (int i = 0; i < frame.translatorCount; i++) {
			int index = frame.indexFrameIds[i];
			if (index < 0 || index >= framemap.types.length)
				continue;

			int type = framemap.types[index];
			int[] groups = framemap.frameMaps[index];
			int dx = frame.translator_x[i];
			int dy = frame.translator_y[i];
			int dz = frame.translator_z[i];

			if (type == FADE_FACES) {
				if (faceGroups == null)
					faceGroups = faceGroups(def);
				fadeFaces(def, faceGroups, groups, dx);
				continue;
			}

			switch (type) {
				case SET_PIVOT: {
					long sumX = 0, sumY = 0, sumZ = 0;
					int counted = 0;
					for (int group : groups) {
						if (group < 0 || group >= vertexGroups.length)
							continue;
						for (int vertex : vertexGroups[group]) {
							sumX += def.vertexX[vertex];
							sumY += def.vertexY[vertex];
							sumZ += def.vertexZ[vertex];
							counted++;
						}
					}
					if (counted > 0) {
						pivotX = (int) (sumX / counted) + dx;
						pivotY = (int) (sumY / counted) + dy;
						pivotZ = (int) (sumZ / counted) + dz;
					} else {
						pivotX = dx;
						pivotY = dy;
						pivotZ = dz;
					}
					break;
				}

				case MOVE:
					for (int group : groups) {
						if (group < 0 || group >= vertexGroups.length)
							continue;
						for (int vertex : vertexGroups[group]) {
							def.vertexX[vertex] += dx;
							def.vertexY[vertex] += dy;
							def.vertexZ[vertex] += dz;
						}
					}
					break;

				case TURN: {
					int aroundX = (dx & 0xFF) * 8;
					int aroundY = (dy & 0xFF) * 8;
					int aroundZ = (dz & 0xFF) * 8;
					for (int group : groups) {
						if (group < 0 || group >= vertexGroups.length)
							continue;
						for (int vertex : vertexGroups[group]) {
							int x = def.vertexX[vertex] - pivotX;
							int y = def.vertexY[vertex] - pivotY;
							int z = def.vertexZ[vertex] - pivotZ;

							if (aroundZ != 0) {
								int sin = SINE[aroundZ], cos = COSINE[aroundZ];
								int turned = x * cos + y * sin >> 16;
								y = y * cos - x * sin >> 16;
								x = turned;
							}
							if (aroundX != 0) {
								int sin = SINE[aroundX], cos = COSINE[aroundX];
								int turned = y * cos - z * sin >> 16;
								z = z * cos + y * sin >> 16;
								y = turned;
							}
							if (aroundY != 0) {
								int sin = SINE[aroundY], cos = COSINE[aroundY];
								int turned = z * sin + x * cos >> 16;
								z = z * cos - x * sin >> 16;
								x = turned;
							}

							def.vertexX[vertex] = x + pivotX;
							def.vertexY[vertex] = y + pivotY;
							def.vertexZ[vertex] = z + pivotZ;
						}
					}
					break;
				}

				case SCALE:
					for (int group : groups) {
						if (group < 0 || group >= vertexGroups.length)
							continue;
						for (int vertex : vertexGroups[group]) {
							def.vertexX[vertex] = (def.vertexX[vertex] - pivotX) * dx / 128 + pivotX;
							def.vertexY[vertex] = (def.vertexY[vertex] - pivotY) * dy / 128 + pivotY;
							def.vertexZ[vertex] = (def.vertexZ[vertex] - pivotZ) * dz / 128 + pivotZ;
						}
					}
					break;

				default:
					break;
			}
		}
	}

	@Nullable
	private static int[][] faceGroups(ModelDefinition def) {
		int[] packed = def.packedTransparencyVertexGroups;
		if (packed == null)
			return null;

		if (def.faceTransparencies == null) {
			def.faceTransparencies = new byte[def.faceCount];
		}

		int faces = Math.min(def.faceCount, packed.length);
		int highest = 0;
		int[] counts = new int[256];
		for (int face = 0; face < faces; face++) {
			int group = packed[face] & 0xFF;
			counts[group]++;
			if (group > highest)
				highest = group;
		}

		var groups = new int[highest + 1][];
		for (int group = 0; group <= highest; group++) {
			groups[group] = new int[counts[group]];
			counts[group] = 0;
		}
		for (int face = 0; face < faces; face++) {
			int group = packed[face] & 0xFF;
			groups[group][counts[group]++] = face;
		}
		return groups;
	}

	private static void fadeFaces(ModelDefinition def, @Nullable int[][] faceGroups, int[] frameMap, int by) {
		if (faceGroups == null)
			return;

		for (int group : frameMap) {
			if (group < 0 || group >= faceGroups.length)
				continue;
			for (int face : faceGroups[group]) {
				int alpha = (def.faceTransparencies[face] & 0xFF) + by * 8;
				def.faceTransparencies[face] = (byte) Math.max(0, Math.min(255, alpha));
			}
		}
	}

	@Nullable
	public ModelDefinition unlitModel(ObjectDefinition object, int type, int rotation) throws IOException {
		return build(object, type, rotation);
	}

	@Nullable
	private ModelDefinition build(ObjectDefinition object, int type, int rotation) throws IOException {
		int[] modelIds = object.getObjectModels();
		int[] types = object.getObjectTypes();

		ModelDefinition model;
		if (types == null) {
			if (type != LOC_OBJECT || modelIds == null || modelIds.length == 0)
				return null;

			boolean mirrored = object.isRotated();
			if (modelIds.length == 1) {
				model = defs.model(modelIds[0]);
				if (model == null)
					return null;
				if (mirrored)
					mirror(model);
			} else {
				var parts = new ModelDefinition[modelIds.length];
				for (int i = 0; i < modelIds.length; i++) {
					parts[i] = defs.model(modelIds[i]);
					if (parts[i] == null)
						return null;
					if (mirrored)
						mirror(parts[i]);
				}
				model = merge(parts);
			}
		} else {
			int index = -1;
			for (int i = 0; i < types.length; i++) {
				if (types[i] == type) {
					index = i;
					break;
				}
			}
			if (index == -1 || modelIds == null || index >= modelIds.length)
				return null;

			model = defs.model(modelIds[index]);
			if (model == null)
				return null;
			if (object.isRotated() ^ rotation > 3)
				mirror(model);
		}

		if (type == 4 && rotation > 3) {
			rotate(model, DIAGONAL_ANGLE);
			model.move(DIAGONAL_OFFSET, 0, -DIAGONAL_OFFSET);
		}

		switch (rotation & 3) {
			case 1:
				model.rotate1();
				break;
			case 2:
				model.rotate2();
				break;
			case 3:
				model.rotate3();
				break;
			default:
				break;
		}

		if (object.getFullRecolor() != -1) {
			for (int i = 0; i < model.faceCount; i++)
				model.faceColors[i] = object.getFullRecolor();
		} else if (object.getRecolorToFind() != null) {
			short[] find = object.getRecolorToFind();
			short[] replace = object.getRecolorToReplace();
			for (int i = 0; i < find.length; i++)
				model.recolor(find[i], replace[i]);
		}

		if (object.getRetextureToFind() != null) {
			short[] find = object.getRetextureToFind();
			short[] replace = object.getTextureToReplace();
			for (int i = 0; i < find.length; i++)
				model.retexture(find[i], replace[i]);
		}

		if (object.getModelSizeX() != TILE_SIZE || object.getModelSizeHeight() != TILE_SIZE || object.getModelSizeY() != TILE_SIZE)
			model.resize(object.getModelSizeX(), object.getModelSizeHeight(), object.getModelSizeY());

		int offsetX = signed(object.getOffsetX());
		int offsetHeight = signed(object.getOffsetHeight());
		int offsetY = signed(object.getOffsetY());
		if (offsetX != 0 || offsetHeight != 0 || offsetY != 0)
			model.move(offsetX, offsetHeight, offsetY);

		return model;
	}

	private static int signed(int unsignedShort) {
		return unsignedShort > Short.MAX_VALUE ? unsignedShort - 0x10000 : unsignedShort;
	}

	private static void mirror(ModelDefinition model) {
		for (int i = 0; i < model.vertexCount; i++)
			model.vertexZ[i] = -model.vertexZ[i];
		for (int i = 0; i < model.faceCount; i++) {
			int swap = model.faceIndices1[i];
			model.faceIndices1[i] = model.faceIndices3[i];
			model.faceIndices3[i] = swap;
		}
		model.vertexNormals = null;
		model.faceNormals = null;
	}

	private static void rotate(ModelDefinition model, int angle) {
		int sin = CircularAngle.SINE[angle];
		int cos = CircularAngle.COSINE[angle];
		for (int i = 0; i < model.vertexCount; i++) {
			int x = model.vertexX[i];
			int z = model.vertexZ[i];
			model.vertexX[i] = x * cos + z * sin >> 16;
			model.vertexZ[i] = z * cos - x * sin >> 16;
		}
		model.vertexNormals = null;
		model.faceNormals = null;
	}

	private static ModelDefinition merge(ModelDefinition[] parts) {
		var merged = new ModelDefinition();

		int vertexTotal = 0, faceTotal = 0, textureFaceTotal = 0;
		boolean anyRenderTypes = false, anyPriorities = false, anyTransparencies = false;
		boolean anyFaceGroups = false, anyTextures = false, anyTextureCoords = false, anyZOffsets = false;
		int sharedPriority = -1;

		for (ModelDefinition part : parts) {
			if (part == null)
				continue;
			vertexTotal += part.vertexCount;
			faceTotal += part.faceCount;
			textureFaceTotal += part.numTextureFaces;

			if (part.faceRenderPriorities != null) {
				anyPriorities = true;
			} else {
				if (sharedPriority == -1)
					sharedPriority = part.priority;
				if (sharedPriority != part.priority)
					anyPriorities = true;
			}

			anyRenderTypes |= part.faceRenderTypes != null;
			anyTransparencies |= part.faceTransparencies != null;
			anyFaceGroups |= part.packedTransparencyVertexGroups != null;
			anyTextures |= part.faceTextures != null;
			anyTextureCoords |= part.textureCoords != null;
			anyZOffsets |= part.faceZOffsets != null;
		}

		merged.vertexX = new int[vertexTotal];
		merged.vertexY = new int[vertexTotal];
		merged.vertexZ = new int[vertexTotal];
		merged.packedVertexGroups = new int[vertexTotal];
		merged.faceIndices1 = new int[faceTotal];
		merged.faceIndices2 = new int[faceTotal];
		merged.faceIndices3 = new int[faceTotal];
		merged.faceColors = new short[faceTotal];
		merged.priority = (byte) (sharedPriority == -1 ? 0 : sharedPriority);

		if (anyRenderTypes)
			merged.faceRenderTypes = new byte[faceTotal];
		if (anyPriorities)
			merged.faceRenderPriorities = new byte[faceTotal];
		if (anyTransparencies)
			merged.faceTransparencies = new byte[faceTotal];
		if (anyFaceGroups)
			merged.packedTransparencyVertexGroups = new int[faceTotal];
		if (anyTextures)
			merged.faceTextures = new short[faceTotal];
		if (anyTextureCoords)
			merged.textureCoords = new byte[faceTotal];
		if (anyZOffsets)
			merged.faceZOffsets = new byte[faceTotal];

		if (textureFaceTotal > 0) {
			merged.textureRenderTypes = new byte[textureFaceTotal];
			merged.texIndices1 = new short[textureFaceTotal];
			merged.texIndices2 = new short[textureFaceTotal];
			merged.texIndices3 = new short[textureFaceTotal];
		}

		merged.vertexCount = 0;
		merged.faceCount = 0;
		merged.numTextureFaces = 0;

		for (ModelDefinition part : parts) {
			if (part == null)
				continue;

			int[] partGroups = groupOfVertex(part);

			for (int face = 0; face < part.faceCount; face++) {
				int at = merged.faceCount;

				if (anyRenderTypes && part.faceRenderTypes != null)
					merged.faceRenderTypes[at] = part.faceRenderTypes[face];
				if (anyPriorities)
					merged.faceRenderPriorities[at] = part.faceRenderPriorities != null
						? part.faceRenderPriorities[face]
						: part.priority;
				if (anyTransparencies && part.faceTransparencies != null)
					merged.faceTransparencies[at] = part.faceTransparencies[face];
				if (anyFaceGroups && part.packedTransparencyVertexGroups != null)
					merged.packedTransparencyVertexGroups[at] = part.packedTransparencyVertexGroups[face];
				if (anyTextures)
					merged.faceTextures[at] = part.faceTextures != null ? part.faceTextures[face] : -1;
				if (anyTextureCoords)
					merged.textureCoords[at] = part.textureCoords != null && part.textureCoords[face] != -1
						? (byte) (part.textureCoords[face] + merged.numTextureFaces)
						: -1;
				if (anyZOffsets && part.faceZOffsets != null)
					merged.faceZOffsets[at] = part.faceZOffsets[face];

				merged.faceColors[at] = part.faceColors[face];
				merged.faceIndices1[at] = shareVertex(merged, part, partGroups, part.faceIndices1[face]);
				merged.faceIndices2[at] = shareVertex(merged, part, partGroups, part.faceIndices2[face]);
				merged.faceIndices3[at] = shareVertex(merged, part, partGroups, part.faceIndices3[face]);
				merged.faceCount++;
			}

			for (int triangle = 0; triangle < part.numTextureFaces; triangle++) {
				int at = merged.numTextureFaces;
				byte renderType = merged.textureRenderTypes[at] = part.textureRenderTypes[triangle];
				if (renderType == 0) {
					merged.texIndices1[at] = (short) shareVertex(merged, part, partGroups, part.texIndices1[triangle]);
					merged.texIndices2[at] = (short) shareVertex(merged, part, partGroups, part.texIndices2[triangle]);
					merged.texIndices3[at] = (short) shareVertex(merged, part, partGroups, part.texIndices3[triangle]);
				}
				merged.numTextureFaces++;
			}
		}

		return merged;
	}

	private static int shareVertex(
		ModelDefinition merged, ModelDefinition part, @Nullable int[] partGroups, int vertex
	) {
		int x = part.vertexX[vertex];
		int y = part.vertexY[vertex];
		int z = part.vertexZ[vertex];

		for (int i = 0; i < merged.vertexCount; i++)
			if (merged.vertexX[i] == x && merged.vertexY[i] == y && merged.vertexZ[i] == z)
				return i;

		merged.vertexX[merged.vertexCount] = x;
		merged.vertexY[merged.vertexCount] = y;
		merged.vertexZ[merged.vertexCount] = z;
		if (partGroups != null && vertex < partGroups.length)
			merged.packedVertexGroups[merged.vertexCount] = partGroups[vertex];
		return merged.vertexCount++;
	}

	@Nullable
	private static int[] groupOfVertex(ModelDefinition part) {
		if (part.packedVertexGroups != null)
			return part.packedVertexGroups;

		int[][] groups = part.getVertexGroups();
		if (groups == null)
			return null;

		var byVertex = new int[part.vertexCount];
		for (int group = 0; group < groups.length; group++) {
			if (groups[group] == null)
				continue;
			for (int vertex : groups[group])
				if (vertex >= 0 && vertex < byVertex.length)
					byVertex[vertex] = group;
		}
		return byVertex;
	}

	public static BuiltModel light(ModelDefinition def, int ambient, int contrast) {
		return light(def, ambient, contrast, null);
	}

	public static BuiltModel light(ModelDefinition def, int ambient, int contrast, @Nullable VertexNormal[] mergedNormals) {
		def.computeNormals();

		int lightLength = (int) Math.sqrt((double) LIGHT_Z * LIGHT_Z + (double) LIGHT_X * LIGHT_X + (double) LIGHT_Y * LIGHT_Y);
		int scaledContrast = lightLength * contrast >> 8;

		var model = new BuiltModel();
		model.faceColors1 = new int[def.faceCount];
		model.faceColors2 = new int[def.faceCount];
		model.faceColors3 = new int[def.faceCount];

		if (def.numTextureFaces > 0 && def.textureCoords != null)
			buildTextureTriangles(def, model);

		for (int face = 0; face < def.faceCount; face++) {
			int renderType = def.faceRenderTypes == null ? 0 : def.faceRenderTypes[face];
			int alpha = def.faceTransparencies == null ? 0 : def.faceTransparencies[face];
			int texture = def.faceTextures == null ? -1 : def.faceTextures[face];

			if (alpha == -2)
				renderType = 3;
			if (alpha == -1)
				renderType = 2;

			if (texture == -1) {
				if (renderType == 0) {
					int color = def.faceColors[face] & 0xFFFF;
					model.faceColors1[face] = shade(color, vertexLight(def, mergedNormals, def.faceIndices1[face], scaledContrast, ambient));
					model.faceColors2[face] = shade(color, vertexLight(def, mergedNormals, def.faceIndices2[face], scaledContrast, ambient));
					model.faceColors3[face] = shade(color, vertexLight(def, mergedNormals, def.faceIndices3[face], scaledContrast, ambient));
				} else if (renderType == 1) {
					model.faceColors1[face] = shade(def.faceColors[face] & 0xFFFF, faceLight(def, face, scaledContrast, ambient));
					model.faceColors3[face] = -1;
				} else if (renderType == 3) {
					model.faceColors1[face] = 128;
					model.faceColors3[face] = -1;
				} else {
					model.faceColors3[face] = -2;
				}
			} else if (renderType == 0) {
				model.faceColors1[face] = clamp(vertexLight(def, mergedNormals, def.faceIndices1[face], scaledContrast, ambient));
				model.faceColors2[face] = clamp(vertexLight(def, mergedNormals, def.faceIndices2[face], scaledContrast, ambient));
				model.faceColors3[face] = clamp(vertexLight(def, mergedNormals, def.faceIndices3[face], scaledContrast, ambient));
			} else if (renderType == 1) {
				model.faceColors1[face] = clamp(faceLight(def, face, scaledContrast, ambient));
				model.faceColors3[face] = -1;
			} else {
				model.faceColors3[face] = -2;
			}
		}

		model.verticesCount = def.vertexCount;
		model.verticesX = toFloats(def.vertexX, def.vertexCount);
		model.verticesY = toFloats(def.vertexY, def.vertexCount);
		model.verticesZ = toFloats(def.vertexZ, def.vertexCount);

		model.faceCount = def.faceCount;
		model.faceIndices1 = def.faceIndices1;
		model.faceIndices2 = def.faceIndices2;
		model.faceIndices3 = def.faceIndices3;

		model.faceRenderPriorities = def.faceRenderPriorities;
		model.faceTransparencies = def.faceTransparencies;
		model.faceTextures = def.faceTextures;
		model.faceBias = def.faceZOffsets;
		model.unlitFaceColors = def.faceColors;

		storeVertexNormals(def, mergedNormals, model);

		return model;
	}

	private static void buildTextureTriangles(ModelDefinition def, BuiltModel model) {
		int[] remapped = new int[def.numTextureFaces];

		for (int face = 0; face < def.faceCount; face++)
			if (def.textureCoords[face] != -1)
				remapped[def.textureCoords[face] & 0xFF]++;

		int kept = 0;
		for (int i = 0; i < def.numTextureFaces; i++)
			if (remapped[i] > 0 && def.textureRenderTypes[i] == 0)
				kept++;

		model.texIndices1 = new int[kept];
		model.texIndices2 = new int[kept];
		model.texIndices3 = new int[kept];

		int at = 0;
		for (int i = 0; i < def.numTextureFaces; i++) {
			if (remapped[i] > 0 && def.textureRenderTypes[i] == 0) {
				model.texIndices1[at] = def.texIndices1[i] & 0xFFFF;
				model.texIndices2[at] = def.texIndices2[i] & 0xFFFF;
				model.texIndices3[at] = def.texIndices3[i] & 0xFFFF;
				remapped[i] = at++;
			} else {
				remapped[i] = -1;
			}
		}

		model.textureFaces = new byte[def.faceCount];
		for (int face = 0; face < def.faceCount; face++)
			model.textureFaces[face] = def.textureCoords[face] != -1
				? (byte) remapped[def.textureCoords[face] & 0xFF]
				: -1;
	}

	private static void storeVertexNormals(ModelDefinition def, @Nullable VertexNormal[] mergedNormals, BuiltModel model) {
		if (def.vertexNormals == null)
			return;

		model.vertexNormalsX = new int[def.vertexCount];
		model.vertexNormalsY = new int[def.vertexCount];
		model.vertexNormalsZ = new int[def.vertexCount];
		for (int i = 0; i < def.vertexCount; i++) {
			VertexNormal normal = normalAt(def, mergedNormals, i);
			if (normal == null)
				continue;
			model.vertexNormalsX[i] = normal.x;
			model.vertexNormalsY[i] = normal.y;
			model.vertexNormalsZ[i] = normal.z;
		}
	}

	@Nullable
	static VertexNormal normalAt(ModelDefinition def, @Nullable VertexNormal[] mergedNormals, int vertex) {
		if (mergedNormals != null && mergedNormals[vertex] != null)
			return mergedNormals[vertex];
		return def.vertexNormals[vertex];
	}

	private static int vertexLight(ModelDefinition def, @Nullable VertexNormal[] mergedNormals, int vertex, int scaledContrast, int ambient) {
		VertexNormal normal = normalAt(def, mergedNormals, vertex);
		int divisor = scaledContrast * normal.magnitude;
		if (divisor == 0)
			return ambient;
		return (LIGHT_Y * normal.y + LIGHT_Z * normal.z + LIGHT_X * normal.x) / divisor + ambient;
	}

	private static int faceLight(ModelDefinition def, int face, int scaledContrast, int ambient) {
		FaceNormal normal = def.faceNormals[face];
		int divisor = scaledContrast / 2 + scaledContrast;
		if (divisor == 0)
			return ambient;
		return (LIGHT_Y * normal.y + LIGHT_Z * normal.z + LIGHT_X * normal.x) / divisor + ambient;
	}

	private static int shade(int color, int light) {
		return (color & 0xFF80) + clamp((color & 0x7F) * light >> 7);
	}

	private static int clamp(int light) {
		if (light < 2)
			return 2;
		return Math.min(light, 126);
	}

	private static float[] toFloats(int[] values, int count) {
		float[] floats = new float[count];
		for (int i = 0; i < count; i++)
			floats[i] = values[i];
		return floats;
	}

	public static void contourDefinition(
		ModelDefinition def, int[][] heights, int localX, int baseHeight, int localY, int contouredGround
	) {
		ModelBounds bounds = ModelBounds.of(def);

		int minX = bounds.minX + localX;
		int maxX = bounds.maxX + localX;
		int minZ = bounds.minZ + localY;
		int maxZ = bounds.maxZ + localY;

		if (minX < 0 || (maxX + TILE_SIZE >> 7) >= heights.length || minZ < 0 || (maxZ + TILE_SIZE >> 7) >= heights[0].length)
			return;

		int westTile = minX >> 7;
		int eastTile = maxX + TILE_SIZE - 1 >> 7;
		int southTile = minZ >> 7;
		int northTile = maxZ + TILE_SIZE - 1 >> 7;

		if (heights[westTile][southTile] == baseHeight
			&& heights[eastTile][southTile] == baseHeight
			&& heights[westTile][northTile] == baseHeight
			&& heights[eastTile][northTile] == baseHeight)
			return;

		for (int i = 0; i < def.vertexCount; i++) {
			int vertexY = def.vertexY[i];
			int ground = groundAt(def.vertexX[i] + localX, def.vertexZ[i] + localY, heights);

			if (contouredGround > 0) {
				int up = (-vertexY << 16) / Math.max(1, bounds.modelHeight);
				if (up >= contouredGround)
					continue;
				def.vertexY[i] = (contouredGround - up) * (ground - baseHeight) / contouredGround + vertexY;
			} else {
				def.vertexY[i] = vertexY + ground - baseHeight;
			}
		}

		def.vertexNormals = null;
		def.faceNormals = null;
	}

	static int groundAt(int x, int z, int[][] heights) {
		int fracX = x & TILE_SIZE - 1;
		int fracZ = z & TILE_SIZE - 1;
		int tileX = x >> 7;
		int tileZ = z >> 7;

		int south = (TILE_SIZE - fracX) * heights[tileX][tileZ] + heights[tileX + 1][tileZ] * fracX >> 7;
		int north = (TILE_SIZE - fracX) * heights[tileX][tileZ + 1] + heights[tileX + 1][tileZ + 1] * fracX >> 7;
		return (TILE_SIZE - fracZ) * south + fracZ * north >> 7;
	}

	public static BuiltModel contour(
		BuiltModel model, int[][] heights, int localX, int baseHeight, int localY, int contouredGround
	) {
		model.calculateBoundsCylinder();
		int reach = model.xyzMag;

		int minX = localX - reach;
		int maxX = localX + reach;
		int minY = localY - reach;
		int maxY = localY + reach;

		if (minX < 0 || (maxX + TILE_SIZE >> 7) >= heights.length || minY < 0 || (maxY + TILE_SIZE >> 7) >= heights[0].length)
			return model;

		int westTile = minX >> 7;
		int eastTile = maxX + TILE_SIZE - 1 >> 7;
		int southTile = minY >> 7;
		int northTile = maxY + TILE_SIZE - 1 >> 7;

		if (heights[westTile][southTile] == baseHeight
			&& heights[eastTile][southTile] == baseHeight
			&& heights[westTile][northTile] == baseHeight
			&& heights[eastTile][northTile] == baseHeight)
			return model;

		BuiltModel bent = model.copyForContour();

		for (int i = 0; i < model.verticesCount; i++) {
			int vertexY = (int) model.verticesY[i];

			if (contouredGround > 0) {
				int up = (-vertexY << 16) / Math.max(1, model.modelHeight);
				if (up >= contouredGround) {
					bent.verticesY[i] = model.verticesY[i];
					continue;
				}
				int ground = groundAt(model, heights, i, localX, localY);
				bent.verticesY[i] = (contouredGround - up) * (ground - baseHeight) / contouredGround + vertexY;
			} else {
				int ground = groundAt(model, heights, i, localX, localY);
				bent.verticesY[i] = vertexY + ground - baseHeight;
			}
		}

		return bent;
	}

	private static int groundAt(BuiltModel model, int[][] heights, int vertex, int localX, int localY) {
		return groundAt((int) model.verticesX[vertex] + localX, (int) model.verticesZ[vertex] + localY, heights);
	}
}
