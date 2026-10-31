package rs117.hd.dev.sceneloader;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import rs117.hd.dev.sceneloader.map.MapHost;
import rs117.hd.dev.sceneloader.scene.SceneBuilder;
import rs117.hd.dev.sceneloader.scene.VariantObject;
import rs117.hd.dev.sceneloader.scene.VariantOptions;

import static net.runelite.api.Constants.REGION_SIZE;

public class SceneLoaderPanel extends PluginPanel {
	/** Somewhere recognisable for the map to open looking at, which is not the same as picking it */
	private static final int LUMBRIDGE = 50 << 8 | 50;

	private static final int ROW_HEIGHT = 24;
	private static final int SPINNER_WIDTH = 66;
	private static final int WIDE_SPINNER_WIDTH = 100;
	private static final int ICON_BUTTON_WIDTH = 26;
	private static final int LIST_HEIGHT = 150;
	private static final int PREVIEW_SIZE = 86;
	private static final int FIRST_FORM_ITEM = 2;

	private final SceneLoader tools;

	private final JTextField regionIds = new JTextField();
	private final JLabel regionNote = note(Color.GRAY);
	private final JSpinner expandedChunks = spinner(5, 0, 5);
	private final JSpinner plane = spinner(0, 0, 3);
	private final JCheckBox planesBelow = new JCheckBox("Draw planes below", true);
	private final JCheckBox showRoofs = new JCheckBox("Draw roofs", false);
	private final JCheckBox keepLoaded = new JCheckBox("Keep loaded", true);
	private final JCheckBox freeCamera = new JCheckBox("Free camera", true);
	private final JSpinner cameraSpeed = spinner(1, 1, 20);

	private final JLabel status = note(Color.LIGHT_GRAY);
	private final JLabel summary = note(Color.GRAY);

	private final DefaultListModel<VariantObject> variantModel = new DefaultListModel<>();
	private final JList<VariantObject> variantList = new JList<>(variantModel);
	private final JComboBox<String> formChoice = new JComboBox<>();
	private final JLabel variantHeading = heading("Variable objects");
	private final JLabel formLabel = heading("Form");
	private final ModelPreview nowPreview = new ModelPreview(PREVIEW_SIZE);
	private final ModelPreview nextPreview = new ModelPreview(PREVIEW_SIZE);
	private JPanel previewRow;
	private int[] formOptions = new int[0];
	private JButton goTo;
	private int goToIndex;
	private JButton applyForm;
	private JButton resetForm;
	private JScrollPane variantScroll;

	@Nullable
	private MapHost mapHost;

	private int filling;

