package rs117.hd.dev.sceneloader.scene;

import net.runelite.cache.definitions.ModelDefinition;

final class ModelBounds {
	final int minX, maxX;
	final int minZ, maxZ;
	final int maxY, modelHeight;

	private ModelBounds(int minX, int maxX, int minZ, int maxZ, int maxY, int modelHeight) {
		this.minX = minX;
		this.maxX = maxX;
		this.minZ = minZ;
		this.maxZ = maxZ;
		this.maxY = maxY;
		this.modelHeight = modelHeight;
	}

	static ModelBounds of(ModelDefinition def) {
		int minX = 999999, maxX = -999999;
		int minZ = 99999, maxZ = -99999;
		int maxY = 0, modelHeight = 0;

		for (int i = 0; i < def.vertexCount; i++) {
			int x = def.vertexX[i];
			int y = def.vertexY[i];
			int z = def.vertexZ[i];

			if (x < minX)
				minX = x;
			if (x > maxX)
				maxX = x;
			if (z < minZ)
				minZ = z;
			if (z > maxZ)
				maxZ = z;
			if (-y > modelHeight)
				modelHeight = -y;
			if (y > maxY)
				maxY = y;
		}

		return new ModelBounds(minX, maxX, minZ, maxZ, maxY, modelHeight);
	}
}
