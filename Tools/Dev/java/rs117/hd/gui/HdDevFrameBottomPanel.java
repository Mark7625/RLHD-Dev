package rs117.hd.gui;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** Bottom-dock presentation of a {@link HdDevFrame}: a height drag handle, header, then content. */
class HdDevFrameBottomPanel extends JPanel
{
	private static final int MIN_HEIGHT = 40;
	private static final int MAX_HEIGHT = 600;

	HdDevFrameBottomPanel(String title, JComponent content, Runnable onPopOut)
	{
		super(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel(new BorderLayout());
		top.add(HdDevFrameResizeHandle.forHeight(this, MIN_HEIGHT, MAX_HEIGHT), BorderLayout.NORTH);
		top.add(HdDevFrameHeader.create(title, "⇗", "Pop out to window", onPopOut), BorderLayout.SOUTH);

		add(top, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);
	}
}
