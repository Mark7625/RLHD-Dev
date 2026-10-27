package rs117.hd.gui;

import javax.swing.JComponent;

/** Implement and annotate with {@link HdPanel} for automatic discovery by {@link HdPanelRegistry}. */
public interface HdDevPanel
{
	JComponent buildContent();

	default void onRegister() {}

	default void onUnregister() {}
}
