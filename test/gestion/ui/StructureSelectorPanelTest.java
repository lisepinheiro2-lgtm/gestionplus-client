package gestion.ui;

import static gestion.ui.WindowTestSupport.named;
import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextArea;

import org.junit.jupiter.api.Test;

import gestion.app.StructureSelectionState;
import gestion.app.StructureSelectionState.Phase;
import gestion.net.StructureSummary;

class StructureSelectorPanelTest {

	private static final StructureSummary FIRST = new StructureSummary(UUID.randomUUID(), "Fictitious North");
	private static final StructureSummary SECOND = new StructureSummary(UUID.randomUUID(), "Fictitious South");

	@Test
	void noAccessIsExplicitAndDoesNotOfferAUsableChoice() throws Exception {
		onEdt(() -> {
			var panel = new StructureSelectorPanel();
			panel.showSelection(new StructureSelectionState(Phase.EMPTY, List.of(), null, ""));
			assertEquals("Vous n’avez accès à aucune structure.", message(panel).getText());
			assertFalse(choices(panel).isEnabled());
			assertEquals(0, choices(panel).getItemCount());
			assertTrue(refresh(panel).isEnabled());
		});
	}

	@Test
	void theOnlyStructureIsClearlyDisplayedWithoutEmittingAnArtificialUserSelection() throws Exception {
		onEdt(() -> {
			var panel = new StructureSelectorPanel();
			AtomicInteger selections = new AtomicInteger();
			panel.setActions(id -> selections.incrementAndGet(), () -> {
			});
			panel.showSelection(new StructureSelectionState(Phase.READY, List.of(FIRST), FIRST.structureID(), ""));
			assertEquals(FIRST, choices(panel).getSelectedItem());
			assertFalse(choices(panel).isEnabled());
			assertEquals("Structure active : Fictitious North", message(panel).getText());
			assertEquals(0, selections.get());
		});
	}

	@Test
	void severalStructuresRequireAChoiceAndSupportTheKeyboardAndRefreshAction() throws Exception {
		onEdt(() -> {
			var panel = new StructureSelectorPanel();
			List<UUID> selections = new ArrayList<>();
			AtomicInteger refreshes = new AtomicInteger();
			panel.setActions(selections::add, refreshes::incrementAndGet);
			panel.showSelection(new StructureSelectionState(Phase.READY, List.of(FIRST, SECOND), null, ""));
			JComboBox<?> choices = choices(panel);
			assertTrue(choices.isEnabled());
			assertTrue(choices.isFocusable());
			assertNull(choices.getSelectedItem());
			assertTrue(selections.isEmpty());
			choices.setSelectedItem(SECOND);
			assertEquals(List.of(SECOND.structureID()), selections);
			assertTrue(refresh(panel).getMnemonic() != 0);
			refresh(panel).doClick(0);
			assertEquals(1, refreshes.get());
		});
	}

	@Test
	void loadingAndSuspensionRemoveThePreviousDisplayAndChoice() throws Exception {
		onEdt(() -> {
			var panel = new StructureSelectorPanel();
			panel.showSelection(new StructureSelectionState(Phase.READY, List.of(FIRST), FIRST.structureID(), ""));
			panel.showSelection(new StructureSelectionState(Phase.LOADING, List.of(), null, "Chargement…"));
			assertEquals(0, choices(panel).getItemCount());
			assertFalse(refresh(panel).isEnabled());
			assertFalse(message(panel).getText().contains(FIRST.structureName()));
			panel.showSelection(new StructureSelectionState(Phase.SUSPENDED, List.of(), null, ""));
			assertFalse(choices(panel).isEnabled());
			assertFalse(refresh(panel).isEnabled());
		});
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void serverNamesAreRenderedAsPlainTextAndRemainReadableInBothThemes() throws Exception {
		onEdt(() -> {
			var panel = new StructureSelectorPanel();
			var untrusted = new StructureSummary(UUID.randomUUID(), "<html><img src='https://example.invalid/x'>");
			panel.showSelection(new StructureSelectionState(Phase.READY, List.of(untrusted),
					untrusted.structureID(), ""));
			JComboBox choices = choices(panel);
			JLabel rendered = (JLabel) choices.getRenderer().getListCellRendererComponent(new JList<>(),
					untrusted, 0, false, false);
			assertEquals(untrusted.structureName(), rendered.getText());
			assertNull(rendered.getClientProperty("html"));
			panel.applyAppTheme(false);
			Color light = message(panel).getForeground();
			assertNotEquals(panel.getBackground(), light);
			panel.applyAppTheme(true);
			assertNotEquals(light, message(panel).getForeground());
			assertNotEquals(panel.getBackground(), message(panel).getForeground());
		});
	}

	private static JComboBox<?> choices(StructureSelectorPanel panel) {
		return named(panel, JComboBox.class, "Structure de travail");
	}

	private static JButton refresh(StructureSelectorPanel panel) {
		return named(panel, JButton.class, "Actualiser les structures accessibles");
	}

	private static JTextArea message(StructureSelectorPanel panel) {
		return named(panel, JTextArea.class, "État de la structure");
	}
}
