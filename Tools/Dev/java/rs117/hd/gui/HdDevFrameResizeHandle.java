package rs117.hd.gui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** A thin draggable strip that resizes a target by mutating its preferred size and revalidating. */
class HdDevFrameResizeHandle extends JPanel
{
	private static final int THICKNESS = 6;

	private HdDevFrameResizeHandle(boolean vertical)
	{
		setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		setPreferredSize(vertical ? new Dimension(THICKNESS, 0) : new Dimension(0, THICKNESS));
		setCursor(Cursor.getPredefinedCursor(vertical ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(ColorScheme.BRAND_ORANGE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			}
		});
	}

	static HdDevFrameResizeHandle forWidth(JComponent target, int minWidth, int maxWidth)
	{
		HdDevFrameResizeHandle handle = new HdDevFrameResizeHandle(true);
		handle.trackDrag((dx, dy) -> {
			Dimension size = target.getPreferredSize();
			int width = clamp(size.width - dx, minWidth, maxWidth);
			target.setPreferredSize(new Dimension(width, size.height));
			target.revalidate();
			target.repaint();
		});
		return handle;
	}

	static HdDevFrameResizeHandle forHeight(JComponent target, int minHeight, int maxHeight)
	{
		HdDevFrameResizeHandle handle = new HdDevFrameResizeHandle(false);
		handle.trackDrag((dx, dy) -> {
			Dimension size = target.getPreferredSize();
			int height = clamp(size.height - dy, minHeight, maxHeight);
			target.setPreferredSize(new Dimension(size.width, height));
			target.revalidate();
			target.repaint();
		});
		return handle;
	}

	private void trackDrag(DragHandler handler)
	{
		MouseAdapter listener = new MouseAdapter()
		{
			private int lastX;
			private int lastY;

			@Override
			public void mousePressed(MouseEvent e)
			{
				lastX = e.getXOnScreen();
				lastY = e.getYOnScreen();
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				int dx = e.getXOnScreen() - lastX;
				int dy = e.getYOnScreen() - lastY;
				lastX = e.getXOnScreen();
				lastY = e.getYOnScreen();
				handler.onDrag(dx, dy);
			}
		};
		addMouseListener(listener);
		addMouseMotionListener(listener);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private interface DragHandler
	{
		void onDrag(int dx, int dy);
	}
}
