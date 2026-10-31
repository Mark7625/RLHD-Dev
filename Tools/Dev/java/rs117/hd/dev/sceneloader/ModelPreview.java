package rs117.hd.dev.sceneloader;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import rs117.hd.dev.sceneloader.scene.BuiltModel;

public class ModelPreview extends JComponent {
	private static final double START_YAW = 0.6;
	private static final double START_PITCH = 0.5;
	private static final double RADIANS_PER_PIXEL = 0.012;
	private static final double MAX_PITCH = Math.PI / 2 - 0.05;
	private static final int CAPTION_HEIGHT = 13;
	private static final int PADDING = 4;

	@Nullable
	private BuiltModel model;
	@Nullable
	private int[] faceColors;
	private String caption = "";
	private String emptyText = "";

	private double yaw = START_YAW;
	private double pitch = START_PITCH;

	private float[] rotatedX = new float[0], rotatedY = new float[0], rotatedZ = new float[0];
	private int[] faceOrder = new int[0];
	private float[] faceDepth = new float[0];
	private final int[] polyX = new int[3], polyY = new int[3];

	public ModelPreview(int size) {
		var bounds = new Dimension(size, size + CAPTION_HEIGHT);
		setPreferredSize(bounds);
		setMinimumSize(bounds);
		setMaximumSize(bounds);
		setFont(FontManager.getRunescapeSmallFont());
		ToolTipManager.sharedInstance().registerComponent(this);

		var drag = new MouseAdapter() {
			private int lastX, lastY;

			@Override
			public void mousePressed(MouseEvent e) {
				lastX = e.getX();
				lastY = e.getY();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				yaw -= (e.getX() - lastX) * RADIANS_PER_PIXEL;
				pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch + (e.getY() - lastY) * RADIANS_PER_PIXEL));
				lastX = e.getX();
				lastY = e.getY();
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					yaw = START_YAW;
					pitch = START_PITCH;
					repaint();
				}
			}
		};
		addMouseListener(drag);
		addMouseMotionListener(drag);
	}

	public static final class Shape {
		final BuiltModel model;
		final int[] faceColors;

		public Shape(BuiltModel model, int[] faceColors) {
			this.model = model;
			this.faceColors = faceColors;
		}
	}

	public void show(@Nullable Shape shape, String caption, String emptyText) {
		this.model = shape == null ? null : shape.model;
		this.faceColors = shape == null ? null : shape.faceColors;
		this.caption = caption;
		this.emptyText = emptyText;
		setToolTipText(caption.isEmpty() ? null : caption + " — drag to turn, double click to straighten");
		repaint();
	}

	public String getCaption() {
		return caption;
	}

	public void matchAngleOf(ModelPreview other) {
		yaw = other.yaw;
		pitch = other.pitch;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics.create();
		try {
			int width = getWidth();
			int height = getHeight() - CAPTION_HEIGHT;

			g.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g.fillRect(0, 0, width, height);
			g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
			g.drawRect(0, 0, width - 1, height - 1);

			if (model != null && faceColors != null && model.getFaceCount() > 0) {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				paintModel(g, width, height);
			} else {
				g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
				g.setFont(getFont());
				int textWidth = g.getFontMetrics().stringWidth(emptyText);
				g.drawString(emptyText, (width - textWidth) / 2, height / 2);
			}

			if (!caption.isEmpty()) {
				g.setColor(Color.LIGHT_GRAY);
				g.setFont(getFont());
				int textWidth = g.getFontMetrics().stringWidth(caption);
				g.drawString(caption, Math.max(0, (width - textWidth) / 2), height + CAPTION_HEIGHT - 3);
			}
		} finally {
			g.dispose();
		}
	}

	private void paintModel(Graphics2D g, int width, int height) {
		assert model != null && faceColors != null;

		int vertexCount = model.getVerticesCount();
		float[] sourceX = model.getVerticesX();
		float[] sourceY = model.getVerticesY();
		float[] sourceZ = model.getVerticesZ();

		if (rotatedX.length < vertexCount) {
			rotatedX = new float[vertexCount];
			rotatedY = new float[vertexCount];
			rotatedZ = new float[vertexCount];
		}

		float sinYaw = (float) Math.sin(yaw), cosYaw = (float) Math.cos(yaw);
		float sinPitch = (float) Math.sin(pitch), cosPitch = (float) Math.cos(pitch);

		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
		float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (int v = 0; v < vertexCount; v++) {
			float x = sourceX[v], y = sourceY[v], z = sourceZ[v];

			float turnedX = x * cosYaw - z * sinYaw;
			float turnedZ = x * sinYaw + z * cosYaw;
			float tippedY = y * cosPitch - turnedZ * sinPitch;
			float tippedZ = y * sinPitch + turnedZ * cosPitch;

			rotatedX[v] = turnedX;
			rotatedY[v] = tippedY;
			rotatedZ[v] = tippedZ;

			if (turnedX < minX) minX = turnedX;
			if (turnedX > maxX) maxX = turnedX;
			if (tippedY < minY) minY = tippedY;
			if (tippedY > maxY) maxY = tippedY;
		}

		float spanX = Math.max(1, maxX - minX);
		float spanY = Math.max(1, maxY - minY);
		float scale = Math.min((width - 2 * PADDING) / spanX, (height - 2 * PADDING) / spanY);
		float offsetX = width / 2f - (minX + maxX) / 2f * scale;
		float offsetY = height / 2f - (minY + maxY) / 2f * scale;

		int faceCount = model.getFaceCount();
		int[] indicesA = model.getFaceIndices1();
		int[] indicesB = model.getFaceIndices2();
		int[] indicesC = model.getFaceIndices3();

		if (faceOrder.length < faceCount) {
			faceOrder = new int[faceCount];
			faceDepth = new float[faceCount];
		}

		int drawable = 0;
		for (int f = 0; f < faceCount; f++) {
			if (faceColors[f] < 0)
				continue;
			int a = indicesA[f], b = indicesB[f], c = indicesC[f];
			if (a >= vertexCount || b >= vertexCount || c >= vertexCount)
				continue;
			faceDepth[f] = rotatedZ[a] + rotatedZ[b] + rotatedZ[c];
			faceOrder[drawable++] = f;
		}

		sortByDepthDescending(faceOrder, faceDepth, drawable);

		for (int i = 0; i < drawable; i++) {
			int f = faceOrder[i];
			int a = indicesA[f], b = indicesB[f], c = indicesC[f];

			polyX[0] = Math.round(rotatedX[a] * scale + offsetX);
			polyX[1] = Math.round(rotatedX[b] * scale + offsetX);
			polyX[2] = Math.round(rotatedX[c] * scale + offsetX);
			polyY[0] = Math.round(rotatedY[a] * scale + offsetY);
			polyY[1] = Math.round(rotatedY[b] * scale + offsetY);
			polyY[2] = Math.round(rotatedY[c] * scale + offsetY);

			g.setColor(new Color(faceColors[f]));
			g.fillPolygon(polyX, polyY, 3);
			g.drawPolygon(polyX, polyY, 3);
		}
	}

	private static void sortByDepthDescending(int[] order, float[] depth, int count) {
		for (int i = 1; i < count; i++) {
			int face = order[i];
			float d = depth[face];
			int j = i - 1;
			while (j >= 0 && depth[order[j]] < d) {
				order[j + 1] = order[j];
				j--;
			}
			order[j + 1] = face;
		}
	}
}
