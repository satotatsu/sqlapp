/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.util.EnumMap;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/** Tab grouping, numbered steps and the highlighted next action. */
class WorkflowGuidanceTest {
	@Test
	void nestedTabsSelectTheScreenAndItsGroup() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var tabs = new JTabbedPane();
			final var task = new JPanel();
			final var tools = new JTabbedPane();
			final var editor = new JPanel();
			final var drivers = new JPanel();
			tabs.addTab("Task", task);
			tools.addTab("Editor", editor);
			tools.addTab("Drivers", drivers);
			tabs.addTab("Tools", tools);
			assertTrue(NestedTabs.contains(tabs, drivers));
			assertFalse(NestedTabs.contains(tabs, new JPanel()));
			assertTrue(NestedTabs.select(tabs, drivers));
			assertSame(tools, tabs.getSelectedComponent());
			assertSame(drivers, tools.getSelectedComponent());
			assertTrue(NestedTabs.select(tabs, task));
			assertSame(task, tabs.getSelectedComponent());
			assertFalse(NestedTabs.select(tabs, new JPanel()));
			assertSame(task, tabs.getSelectedComponent());
		});
	}

	@Test
	void startScreenOpensToolsInsideTheToolsGroup() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var tabs = new JTabbedPane();
			final var tools = new JTabbedPane();
			final var targets = new EnumMap<MigrationStartPanel.Destination, Component>(
					MigrationStartPanel.Destination.class);
			for (var destination : MigrationStartPanel.Destination.values()) {
				final var screen = new JPanel();
				targets.put(destination, screen);
				if (destination == MigrationStartPanel.Destination.SQL_EDITOR)
					tools.addTab(destination.name(), screen);
				else
					tabs.addTab(destination.name(), screen);
			}
			tabs.addTab("Tools", tools);
			final var start = MigrationStartPanel.install(tabs, targets);
			start.buttons.get(MigrationStartPanel.Destination.SQL_EDITOR).doClick(0);
			assertSame(tools, tabs.getSelectedComponent());
			assertSame(targets.get(MigrationStartPanel.Destination.SQL_EDITOR), tools.getSelectedComponent());
		});
	}

	@Test
	void directTransferHighlightsTheNextStepInStepOrder() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DirectTransferPanel((action, request, from, to) -> null, prepared -> null);
			assertTrue(PrimaryAction.isPrimary(panel.create), "no job yet: create one");
			assertFalse(PrimaryAction.isPrimary(panel.review));
			panel.job.setText("transfer.yaml");
			assertTrue(PrimaryAction.isPrimary(panel.review), "a job is chosen: review it");
			assertFalse(PrimaryAction.isPrimary(panel.create));
			assertFalse(PrimaryAction.isPrimary(panel.execute));
		});
	}

	@Test
	void fileTransferStepsFollowTheExportOrImportChoice() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel(
					(action, input, workspace, spec, password, schema, reviewed) -> null, prepared -> null);
			assertTrue(PrimaryAction.isPrimary(panel.create));
			assertEquals("1. Connect the source", titleOfStep(panel, 0));
			assertEquals("2. Job", titleOfStep(panel, 1));
			panel.job.setText("transfer.yaml");
			assertTrue(PrimaryAction.isPrimary(panel.export));
			panel.step.setSelectedIndex(1);
			assertEquals("1. Choose the package", titleOfStep(panel, 0));
			assertEquals("2. Connect the target", titleOfStep(panel, 1));
			assertTrue(PrimaryAction.isPrimary(panel.review), "import: review before executing");
			assertFalse(PrimaryAction.isPrimary(panel.export));
		});
	}

	/** Steps inside the vertical step list, in display order. */
	private static String titleOfStep(final java.awt.Container root, final int index) {
		final var found = new java.util.ArrayList<StepSection>();
		collect(root, found);
		return found.stream()
				.filter(section -> section.getParent() != null
						&& section.getParent().getLayout() instanceof javax.swing.BoxLayout)
				.toList().get(index).title();
	}

	private static void collect(final java.awt.Container container, final java.util.List<StepSection> found) {
		for (final var child : container.getComponents()) {
			if (child instanceof StepSection section)
				found.add(section);
			if (child instanceof java.awt.Container nested)
				collect(nested, found);
		}
	}
}
