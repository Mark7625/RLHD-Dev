package rs117.hd.gui;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;

/** A single {@link HdDevFrame} whose content is a tab strip, shared by multiple dev-tools panels. */
@Singleton
public class HdDevFrameGroup extends HdDevFrame
{
	private JTabbedPane tabs;

	@Inject
	private HdDevFrameGroup()
	{
		super("117 HD Dev Tools", null, DockType.SIDEBAR, 1);
	}

	@Override
	protected JComponent buildContent()
	{
		tabs = new JTabbedPane();
		return tabs;
	}

	public void addTab(String title, JComponent content)
	{
		runOnEdt(() -> {
			if (!isRegistered())
				register();
			tabs.addTab(title, content);
		});
	}

	public void removeTab(JComponent content)
	{
		runOnEdt(() -> {
			if (tabs == null)
				return;
			int index = tabs.indexOfComponent(content);
			if (index >= 0)
				tabs.removeTabAt(index);
		});
	}

	public void selectTab(JComponent content)
	{
		runOnEdt(() -> {
			open();
			if (tabs == null)
				return;
			int index = tabs.indexOfComponent(content);
			if (index >= 0)
				tabs.setSelectedIndex(index);
		});
	}
}
