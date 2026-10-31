package rs117.hd.dev.sceneloader.scene;

public final class HslPalette {
	public static final double DEFAULT_BRIGHTNESS = 0.8;

	private static final int SIZE = 65536;

	private final int[] palette = new int[SIZE];

	public HslPalette(double brightness) {
		for (int hueSat = 0; hueSat < 512; hueSat++) {
			double hue = (hueSat >> 3) / 64.0 + 0.0078125;
			double saturation = (hueSat & 7) / 8.0 + 0.0625;
			int index = hueSat * 128;
			for (int luminance = 0; luminance < 128; luminance++) {
				double lightness = luminance / 128.0;
				double r = lightness;
				double g = lightness;
				double b = lightness;
				if (saturation != 0) {
					double high;
					if (lightness < 0.5) {
						high = lightness * (saturation + 1.0);
					} else {
						high = saturation + lightness - saturation * lightness;
					}
					double low = lightness * 2.0 - high;

					double redHue = hue + 1.0 / 3.0;
					if (redHue > 1.0)
						redHue--;
					double blueHue = hue - 1.0 / 3.0;
					if (blueHue < 0.0)
						blueHue++;

					r = hueToChannel(redHue, high, low, lightness);
					g = hueToChannel(hue, high, low, lightness);
					b = hueToChannel(blueHue, high, low, lightness);
				}

				int rgb = ((int) (r * 256.0) << 16) + ((int) (g * 256.0) << 8) + (int) (b * 256.0);
				int adjusted = applyBrightness(rgb, brightness);
				palette[index++] = adjusted == 0 ? 1 : adjusted;
			}
		}
	}

	private static double hueToChannel(double hue, double high, double low, double fallback) {
		if (hue * 6.0 < 1.0)
			return (high - low) * 6.0 * hue + low;
		if (hue * 2.0 < 1.0)
			return high;
		if (hue * 3.0 < 2.0)
			return (high - low) * (2.0 / 3.0 - hue) * 6.0 + low;
		return low;
	}

	private static int applyBrightness(int rgb, double brightness) {
		double r = Math.pow((rgb >> 16) / 256.0, brightness);
		double g = Math.pow((rgb >> 8 & 0xFF) / 256.0, brightness);
		double b = Math.pow((rgb & 0xFF) / 256.0, brightness);
		return ((int) (r * 256.0) << 16) + ((int) (g * 256.0) << 8) + (int) (b * 256.0);
	}

	public int toRgb(int packedHsl) {
		if (packedHsl == -2)
			return 0;
		return palette[packedHsl & 0xFFFF];
	}
}