	public SceneLoaderPanel(SceneLoader tools) {
		super(true);
		this.tools = tools;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		regionIds.setToolTipText(
			"One region id, or several separated by commas to lay them out side by side. "
			+ "Up to " + SceneBuilder.MAX_STITCHED_REGIONS + " fit, "
			+ SceneBuilder.REGIONS_PER_SIDE + " to a side, and extra chunks are ignored for those.");
		regionIds.setFont(FontManager.getRunescapeSmallFont());
		add(labelled("Region ID", narrow(regionIds, WIDE_SPINNER_WIDTH), mapButton()));
		add(regionNote);
		add(Box.createVerticalStrut(6));

		expandedChunks.setToolTipText("How far past the usual scene to build, in chunks of eight tiles");
		expandedChunks.addChangeListener(e -> {
			if (mapHost != null)
				mapHost.refresh();
		});
		add(labelled("Extra chunks", expandedChunks, SPINNER_WIDTH));
		add(Box.createVerticalStrut(8));

		plane.setToolTipText("Which plane to look at, in place of the one the player is standing on");
		plane.addChangeListener(e -> {
			tools.setPlane((Integer) plane.getValue());
			if (filling == 0)
				reloadForPlane();
		});
		add(labelled("Plane", plane, SPINNER_WIDTH));
		add(Box.createVerticalStrut(4));

		checkBox(planesBelow, "Keep the planes under the one being looked at, rather than only that one");
		planesBelow.addActionListener(e -> tools.setPlanesBelow(planesBelow.isSelected()));
		add(planesBelow);
		add(Box.createVerticalStrut(4));

		checkBox(showRoofs, "Leave the roofs on. Turning them off opens up everything above the plane below.");
		showRoofs.addActionListener(e -> tools.setShowRoofs(showRoofs.isSelected()));
		add(showRoofs);
		add(Box.createVerticalStrut(8));

		checkBox(keepLoaded, "Put the scene back whenever the game replaces it, such as when you move");
		keepLoaded.addActionListener(e -> tools.setKeepLoaded(keepLoaded.isSelected()));
		add(keepLoaded);
		add(Box.createVerticalStrut(4));

		checkBox(freeCamera,
			"Flies the view instead of following the player, and comes on whenever a scene is loaded. "
			+ "Moves the way the game's own detached camera does: WASD travels level, space or R to rise, "
			+ "shift or Q or F to drop, control to go slowly, and the arrow keys or a drag with the mouse "
			+ "wheel held to look around.");
		freeCamera.addActionListener(e -> {
			tools.setFreeCamera(freeCamera.isSelected());
			cameraSpeed.setEnabled(freeCamera.isSelected());
		});
		cameraSpeed.setToolTipText("How fast flying moves. One is exactly the speed of the game's own camera.");
		cameraSpeed.addChangeListener(e -> tools.setCameraSpeed((Integer) cameraSpeed.getValue()));
		add(alongside(freeCamera, cameraSpeed));

		add(Box.createVerticalStrut(10));
		add(status);
		add(summary);

		add(Box.createVerticalStrut(10));
		add(variantHeading);
		add(Box.createVerticalStrut(2));
		buildVariantSection();

		add(Box.createVerticalStrut(10));
		add(button("Load from cache", "Build the scene at these coordinates and hand it to 117 HD", this::load));
		add(Box.createVerticalStrut(4));
		add(button("Back to the game", "Drop the cache scene and let the game's own scene render again",
			tools::restoreGameScene));

		regionIds.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				regionsChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				regionsChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				regionsChanged();
			}
		});
		updateRegionNote();

		setVariantObjects(List.of());
	}

	private void buildVariantSection() {
		variantList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		variantList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		variantList.setForeground(Color.LIGHT_GRAY);
		variantList.setFont(FontManager.getRunescapeSmallFont());
		variantList.setCellRenderer(new VariantRenderer());
		variantList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
				showFormsFor(variantList.getSelectedValue());
		});

		variantScroll = new JScrollPane(variantList);
		variantScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		variantScroll.setPreferredSize(new Dimension(contentWidth(), LIST_HEIGHT));
		variantScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, LIST_HEIGHT));
		variantScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR));
		add(variantScroll);

		add(Box.createVerticalStrut(6));
		add(formLabel);
		add(dropdown(formChoice, "Which form this object takes, since the cache alone does not decide it",
			this::chooseForm));

		add(Box.createVerticalStrut(4));
		previewRow = sideBySide(nowPreview, nextPreview);
		add(previewRow);

		add(Box.createVerticalStrut(4));
		goTo = button("Go to", "Fly to this object in the scene and ring it", this::goToNext);
		add(goTo);

		add(Box.createVerticalStrut(4));
		applyForm = button("Apply transform", "Rebuild the scene with the chosen form in place", this::applyForm);
		add(applyForm);
		add(Box.createVerticalStrut(2));
		resetForm = button("Reset to default", "Put this object back to the form the game would pick",
			this::resetForm);
		add(resetForm);

	}

	private void reloadForPlane() {
		if (!tools.hasScene())
			return;
		setStatus("Rebuilding on plane " + plane.getValue() + "...", Color.LIGHT_GRAY);
		tools.reload();
	}

	private void load() {
		int[] wanted = wantedRegions();
		if (wanted.length == 0) {
			setStatus("No region id to load", Color.GRAY);
			return;
		}

		getOutOfTheWay();

		if (wanted.length > 1) {
			int fit = SceneBuilder.regionsThatFit(wanted).length;
			setStatus("Stitching " + fit + " regions...", Color.LIGHT_GRAY);
			tools.loadStitched(wanted);
			return;
		}

		int region = wanted[0];
		setStatus("Loading region " + region + "...", Color.LIGHT_GRAY);
		tools.load(worldXOf(region), worldYOf(region), (Integer) expandedChunks.getValue());
	}

	/**
	 * Puts the game view back before loading, where the map is sitting over it.
	 *
	 * <p>A map in a window of its own is left alone: there is nothing in the way, and closing it would
	 * mean opening it again for the next pick.
	 */
	private void getOutOfTheWay() {
		if (mapHost != null && mapHost.isOpen() && !mapHost.isPoppedOut())
			mapHost.close();
	}

	private int[] wantedRegions() {
		var found = new ArrayList<Integer>();
		for (String part : regionIds.getText().split("[, ]+")) {
			if (part.isEmpty())
				continue;
			try {
				int region = Integer.parseInt(part.trim());
				if (region >= 0 && region <= 0xFFFF)
					found.add(region);
			} catch (NumberFormatException ignored) {
			}
		}

		var regions = new int[found.size()];
		for (int i = 0; i < regions.length; i++)
			regions[i] = found.get(i);
		return regions;
	}

	private void regionsChanged() {
		updateRegionNote();
		int[] wanted = wantedRegions();
		expandedChunks.setEnabled(wanted.length <= 1);
		if (mapHost != null) {
			mapHost.setListed(wanted);
			mapHost.refresh();
		}
	}

	/** Ctrl clicking a region picks it, and ctrl clicking a picked one takes it back out */
	private void toggleRegion(int region) {
		int[] wanted = wantedRegions();

		var kept = new ArrayList<Integer>();
		boolean removed = false;
		for (int already : wanted) {
			if (already == region) {
				removed = true;
				continue;
			}
			kept.add(already);
		}

		if (removed) {
			setRegions(kept);
			setStatus("Took region " + region + " back out, " + kept.size() + " picked", Color.LIGHT_GRAY);
			return;
		}

		if (wanted.length >= SceneBuilder.MAX_STITCHED_REGIONS) {
			setStatus(
				SceneBuilder.MAX_STITCHED_REGIONS + " regions at a time is the most this picks up",
				Color.GRAY
			);
			return;
		}

		kept.add(region);
		setRegions(kept);
		setStatus("Picked region " + region + ", " + kept.size() + " picked", Color.LIGHT_GRAY);
	}

	/** Drops back to a single region, which is the mode everything else in the panel is written for */
	private void clearPickedRegions() {
		int[] wanted = wantedRegions();
		regionIds.setText(wanted.length == 0 ? "" : String.valueOf(wanted[0]));
		setStatus("Back to one region at a time", Color.LIGHT_GRAY);
	}

	private void setRegions(java.util.List<Integer> regions) {
		var text = new StringBuilder();
		for (int region : regions)
			text.append(text.length() == 0 ? "" : ", ").append(region);
		regionIds.setText(text.toString());
	}

	private void updateRegionNote() {
		int[] wanted = wantedRegions();
		if (wanted.length == 0) {
			regionNote.setText("No region");
			regionNote.setToolTipText(null);
			return;
		}

		if (wanted.length > 1) {
			int fit = SceneBuilder.regionsThatFit(wanted).length;
			String text = fit + " regions laid out";
			if (wanted.length > fit)
				text += ", " + (wanted.length - fit) + " will not fit";
			regionNote.setText(text);
			regionNote.setForeground(wanted.length > fit ? Color.ORANGE.darker() : Color.GRAY);
			regionNote.setToolTipText(
				"Regions that adjoin in the world are laid against each other, and anything unrelated is "
				+ "set apart. Whether they all fit depends on their shape, not just how many there are: the "
				+ "scene is " + SceneBuilder.EXTENDED + " tiles across, so a pair side by side next to "
				+ "a pair stacked up needs more room than there is.");
			return;
		}

		int region = wanted[0];
		String text = (region >> 8) + ", " + (region & 0xFF)
			+ "  at " + worldXOf(region) + ", " + worldYOf(region);
		regionNote.setText(text);
		regionNote.setForeground(Color.GRAY);
		regionNote.setToolTipText("Region " + (region >> 8) + ", " + (region & 0xFF)
			+ ", centred on world tile " + worldXOf(region) + ", " + worldYOf(region));
	}

	private static int worldXOf(int region) {
		return ((region >> 8) << 6) + REGION_SIZE / 2;
	}

	private static int worldYOf(int region) {
		return ((region & 0xFF) << 6) + REGION_SIZE / 2;
	}

	public void setVariantObjects(List<VariantObject> objects) {
		SwingUtilities.invokeLater(() -> {
			filling++;
			try {
				VariantObject previous = variantList.getSelectedValue();

				variantModel.clear();
				for (VariantObject object : objects)
					variantModel.addElement(object);

				boolean any = !objects.isEmpty();
				variantHeading.setVisible(any);
				variantScroll.setVisible(any);
				formLabel.setVisible(any);
				formChoice.setVisible(any);

				if (previous != null) {
					for (VariantObject object : objects) {
						if (object.baseId == previous.baseId) {
							variantList.setSelectedValue(object, true);
							break;
						}
					}
				}
			} finally {
				filling--;
			}
			showFormsFor(variantList.getSelectedValue());
		});
	}

	private void showFormsFor(@Nullable VariantObject object) {
		filling++;
		try {
			formChoice.removeAllItems();
			goToIndex = 0;

			if (object == null) {
				formOptions = new int[0];
				formChoice.setEnabled(false);
				retitle(formLabel, "Form");
				return;
			}

			formChoice.setEnabled(true);
			int fallback = object.firstVisibleVariant();
			formChoice.addItem(fallback < 0 ? "Default" : "Default (" + fallback + ")");
			formChoice.addItem("Hidden");

			var offered = new ArrayList<Integer>();
			for (int i = 0; i < object.variantIds.length; i++) {
				if (object.variantIds[i] == -1)
					continue;
				formChoice.addItem(i + ": " + object.variantLabel(i));
				offered.add(i);
			}
			formOptions = new int[offered.size()];
			for (int i = 0; i < formOptions.length; i++)
				formOptions[i] = offered.get(i);

			retitle(formLabel, formOptions.length == object.variantIds.length
				? "Form"
				: "Form — " + formOptions.length + " of " + object.variantIds.length);

			int choice = tools.getVariants().choiceFor(object.baseId);
			if (choice == VariantOptions.HIDDEN) {
				formChoice.setSelectedIndex(1);
			} else {
				int item = itemForForm(choice);
				formChoice.setSelectedIndex(item < 0 ? 0 : item);
			}
		} finally {
			filling--;
			updatePreviews();
		}
	}

	private void chooseForm() {
		if (filling > 0)
			return;
		updatePreviews();
	}

	private int pendingChoice() {
		int selected = formChoice.getSelectedIndex();
		if (selected <= 0)
			return VariantOptions.DEFAULT;
		if (selected == 1)
			return VariantOptions.HIDDEN;

		int form = selected - FIRST_FORM_ITEM;
		return form < formOptions.length ? formOptions[form] : VariantOptions.DEFAULT;
	}

	private int itemForForm(int form) {
		for (int i = 0; i < formOptions.length; i++)
			if (formOptions[i] == form)
				return FIRST_FORM_ITEM + i;
		return -1;
	}

	private static int objectFor(VariantObject object, int choice) {
		if (choice == VariantOptions.HIDDEN)
			return -1;

		int index = choice == VariantOptions.DEFAULT ? object.firstVisibleVariant() : choice;
		if (index < 0 || index >= object.variantIds.length)
			index = object.firstVisibleVariant();
		if (index < 0)
			return -1;
		return object.variantIds[index];
	}

	private void updatePreviews() {
		if (previewRow == null)
			return;

		VariantObject object = variantList.getSelectedValue();
		boolean any = object != null;
		previewRow.setVisible(any);
		goTo.setVisible(any);
		applyForm.setVisible(any);
		resetForm.setVisible(any);
		if (!any) {
			nowPreview.show(null, "", "");
			nextPreview.show(null, "", "");
			return;
		}

		int applied = tools.getVariants().choiceFor(object.baseId);
		int pending = pendingChoice();
		int appliedId = object.appliedId;
		int pendingId = objectFor(object, pending);

		boolean changes = pending != applied && pendingId != appliedId;
		applyForm.setEnabled(changes);
		applyForm.setText(changes ? "Apply transform" : "Already this form");
		applyForm.setToolTipText(
			changes
				? "Rebuild the scene with the chosen form in place"
				: "The scene already has this object as " + (appliedId < 0 ? "nothing" : appliedId)
				  + ", so there is nothing to apply. Pick a different form."
		);
		resetForm.setEnabled(applied != VariantOptions.DEFAULT);

		int copies = object.placements.size();
		goTo.setEnabled(copies > 0);
		goTo.setText(copies > 1 ? "Go to next of " + copies : "Go to");
		goTo.setToolTipText(
			copies == 0
				? "This object is not standing anywhere in the scene, so there is nowhere to go"
				: copies == 1
					? "Fly to this object and ring it"
					: "Fly to the next of the " + copies + " in the scene, coming back round to the first"
		);

		loadPreview(nowPreview, appliedId, "Now", null);
		loadPreview(nextPreview, pendingId, "After", nowPreview);
	}

	private void goToNext() {
		VariantObject object = variantList.getSelectedValue();
		if (object == null)
			return;

		String where = tools.goToPlacement(object, goToIndex);
		if (where == null) {
			setStatus("Nowhere to go: " + object.label() + " is not in the scene", Color.GRAY);
			return;
		}
		goToIndex++;
		if (!freeCamera.isSelected()) {
			freeCamera.setSelected(true);
			cameraSpeed.setEnabled(true);
		}
		setStatus("Looking at " + object.label() + ", " + where, Color.LIGHT_GRAY);
	}

	private void loadPreview(ModelPreview preview, int objectId, String caption, @Nullable ModelPreview matchAngle) {
		String label = objectId < 0 ? caption + ": nothing" : caption + ": " + objectId;
		preview.show(null, label, "...");
		tools.previewObject(objectId, shape -> {
			if (!label.equals(preview.getCaption()))
				return;
			preview.show(shape, label, objectId < 0 ? "Nothing" : "No model");
			if (matchAngle != null)
				preview.matchAngleOf(matchAngle);
		});
	}

	private void applyForm() {
		VariantObject object = variantList.getSelectedValue();
		if (object == null)
			return;

		int choice = pendingChoice();
		int applied = objectFor(object, choice);
		tools.getVariants().setChoice(object.baseId, choice);
		updatePreviews();
		setStatus(
			"Rebuilding with " + object.label() + " as " + (applied < 0 ? "nothing" : applied) + "...",
			Color.LIGHT_GRAY
		);
		tools.reload();
	}

	private void resetForm() {
		VariantObject object = variantList.getSelectedValue();
		if (object == null)
			return;

		tools.getVariants().setChoice(object.baseId, VariantOptions.DEFAULT);
		filling++;
		try {
			formChoice.setSelectedIndex(0);
		} finally {
			filling--;
		}
		updatePreviews();
		setStatus("Rebuilding with " + object.label() + " at its default...", Color.LIGHT_GRAY);
		tools.reload();
	}

	public void setStatus(String text, Color color) {
		SwingUtilities.invokeLater(() -> {
			status.setText(text);
			status.setToolTipText(text);
			status.setForeground(color);
		});
	}

	public void setSummary(String text) {
		SwingUtilities.invokeLater(() -> {
			summary.setText(text);
			summary.setToolTipText(text);
		});
	}


	private static int contentWidth() {
		return PANEL_WIDTH - 2 * BORDER_OFFSET;
	}

	private static void retitle(JLabel label, String text) {
		label.setMaximumSize(null);
		label.setText(text);
		label.setMaximumSize(label.getPreferredSize());
	}

	private static JLabel heading(String text) {
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.LIGHT_GRAY);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setMaximumSize(label.getPreferredSize());
		return label;
	}

	private static JLabel note(Color color) {
		JLabel label = new JLabel(" ");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		return label;
	}

	private static JPanel labelled(String text, JComponent field, @Nullable Component after) {
		JPanel panel = row();
		panel.add(heading(text));
		panel.add(Box.createHorizontalGlue());
		panel.add(field);
		if (after != null) {
			panel.add(Box.createHorizontalStrut(4));
			panel.add(after);
		}
		return panel;
	}

	private static JTextField narrow(JTextField field, int width) {
		field.setMaximumSize(new Dimension(width, ROW_HEIGHT));
		field.setPreferredSize(new Dimension(width, ROW_HEIGHT));
		return field;
	}

	private static JPanel labelled(String text, JSpinner spinner, int width) {
		return labelled(text, spinner, width, null);
	}

	private static JPanel labelled(String text, JSpinner spinner, int width, @Nullable Component after) {
		JPanel panel = row();
		panel.add(heading(text));
		panel.add(Box.createHorizontalGlue());
		panel.add(narrow(spinner, width));
		if (after != null) {
			panel.add(Box.createHorizontalStrut(4));
			panel.add(after);
		}
		return panel;
	}

	private JButton mapButton() {
		JButton button = new JButton(new ImageIcon(createMapIcon()));
		button.setToolTipText("Pick a region off the world map, or close it if it is already open");
		button.setMargin(new Insets(0, 0, 0, 0));
		var size = new Dimension(ICON_BUTTON_WIDTH, ROW_HEIGHT);
		button.setPreferredSize(size);
		button.setMaximumSize(size);
		button.addActionListener(e -> toggleMap());
		return button;
	}

	private static JPanel alongside(JCheckBox box, JSpinner spinner) {
		JPanel panel = row();
		box.setMaximumSize(box.getPreferredSize());
		panel.add(box);
		panel.add(Box.createHorizontalGlue());
		panel.add(narrow(spinner, SPINNER_WIDTH));
		return panel;
	}

	private static JSpinner narrow(JSpinner spinner, int width) {
		spinner.setMaximumSize(new Dimension(width, ROW_HEIGHT));
		spinner.setPreferredSize(new Dimension(width, ROW_HEIGHT));
		return spinner;
	}

	private static void checkBox(JCheckBox box, String tooltip) {
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setForeground(Color.LIGHT_GRAY);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
		box.setToolTipText(tooltip);
	}

	private static JPanel sideBySide(ModelPreview left, ModelPreview right) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, left.getPreferredSize().height));
		panel.add(left);
		panel.add(Box.createHorizontalStrut(4));
		panel.add(right);
		panel.add(Box.createHorizontalGlue());
		return panel;
	}

	private static JPanel row() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
		return panel;
	}

	private static JButton button(String text, String tooltip, Runnable action) {
		JButton button = new JButton(text);
		button.setToolTipText(tooltip);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		button.addActionListener(e -> action.run());
		return button;
	}

	private static JComboBox<String> dropdown(JComboBox<String> combo, String tooltip, Runnable action) {
		combo.setFont(FontManager.getRunescapeSmallFont());
		combo.setAlignmentX(Component.LEFT_ALIGNMENT);
		combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
		combo.setToolTipText(tooltip);
		combo.addActionListener(e -> action.run());
		return combo;
	}

	private static JSpinner spinner(int value, int min, int max) {
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
		var editor = new JSpinner.NumberEditor(spinner, "#");
		editor.getTextField().setHorizontalAlignment(JTextField.LEFT);
		spinner.setEditor(editor);

		spinner.setMinimumSize(new Dimension(44, ROW_HEIGHT));
		spinner.setPreferredSize(new Dimension(60, ROW_HEIGHT));
		spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
		return spinner;
	}

	private void toggleMap() {
		if (mapHost != null && mapHost.isOpen()) {
			mapHost.close();
			return;
		}

		if (mapHost == null) {
			mapHost = new MapHost(
				tools.client(),
				region -> regionIds.setText(String.valueOf(region)),
				region -> {
					regionIds.setText(String.valueOf(region));
					load();
				},
				this::coverageOf
			);
			mapHost.setOnAdded(this::toggleRegion);
			mapHost.setOnCleared(this::clearPickedRegions);
		}

		int[] wanted = wantedRegions();
		mapHost.setListed(wanted);
		mapHost.open(wanted.length == 0 ? LUMBRIDGE : wanted[0]);
	}

	@Nullable
	private int[] coverageOf(int region) {
		// Picked regions are laid out side by side rather than where they sit in the world, and nothing is
		// loaded around them, so the only true thing to show is which ones are picked
		if (region < 0 || wantedRegions().length > 1)
			return null;
		return SceneBuilder.sceneTiles(
			worldXOf(region), worldYOf(region), (Integer) expandedChunks.getValue());
	}

	public void closeMap() {
		if (mapHost != null)
			mapHost.close();
	}

	private static BufferedImage createMapIcon() {
		BufferedImage image = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		var g = image.createGraphics();

		g.setColor(new Color(0xC8, 0xC8, 0xC8));
		g.fillRect(1, 1, 12, 12);
		g.setColor(new Color(0xFF, 0xA0, 0x30));
		g.fillRect(5, 5, 4, 4);

		g.setColor(new Color(0x38, 0x38, 0x38));
		g.drawRect(1, 1, 12, 12);
		g.drawLine(5, 2, 5, 12);
		g.drawLine(9, 2, 9, 12);
		g.drawLine(2, 5, 12, 5);
		g.drawLine(2, 9, 12, 9);

		g.dispose();
		return image;
	}

	public static BufferedImage createIcon() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		var g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0xC0, 0xC0, 0xC0));
		g.fillOval(3, 1, 10, 10);
		g.fillPolygon(new int[] { 5, 11, 8 }, new int[] { 9, 9, 15 }, 3);
		g.setColor(ColorScheme.DARK_GRAY_COLOR);
		g.fillOval(6, 4, 4, 4);
		g.dispose();
		return image;
	}

	private static final class VariantRenderer extends DefaultListCellRenderer {
		@Override
		public Component getListCellRendererComponent(
			JList<?> list, Object value, int index, boolean selected, boolean focused
		) {
			super.getListCellRendererComponent(list, value, index, selected, focused);
			if (!(value instanceof VariantObject))
				return this;

			var object = (VariantObject) value;
			setText(object.label() + (object.animated ? "  [anim]" : ""));
			setToolTipText(object.variantIds.length + " forms to choose between");
			setFont(FontManager.getRunescapeSmallFont());
			if (!selected)
				setForeground(Color.LIGHT_GRAY);
			return this;
		}
	}
}
