package rs117.hd.dev.sceneloader.scene;

import net.runelite.api.SceneTileModel;

public class BuiltTileModel implements SceneTileModel {
	private static final int[][] SHAPE_VERTICES = {
		{ 1, 3, 5, 7 },
		{ 1, 3, 5, 7 },
		{ 1, 3, 5, 7 },
		{ 1, 3, 5, 7, 6 },
		{ 1, 3, 5, 7, 6 },
		{ 1, 3, 5, 7, 6 },
		{ 1, 3, 5, 7, 6 },
		{ 1, 3, 5, 7, 2, 6 },
		{ 1, 3, 5, 7, 2, 8 },
		{ 1, 3, 5, 7, 2, 8 },
		{ 1, 3, 5, 7, 11, 12 },
		{ 1, 3, 5, 7, 11, 12 },
		{ 1, 3, 5, 7, 13, 14 }
	};

	private static final int[][] SHAPE_FACES = {
		{ 0, 1, 2, 3, 0, 0, 1, 3 },
		{ 1, 1, 2, 3, 1, 0, 1, 3 },
		{ 0, 1, 2, 3, 1, 0, 1, 3 },
		{ 0, 0, 1, 2, 0, 0, 2, 4, 1, 0, 4, 3 },
		{ 0, 0, 1, 4, 0, 0, 4, 3, 1, 1, 2, 4 },
		{ 0, 0, 4, 3, 1, 0, 1, 2, 1, 0, 2, 4 },
		{ 0, 1, 2, 4, 1, 0, 1, 4, 1, 0, 4, 3 },
		{ 0, 4, 1, 2, 0, 4, 2, 5, 1, 0, 4, 5, 1, 0, 5, 3 },
		{ 0, 4, 1, 2, 0, 4, 2, 3, 0, 4, 3, 5, 1, 0, 4, 5 },
		{ 0, 0, 4, 5, 1, 4, 1, 2, 1, 4, 2, 3, 1, 4, 3, 5 },
		{ 0, 0, 1, 5, 0, 1, 4, 5, 0, 1, 2, 4, 1, 0, 5, 3, 1, 5, 4, 3, 1, 4, 2, 3 },
		{ 1, 0, 1, 5, 1, 1, 4, 5, 1, 1, 2, 4, 0, 0, 5, 3, 0, 5, 4, 3, 0, 4, 2, 3 },
		{ 1, 0, 5, 4, 1, 0, 1, 5, 0, 0, 4, 3, 0, 4, 5, 3, 0, 5, 2, 3, 0, 1, 2, 5 }
	};

	private static final int UNIT = 128;
	private static final int HALF = UNIT / 2;
	private static final int QUARTER = UNIT / 4;
	private static final int THREE_QUARTER = UNIT * 3 / 4;

	private final int shape;
	private final int rotation;
	private final int modelUnderlay;
	private final int modelOverlay;
	private final boolean flat;

	private final int[] vertexX;
	private final int[] vertexY;
	private final int[] vertexZ;

	private final int[] faceA;
	private final int[] faceB;
	private final int[] faceC;

	private final int[] triangleColorA;
	private final int[] triangleColorB;
	private final int[] triangleColorC;
	private final int[] triangleTextureId;

	private int bufferOffset, uvBufferOffset, bufferLen;

