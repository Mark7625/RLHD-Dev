package rs117.hd.gui;

import java.awt.BorderLayout;
import java.lang.reflect.Field;
import javax.swing.JComponent;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ClientUI;

/**
 * RuneLite has no supported way to add a panel below the client canvas, so this reaches into
 * {@code ClientUI}'s private {@code clientPanel} field via reflection and adds to its SOUTH slot
 * instead. Fails closed (logs once, does nothing) if RuneLite's internals ever change.
 */
@Slf4j
final class HdDevFrameBottomDock
{
	private static Field clientPanelField;
	private static boolean reflectionFailed;

	private HdDevFrameBottomDock() {}

	static void add(ClientUI clientUi, JComponent content)
	{
		JPanel panel = getClientPanel(clientUi);
		if (panel == null)
			return;
		panel.add(content, BorderLayout.SOUTH);
		panel.revalidate();
		panel.repaint();
	}

	static void remove(ClientUI clientUi, JComponent content)
	{
		JPanel panel = getClientPanel(clientUi);
		if (panel == null)
			return;
		panel.remove(content);
		panel.revalidate();
		panel.repaint();
	}

	private static JPanel getClientPanel(ClientUI clientUi)
	{
		if (reflectionFailed)
			return null;

		try
		{
			if (clientPanelField == null)
			{
				clientPanelField = ClientUI.class.getDeclaredField("clientPanel");
				clientPanelField.setAccessible(true);
			}
			return (JPanel) clientPanelField.get(clientUi);
		}
		catch (ReflectiveOperationException e)
		{
			reflectionFailed = true;
			log.warn(
				"Couldn't reach RuneLite's internal client panel via reflection (its internals may have " +
				"changed) - dev-tools bottom panels won't be shown", e);
			return null;
		}
	}
}
