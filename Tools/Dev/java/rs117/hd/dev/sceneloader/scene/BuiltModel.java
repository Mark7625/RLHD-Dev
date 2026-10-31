package rs117.hd.dev.sceneloader.scene;

import javax.annotation.Nonnull;
import net.runelite.api.AABB;
import net.runelite.api.Model;
import net.runelite.api.Node;

public class BuiltModel implements Model {
	int verticesCount;
	float[] verticesX, verticesY, verticesZ;

	int faceCount;
	int[] faceIndices1, faceIndices2, faceIndices3;

	int[] faceColors1, faceColors2, faceColors3;
	short[] unlitFaceColors;

	byte[] faceTransparencies;
	byte transparency;
	short[] faceTextures;
	byte[] textureFaces;
	int[] texIndices1, texIndices2, texIndices3;

	int[] vertexNormalsX, vertexNormalsY, vertexNormalsZ;

	byte[] faceRenderPriorities;
	byte[] faceBias;

	int modelHeight, bottomY, xyzMag, radius, diameter;
	private boolean boundsCalculated;

	private int sceneId, bufferOffset, uvBufferOffset;

	BuiltModel copyForContour() {
		var copy = new BuiltModel();

		copy.verticesCount = verticesCount;
		copy.verticesX = verticesX;
		copy.verticesY = new float[verticesCount];
		copy.verticesZ = verticesZ;

		copy.faceCount = faceCount;
		copy.faceIndices1 = faceIndices1;
		copy.faceIndices2 = faceIndices2;
		copy.faceIndices3 = faceIndices3;

		copy.faceColors1 = faceColors1;
		copy.faceColors2 = faceColors2;
		copy.faceColors3 = faceColors3;
		copy.unlitFaceColors = unlitFaceColors;

		copy.faceTransparencies = faceTransparencies;
		copy.transparency = transparency;
		copy.faceTextures = faceTextures;
		copy.textureFaces = textureFaces;
		copy.texIndices1 = texIndices1;
		copy.texIndices2 = texIndices2;
		copy.texIndices3 = texIndices3;

		copy.vertexNormalsX = vertexNormalsX;
		copy.vertexNormalsY = vertexNormalsY;
		copy.vertexNormalsZ = vertexNormalsZ;

		copy.faceRenderPriorities = faceRenderPriorities;
		copy.faceBias = faceBias;

		return copy;
	}

	BuiltModel withVertices(float[] x, float[] y, float[] z) {
		var copy = copyForContour();
		copy.verticesX = x;
		copy.verticesY = y;
		copy.verticesZ = z;
		return copy;
	}

	BuiltModel withPose(float[] x, float[] y, float[] z, byte[] faceTransparencies) {
		var copy = withVertices(x, y, z);
		copy.faceTransparencies = faceTransparencies;
		return copy;
	}

	@Override
	public int getVerticesCount() {
		return verticesCount;
	}

	@Override
	public float[] getVerticesX() {
		return verticesX;
	}

	@Override
	public float[] getVerticesY() {
		return verticesY;
	}

	@Override
	public float[] getVerticesZ() {
		return verticesZ;
	}

	@Override
	public int getFaceCount() {
		return faceCount;
	}

	@Override
	public int[] getFaceIndices1() {
		return faceIndices1;
	}

	@Override
	public int[] getFaceIndices2() {
		return faceIndices2;
	}

	@Override
	public int[] getFaceIndices3() {
		return faceIndices3;
	}

	@Override
	public byte[] getFaceTransparencies() {
		return faceTransparencies;
	}

	@Override
	public short[] getFaceTextures() {
		return faceTextures;
	}

	@Override
	public int[] getFaceColors1() {
		return faceColors1;
	}

	@Override
	public int[] getFaceColors2() {
		return faceColors2;
	}

	@Override
	public int[] getFaceColors3() {
		return faceColors3;
	}

	@Override
	public short[] getUnlitFaceColors() {
		return unlitFaceColors;
	}

	@Override
	public int getSceneId() {
		return sceneId;
	}

