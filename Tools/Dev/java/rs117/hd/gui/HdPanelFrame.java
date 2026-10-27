package rs117.hd.gui;

import javax.swing.JComponent;

/** Adapts a plain {@link HdDevPanel} onto the {@link HdDevFrame} presentation engine. */
final class HdPanelFrame extends HdDevFrame
{
	private final HdDevPanel panel;

	HdPanelFrame(HdPanel meta, HdDevPanel panel)
	{
		super(meta.name(), null, meta.value(), 0, meta.canDeDock());
		this.panel = panel;
	}

	@Override
	protected JComponent buildContent()
	{
		return panel.buildContent();
	}

	@Override
	protected void onRegister()
	{
		panel.onRegister();
	}

	@Override
	protected void onUnregister()
	{
		panel.onUnregister();
	}
}
