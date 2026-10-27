package rs117.hd.gui.impl;

import java.awt.GridLayout;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import rs117.hd.gui.*;

@Singleton
@HdPanel(value = DockType.SIDEBAR, name = "Scene Debug")
public class SceneDebugFrame implements HdDevPanel
{
	private static final int REFRESH_MS = 200;

	@Inject
	private Client client;

	private JLabel positionLabel;
	private JLabel cameraLabel;
	private Timer timer;

	@Override
	public JComponent buildContent()
	{
		JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
		positionLabel = new JLabel("Position: -");
		cameraLabel = new JLabel("Camera: -");
		panel.add(positionLabel);
		panel.add(cameraLabel);
		return panel;
	}

	@Override
	public void onRegister()
	{
		timer = new Timer(REFRESH_MS, e -> refresh());
		timer.start();
	}

	@Override
	public void onUnregister()
	{
		if (timer != null)
		{
			timer.stop();
			timer = null;
		}
	}

	private void refresh()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
			return;

		WorldPoint loc = player.getWorldLocation();
		positionLabel.setText(String.format("Position: %d, %d (plane %d)", loc.getX(), loc.getY(), loc.getPlane()));
		cameraLabel.setText(String.format("Camera: yaw %d, pitch %d", client.getCameraYaw(), client.getCameraPitch()));
	}
}