	@Override
	public void setSceneId(int sceneId) {
		this.sceneId = sceneId;
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
	public int getBottomY() {
		calculateBoundsCylinder();
		return bottomY;
	}

	@Override
	public void calculateBoundsCylinder() {
		if (boundsCalculated)
			return;
		boundsCalculated = true;

		modelHeight = 0;
		bottomY = 0;
		int magnitude = 0;

		for (int i = 0; i < verticesCount; i++) {
			int x = (int) verticesX[i];
			int y = (int) verticesY[i];
			int z = (int) verticesZ[i];

			if (-y > modelHeight)
				modelHeight = -y;
			if (y > bottomY)
				bottomY = y;

			int squared = x * x + z * z;
			if (squared > magnitude)
				magnitude = squared;
		}

		xyzMag = (int) (Math.sqrt(magnitude) + 0.99);
		radius = (int) (Math.sqrt((double) xyzMag * xyzMag + (double) modelHeight * modelHeight) + 0.99);
		diameter = radius + (int) (Math.sqrt((double) xyzMag * xyzMag + (double) bottomY * bottomY) + 0.99);
	}

	@Override
	public byte getTransparency() {
		return transparency;
	}

	@Override
	public byte[] getFaceRenderPriorities() {
		return faceRenderPriorities;
	}

	@Override
	public byte[] getFaceBias() {
		return faceBias;
	}

	@Override
	public int getRadius() {
		calculateBoundsCylinder();
		return radius;
	}

	@Override
	public int getDiameter() {
		calculateBoundsCylinder();
		return diameter;
	}

	@Override
	public int getXYZMag() {
		calculateBoundsCylinder();
		return xyzMag;
	}

	@Override
	@Deprecated
	public void calculateExtreme(int orientation) {}

	@Nonnull
	@Override
	public AABB getAABB(int orientation) {
		calculateBoundsCylinder();
		return new ModelAABB(xyzMag, modelHeight, bottomY);
	}

	@Override
	public boolean useBoundingBox() {
		return false;
	}

	@Override
	public int[] getVertexNormalsX() {
		return vertexNormalsX;
	}

	@Override
	public int[] getVertexNormalsY() {
		return vertexNormalsY;
	}

	@Override
	public int[] getVertexNormalsZ() {
		return vertexNormalsZ;
	}

	@Override
	public byte getOverrideAmount() {
		return 0;
	}

	@Override
	public byte getOverrideHue() {
		return 0;
	}

	@Override
	public byte getOverrideSaturation() {
		return 0;
	}

	@Override
	public byte getOverrideLuminance() {
		return 0;
	}

	@Override
	public byte[] getTextureFaces() {
		return textureFaces;
	}

	@Override
	public int[] getTexIndices1() {
		return texIndices1;
	}

	@Override
	public int[] getTexIndices2() {
		return texIndices2;
	}

	@Override
	public int[] getTexIndices3() {
		return texIndices3;
	}

	@Override
	public Model getUnskewedModel() {
		return this;
	}

	@Override
	public void drawFrustum(int zero, int xRotate, int yRotate, int zRotate, int xCamera, int yCamera, int zCamera) {
	}

	@Override
	public void drawOrtho(int zero, int xRotate, int yRotate, int zRotate, int xCamera, int yCamera, int zCamera, int zoom) {}


	@Override
	public Model getModel() {
		return this;
	}

	@Override
	public int getModelHeight() {
		calculateBoundsCylinder();
		return modelHeight;
	}

	@Override
	public void setModelHeight(int modelHeight) {
		this.modelHeight = modelHeight;
	}

	@Override
	public int getAnimationHeightOffset() {
		return 0;
	}

	@Override
	public int getRenderMode() {
		return RENDERMODE_DEFAULT;
	}


	@Override
	public Node getNext() {
		return null;
	}

	@Override
	public Node getPrevious() {
		return null;
	}

	@Override
	public long getHash() {
		return 0;
	}


	@Override
	public Model rotateY90Ccw() {
		throw new UnsupportedOperationException("A cache scene model is already built");
	}

	@Override
	public Model rotateY180Ccw() {
		throw new UnsupportedOperationException("A cache scene model is already built");
	}

	@Override
	public Model rotateY270Ccw() {
		throw new UnsupportedOperationException("A cache scene model is already built");
	}

	@Override
	public Model translate(int x, int y, int z) {
		throw new UnsupportedOperationException("A cache scene model is already built");
	}

	@Override
	public Model scale(int x, int y, int z) {
		throw new UnsupportedOperationException("A cache scene model is already built");
	}

	private static final class ModelAABB implements AABB {
		private final int extremeX, extremeY, extremeZ;
		private final int centerY;

		ModelAABB(int xyzMag, int modelHeight, int bottomY) {
			extremeX = extremeZ = xyzMag;
			extremeY = modelHeight + bottomY >> 1;
			centerY = bottomY - extremeY;
		}

		@Override
		public int getCenterX() {
			return 0;
		}

		@Override
		public int getCenterY() {
			return centerY;
		}

		@Override
		public int getCenterZ() {
			return 0;
		}

		@Override
		public int getExtremeX() {
			return extremeX;
		}

		@Override
		public int getExtremeY() {
			return extremeY;
		}

		@Override
		public int getExtremeZ() {
			return extremeZ;
		}
	}
}
