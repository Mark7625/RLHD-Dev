package rs117.hd.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;
import net.runelite.client.ui.ColorScheme;

/** Floating-window presentation of a {@link HdDevFrame}. Closing it docks back in instead of destroying it. */
class HdDevFrameWindow extends JFrame
{
	private static final Dimension MIN_SIZE = new Dimension(280, 200);
	private static final Dimension DEFAULT_SIZE = new Dimension(420, 320);

	HdDevFrameWindow(String title, JComponent content, Component owner, Runnable onPopIn)
	{
		super(title);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setResizable(true);
		setMinimumSize(MIN_SIZE);
		getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());
		add(HdDevFrameHeader.create(title, "⇙", "Dock to sidebar", onPopIn), BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);

		pack();
		setSize(Math.max(getWidth(), DEFAULT_SIZE.width), Math.max(getHeight(), DEFAULT_SIZE.height));
		setLocationRelativeTo(owner);

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				onPopIn.run();
			}
		});
	}
}