	public BuiltTileModel(
		int shape, int rotation, int texture, int tileX, int tileZ,
		int swY, int seY, int neY, int nwY,
		int swU, int seU, int neU, int nwU,
		int swO, int seO, int neO, int nwO,
		int modelUnderlay, int modelOverlay
	) {
		this.shape = shape;
		this.rotation = rotation;
		this.modelUnderlay = modelUnderlay;
		this.modelOverlay = modelOverlay;
		this.flat = swY == seY && swY == neY && swY == nwY;

		int[] vertices = SHAPE_VERTICES[shape];
		int vertexCount = vertices.length;
		vertexX = new int[vertexCount];
		vertexY = new int[vertexCount];
		vertexZ = new int[vertexCount];

		int[] underlayColors = new int[vertexCount];
		int[] overlayColors = new int[vertexCount];

		int baseX = tileX * UNIT;
		int baseZ = tileZ * UNIT;

		for (int i = 0; i < vertexCount; i++) {
			int vertex = vertices[i];
			if ((vertex & 1) == 0 && vertex <= 8)
				vertex = (vertex - rotation - rotation - 1 & 7) + 1;
			if (vertex > 8 && vertex <= 12)
				vertex = (vertex - 9 - rotation & 3) + 9;
			if (vertex > 12 && vertex <= 16)
				vertex = (vertex - 13 - rotation & 3) + 13;

			int x, z, y, underlay, overlay;
			switch (vertex) {
				case 1:
					x = baseX;
					z = baseZ;
					y = swY;
					underlay = swU;
					overlay = swO;
					break;
				case 2:
					x = baseX + HALF;
					z = baseZ;
					y = swY + seY >> 1;
					underlay = swU + seU >> 1;
					overlay = swO + seO >> 1;
					break;
				case 3:
					x = baseX + UNIT;
					z = baseZ;
					y = seY;
					underlay = seU;
					overlay = seO;
					break;
				case 4:
					x = baseX + UNIT;
					z = baseZ + HALF;
					y = seY + neY >> 1;
					underlay = seU + neU >> 1;
					overlay = seO + neO >> 1;
					break;
				case 5:
					x = baseX + UNIT;
					z = baseZ + UNIT;
					y = neY;
					underlay = neU;
					overlay = neO;
					break;
				case 6:
					x = baseX + HALF;
					z = baseZ + UNIT;
					y = neY + nwY >> 1;
					underlay = neU + nwU >> 1;
					overlay = neO + nwO >> 1;
					break;
				case 7:
					x = baseX;
					z = baseZ + UNIT;
					y = nwY;
					underlay = nwU;
					overlay = nwO;
					break;
				case 8:
					x = baseX;
					z = baseZ + HALF;
					y = swY + nwY >> 1;
					underlay = swU + nwU >> 1;
					overlay = swO + nwO >> 1;
					break;
				case 9:
					x = baseX + HALF;
					z = baseZ + QUARTER;
					y = swY + seY >> 1;
					underlay = swU + seU >> 1;
					overlay = swO + seO >> 1;
					break;
				case 10:
					x = baseX + THREE_QUARTER;
					z = baseZ + HALF;
					y = seY + neY >> 1;
					underlay = seU + neU >> 1;
					overlay = seO + neO >> 1;
					break;
				case 11:
					x = baseX + HALF;
					z = baseZ + THREE_QUARTER;
					y = neY + nwY >> 1;
					underlay = neU + nwU >> 1;
					overlay = neO + nwO >> 1;
					break;
				case 12:
					x = baseX + QUARTER;
					z = baseZ + HALF;
					y = swY + nwY >> 1;
					underlay = swU + nwU >> 1;
					overlay = swO + nwO >> 1;
					break;
				case 13:
					x = baseX + QUARTER;
					z = baseZ + QUARTER;
					y = swY;
					underlay = swU;
					overlay = swO;
					break;
				case 14:
					x = baseX + THREE_QUARTER;
					z = baseZ + QUARTER;
					y = seY;
					underlay = seU;
					overlay = seO;
					break;
				case 15:
					x = baseX + THREE_QUARTER;
					z = baseZ + THREE_QUARTER;
					y = neY;
					underlay = neU;
					overlay = neO;
					break;
				default:
					x = baseX + QUARTER;
					z = baseZ + THREE_QUARTER;
					y = nwY;
					underlay = nwU;
					overlay = nwO;
					break;
			}

			vertexX[i] = x;
			vertexY[i] = y;
			vertexZ[i] = z;
			underlayColors[i] = underlay;
			overlayColors[i] = overlay;
		}

		int[] faces = SHAPE_FACES[shape];
		int faceCount = faces.length / 4;
		faceA = new int[faceCount];
		faceB = new int[faceCount];
		faceC = new int[faceCount];
		triangleColorA = new int[faceCount];
		triangleColorB = new int[faceCount];
		triangleColorC = new int[faceCount];
		triangleTextureId = texture != -1 ? new int[faceCount] : null;

		int f = 0;
		for (int i = 0; i < faceCount; i++) {
			int isOverlay = faces[f];
			int a = faces[f + 1];
			int b = faces[f + 2];
			int c = faces[f + 3];
			f += 4;

			if (a < 4)
				a = a - rotation & 3;
			if (b < 4)
				b = b - rotation & 3;
			if (c < 4)
				c = c - rotation & 3;

			faceA[i] = a;
			faceB[i] = b;
			faceC[i] = c;

			if (isOverlay == 0) {
				triangleColorA[i] = underlayColors[a];
				triangleColorB[i] = underlayColors[b];
				triangleColorC[i] = underlayColors[c];
				if (triangleTextureId != null)
					triangleTextureId[i] = -1;
			} else {
				triangleColorA[i] = overlayColors[a];
				triangleColorB[i] = overlayColors[b];
				triangleColorC[i] = overlayColors[c];
				if (triangleTextureId != null)
					triangleTextureId[i] = texture;
			}
		}
	}

	@Override
	public int getModelUnderlay() {
		return modelUnderlay;
	}

	@Override
	public int getModelOverlay() {
		return modelOverlay;
	}

	@Override
	public int getShape() {
		return shape;
	}

	@Override
	public int getRotation() {
		return rotation;
	}

	@Override
	public int[] getFaceX() {
		return faceA;
	}

	@Override
	public int[] getFaceY() {
		return faceB;
	}

	@Override
	public int[] getFaceZ() {
		return faceC;
	}

	@Override
	public int[] getVertexX() {
		return vertexX;
	}

	@Override
	public int[] getVertexY() {
		return vertexY;
	}

	@Override
	public int[] getVertexZ() {
		return vertexZ;
	}

	@Override
	public int[] getTriangleColorA() {
		return triangleColorA;
	}

	@Override
	public int[] getTriangleColorB() {
		return triangleColorB;
	}

	@Override
	public int[] getTriangleColorC() {
		return triangleColorC;
	}

	@Override
	public int[] getTriangleTextureId() {
		return triangleTextureId;
	}

	@Override
	public boolean isFlat() {
		return flat;
	}

	@Override
	public int getBufferOffset() {
		return bufferOffset;
	}

	@Override
	public void setBufferOffset(int bufferOffset) {
		this.bufferOffset = bufferOffset;
	}

	@Override
	public int getUvBufferOffset() {
		return uvBufferOffset;
	}

	@Override
	public void setUvBufferOffset(int bufferOffset) {
		uvBufferOffset = bufferOffset;
	}

	@Override
	public int getBufferLen() {
		return bufferLen;
	}

	@Override
	public void setBufferLen(int bufferLen) {
		this.bufferLen = bufferLen;
	}
}
