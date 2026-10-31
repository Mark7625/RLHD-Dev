package rs117.hd.dev.sceneloader.standalone.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import rs117.hd.HdPluginConfig;

@Slf4j
public final class HdConfigPanel extends JPanel {
	private static final int WIDTH = 360;

	private static final int CONTROL_WIDTH = 140;

	private final HdPluginConfig config;
	private final BiConsumer<Method, String> onChange;

	private final List<Section> sections = new ArrayList<>();
	private final JTextField search = new JTextField();

	public HdConfigPanel(HdPluginConfig config, BiConsumer<Method, String> onChange) {
		this.config = config;
		this.onChange = onChange;

		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(WIDTH, 100));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 1, 0, 0, ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)
		));

		var top = new JPanel(new BorderLayout(6, 0));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		var filter = new JLabel("Filter");
		filter.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		top.add(filter, BorderLayout.WEST);
		top.add(search, BorderLayout.CENTER);
		top.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		search.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				filter();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				filter();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				filter();
			}
		});

		var stack = new JPanel();
		stack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		for (Section section : build())
			stack.add(section);
		stack.add(Box.createVerticalGlue());

		var scroll = new JScrollPane(stack, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(top, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
	}

	private List<Section> build() {
		var byField = new LinkedHashMap<String, ConfigSection>();
		for (Field field : HdPluginConfig.class.getDeclaredFields()) {
			var section = field.getAnnotation(ConfigSection.class);
			if (section == null)
				continue;
			try {
				byField.put(String.valueOf(field.get(null)), section);
			} catch (IllegalAccessException ex) {
				log.warn("Could not read the section named by {}", field.getName(), ex);
			}
		}

		var items = new LinkedHashMap<String, List<Method>>();
		for (Method method : HdPluginConfig.class.getMethods()) {
			var item = method.getAnnotation(ConfigItem.class);
			if (item == null || method.getParameterCount() > 0 || item.hidden())
				continue;
			items.computeIfAbsent(item.section(), key -> new ArrayList<>()).add(method);
		}

		for (var list : items.values())
			list.sort(Comparator.comparingInt(method -> method.getAnnotation(ConfigItem.class).position()));

		var order = new ArrayList<Map.Entry<String, ConfigSection>>(byField.entrySet());
		order.sort(Comparator.comparingInt(entry -> entry.getValue().position()));

		var built = new ArrayList<Section>();
		var loose = items.get("");
		if (loose != null && !loose.isEmpty())
			built.add(new Section("117 HD", "", loose, false));

		for (var entry : order) {
			var list = items.get(entry.getKey());
			if (list == null || list.isEmpty())
				continue;
			built.add(new Section(
				entry.getValue().name(),
				entry.getValue().description(),
				list,
				entry.getValue().closedByDefault()
			));
		}

		sections.addAll(built);
		return built;
	}

	private void filter() {
		String wanted = search.getText().trim().toLowerCase(Locale.ROOT);
		for (Section section : sections)
			section.filter(wanted);
		revalidate();
		repaint();
	}

	private final class Section extends JPanel {
		private final JPanel contents = new JPanel();
		private final List<Setting> settings = new ArrayList<>();
		private final JLabel arrow = new JLabel();
		private boolean open;

		Section(String name, String description, List<Method> methods, boolean closed) {
			setLayout(new BorderLayout());
			setAlignmentX(LEFT_ALIGNMENT);
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

			contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
			contents.setBackground(ColorScheme.DARK_GRAY_COLOR);
			contents.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 0));

			for (Method method : methods) {
				var setting = new Setting(method);
				settings.add(setting);
				contents.add(setting);
			}

			var title = new JLabel(name);
			title.setFont(FontManager.getDefaultBoldFont());
			title.setForeground(ColorScheme.TEXT_COLOR);
			if (!description.isEmpty())
				title.setToolTipText(asHtml(description));

			arrow.setForeground(ColorScheme.BRAND_ORANGE);

			var header = new JPanel(new BorderLayout(6, 0));
			header.add(arrow, BorderLayout.WEST);
			header.add(title, BorderLayout.CENTER);
			header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			header.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR),
				BorderFactory.createEmptyBorder(5, 6, 5, 6)
			));
			header.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			header.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mousePressed(java.awt.event.MouseEvent event) {
					open(!open);
				}

				@Override
				public void mouseEntered(java.awt.event.MouseEvent event) {
					header.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent event) {
					header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				}
			});

			add(header, BorderLayout.NORTH);
			add(contents, BorderLayout.CENTER);
			open(!closed);
		}

		private void open(boolean open) {
			this.open = open;
			arrow.setText(open ? "▾" : "▸");
			contents.setVisible(open);
			revalidate();
		}

		void filter(String wanted) {
			int showing = 0;
			for (Setting setting : settings) {
				boolean matches = setting.matches(wanted);
				setting.setVisible(matches);
				if (matches)
					showing++;
			}

			setVisible(showing > 0);
			if (!wanted.isEmpty() && showing > 0)
				open(true);
		}

		@Override
		public Dimension getMaximumSize() {
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	private final class Setting extends JPanel {
		private final String searchable;

		Setting(Method method) {
			var item = method.getAnnotation(ConfigItem.class);
			var units = method.getAnnotation(Units.class);

			String name = item.name() + (units == null ? "" : " (" + units.value().trim() + ")");
			searchable = (item.name() + " " + item.description() + " " + method.getName()).toLowerCase(Locale.ROOT);

			setLayout(new BorderLayout(6, 0));
			setAlignmentX(LEFT_ALIGNMENT);
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
			setToolTipText(asHtml(item.description()));

			JComponent control = controlFor(method);
			var label = new JLabel(name);
			label.setToolTipText(asHtml(item.description()));
			label.setHorizontalAlignment(SwingConstants.LEFT);
			label.setForeground(ColorScheme.TEXT_COLOR);

			if (control instanceof JCheckBox) {
				((JCheckBox) control).setText(name);
				((JCheckBox) control).setToolTipText(asHtml(item.description()));
				add(control, BorderLayout.CENTER);
			} else {
				control.setPreferredSize(new Dimension(CONTROL_WIDTH, control.getPreferredSize().height));
				var holder = new JPanel(new BorderLayout());
				holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
				holder.add(control, BorderLayout.CENTER);
				holder.setPreferredSize(new Dimension(CONTROL_WIDTH, control.getPreferredSize().height));

				add(label, BorderLayout.CENTER);
				add(holder, BorderLayout.EAST);
			}
		}

		boolean matches(String wanted) {
			return wanted.isEmpty() || searchable.contains(wanted);
		}

		@Override
		public Dimension getMaximumSize() {
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	private JComponent controlFor(Method method) {
		Class<?> type = method.getReturnType();
		Object current = valueOf(method);

		if (type == boolean.class || type == Boolean.class) {
			var box = new JCheckBox();
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			box.setForeground(ColorScheme.TEXT_COLOR);
			box.setSelected(Boolean.TRUE.equals(current));
			box.addActionListener(changed(method, () -> String.valueOf(box.isSelected())));
			return box;
		}

		if (type.isEnum()) {
			var options = new JComboBox<>(type.getEnumConstants());
			options.setSelectedItem(current);
			options.addActionListener(changed(method, () -> ((Enum<?>) options.getSelectedItem()).name()));
			return options;
		}

		if (type == int.class || type == Integer.class || type == double.class || type == Double.class) {
			var range = method.getAnnotation(Range.class);
			double value = current instanceof Number ? ((Number) current).doubleValue() : 0;
			double min = range == null ? -100_000 : range.min();
			double max = range == null ? 100_000 : range.max();
			boolean whole = type == int.class || type == Integer.class;

			var spinner = new JSpinner(new SpinnerNumberModel(
				Math.max(min, Math.min(max, value)), min, max, whole ? 1 : 0.1));
			spinner.addChangeListener(event -> {
				double now = ((Number) spinner.getValue()).doubleValue();
				send(method, whole ? String.valueOf((int) now) : String.valueOf(now));
			});
			return spinner;
		}

		var text = new JTextField(current == null ? "" : String.valueOf(current), 8);
		text.addActionListener(changed(method, text::getText));
		return text;
	}

	private ActionListener changed(Method method, java.util.function.Supplier<String> value) {
		return event -> send(method, value.get());
	}

	private void send(Method method, String value) {
		try {
			onChange.accept(method, value);
		} catch (Throwable ex) {
			log.warn("Could not change {}", method.getName(), ex);
		}
	}

	@Nullable
	private Object valueOf(Method method) {
		try {
			return method.invoke(config);
		} catch (Throwable ex) {
			log.debug("Could not read {}", method.getName(), ex);
			return null;
		}
	}

	private static String asHtml(String description) {
		if (description == null || description.isEmpty())
			return null;
		return "<html><body style='width:260px'>" + description + "</body></html>";
	}
}
