package gestion.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import gestion.app.StructureSelectionState;
import gestion.app.StructureSelectionState.Phase;
import gestion.net.StructureSummary;

public final class StructureSelectorPanel extends JPanel implements AppThemeAware {

	private final JComboBox<StructureSummary> choices = new JComboBox<>();
	private final JButton refresh = new JButton("Actualiser");
	private final JLabel label = new JLabel("Structure");
	private final JTextArea message = new JTextArea(2, 36);
	private Consumer<UUID> onSelection = id -> {
	};
	private Runnable onRefresh = () -> {
	};
	private boolean updating;

	public StructureSelectorPanel() {
		super(new BorderLayout(0, 8));
		label.setDisplayedMnemonic(KeyEvent.VK_S);
		label.setLabelFor(choices);
		choices.getAccessibleContext().setAccessibleName("Structure de travail");
		choices.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean focus) {
				JLabel cell = (JLabel) super.getListCellRendererComponent(list, "", index, selected, focus);
				cell.putClientProperty("html.disable", Boolean.TRUE);
				cell.setText(value instanceof StructureSummary structure ? structure.structureName()
						: "Choisissez une structure…");
				return cell;
			}
		});
		choices.addActionListener(event -> {
			if (!updating) {
				StructureSummary selection = (StructureSummary) choices.getSelectedItem();
				onSelection.accept(selection == null ? null : selection.structureID());
			}
		});
		refresh.setMnemonic(KeyEvent.VK_A);
		refresh.getAccessibleContext().setAccessibleName("Actualiser les structures accessibles");
		refresh.addActionListener(event -> onRefresh.run());
		JPanel row = new JPanel(new BorderLayout(12, 0));
		row.setOpaque(false);
		row.add(label, BorderLayout.WEST);
		row.add(choices, BorderLayout.CENTER);
		row.add(refresh, BorderLayout.EAST);
		message.setEditable(false);
		message.setFocusable(false);
		message.setLineWrap(true);
		message.setWrapStyleWord(true);
		message.setOpaque(false);
		message.getAccessibleContext().setAccessibleName("État de la structure");
		add(row, BorderLayout.NORTH);
		add(message, BorderLayout.CENTER);
		showSelection(new StructureSelectionState(Phase.SUSPENDED, List.of(), null, ""));
	}

	public void setActions(Consumer<UUID> onSelection, Runnable onRefresh) {
		this.onSelection = Objects.requireNonNull(onSelection);
		this.onRefresh = Objects.requireNonNull(onRefresh);
	}

	public void showSelection(StructureSelectionState state) {
		updating = true;
		try {
			choices.removeAllItems();
			if (state.structures().size() > 1) {
				choices.addItem(null);
			}
			for (StructureSummary structure : state.structures()) {
				choices.addItem(structure);
			}
			choices.setSelectedItem(state.structures().stream()
					.filter(item -> item.structureID().equals(state.selectionID())).findFirst().orElse(null));
		} finally {
			updating = false;
		}
		boolean ready = state.phase() == Phase.READY || state.phase() == Phase.VALIDATING;
		choices.setEnabled(ready && state.structures().size() > 1);
		refresh.setEnabled(state.phase() != Phase.SUSPENDED && state.phase() != Phase.LOADING);
		String text = state.message();
		if (text.isEmpty()) {
			text = switch (state.phase()) {
			case EMPTY -> "Vous n’avez accès à aucune structure.";
			case READY -> state.structures().stream()
					.filter(item -> item.structureID().equals(state.selectionID())).findFirst()
					.map(item -> "Structure active : " + item.structureName())
					.orElse("Choisissez une structure pour poursuivre.");
			default -> "";
			};
		}
		message.setText(text);
		message.setCaretPosition(0);
		applyAppTheme(UIUtils.isDarkMode());
		revalidate();
		repaint();
	}

	@Override
	public void applyAppTheme(boolean dark) {
		setBackground(dark ? new Color(33, 42, 56) : Color.WHITE);
		Color foreground = dark ? new Color(215, 225, 237) : new Color(41, 56, 74);
		label.setForeground(foreground);
		message.setForeground(foreground);
		setBorder(BorderFactory.createEmptyBorder(12, 24, 8, 24));
		UIUtils.styleActionButton(refresh, "neutral");
	}
}
