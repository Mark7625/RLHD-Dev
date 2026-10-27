package rs117.hd.gui;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import net.runelite.client.ui.PluginPanel;

/** Sidebar presentation of a {@link HdDevFrame}: header, a width drag handle, then content. */
class HdDevFrameSidebarPanel extends PluginPanel
{
	private static final int MIN_WIDTH = PluginPanel.PANEL_WIDTH;
	private static final int MAX_WIDTH = 700;

	HdDevFrameSidebarPanel(String title, JComponent content, Runnable onPopOut)
	{
		super();
		setLayout(new BorderLayout());
		add(HdDevFrameHeader.create(title, "⇗", "Pop out to window", onPopOut), BorderLayout.NORTH);
		// resize targets RuneLite's own wrapper, since that's what the sidebar actually lays out by
		add(HdDevFrameResizeHandle.forWidth(getWrappedPanel(), MIN_WIDTH, MAX_WIDTH), BorderLayout.WEST);
		add(content, BorderLayout.CENTER);
	}
}
