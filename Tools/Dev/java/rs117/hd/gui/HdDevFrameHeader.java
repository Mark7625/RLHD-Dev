package rs117.hd.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** Small title bar shared by every {@link HdDevFrame} presentation. {@code onClick} may be null to omit the button. */
class HdDevFrameHeader
{
	private HdDevFrameHeader() {}

	static JPanel create(String title, String buttonText, String buttonTooltip, Runnable onClick)
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
		titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(titleLabel, BorderLayout.CENTER);

		if (onClick != null)
		{
			JButton button = new JButton(buttonText);
			button.setToolTipText(buttonTooltip);
			button.setFocusable(false);
			button.setMargin(new java.awt.Insets(0, 4, 0, 4));
			button.addActionListener(e -> onClick.run());
			header.add(button, BorderLayout.EAST);
		}

		return header;
	}
}
