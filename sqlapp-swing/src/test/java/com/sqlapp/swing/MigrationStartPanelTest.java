/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Component;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MigrationStartPanelTest {
	@Test
	void installsAsEntryAndRoutesEveryTaskWithoutReplacingToolInstancesOrInputs() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var tabs = new JTabbedPane();
			final var targets = new EnumMap<MigrationStartPanel.Destination, Component>(
					MigrationStartPanel.Destination.class);
			final var inputs = new java.util.ArrayList<JTextField>();
			for (var destination : MigrationStartPanel.Destination.values()) {
				final var tool = new JPanel();
				final var input = new JTextField("Unsaved " + destination);
				inputs.add(input);
				tool.add(input);
				tabs.addTab(destination.name(), tool);
				targets.put(destination, tool);
			}
			final var start = MigrationStartPanel.install(tabs, targets);
			assertSame(start, tabs.getSelectedComponent());
			assertEquals("Start", tabs.getTitleAt(0));
			assertFalse(start.tools.isVisible());
			start.more.doClick(0);
			assertTrue(start.tools.isVisible());
			for (var destination : MigrationStartPanel.Destination.values()) {
				tabs.setSelectedComponent(start);
				start.buttons.get(destination).doClick(0);
				assertSame(targets.get(destination), tabs.getSelectedComponent());
			}
			assertEquals(targets.size() + 1, tabs.getTabCount());
			for (var input : inputs)
				assertTrue(input.getText().startsWith("Unsaved "));
			start.more.doClick(0);
			assertFalse(start.tools.isVisible());
		});
	}

	@Test
	void constructionAndToolDisclosureDoNotRunNavigationActions() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var calls = new AtomicInteger();
			final var start = new MigrationStartPanel(destination -> calls.incrementAndGet());
			assertEquals(0, calls.get());
			start.more.doClick(0);
			start.more.doClick(0);
			assertEquals(0, calls.get());
			start.buttons.get(MigrationStartPanel.Destination.DATA).doClick(0);
			assertEquals(1, calls.get());
		});
	}

	@Test
	void returningThroughStartPreservesUnsavedSqlAndYamlAndUndoHistory() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var sql = new SqlEditorPanel(path -> {
				throw new AssertionError("Navigation must not hand off SQL");
			});
			final var yaml = new JobConfigurationEditorPanel(snapshot -> {
				throw new AssertionError("Navigation must not hand off a job");
			});
			sql.text.append("SELECT 1;");
			yaml.text.append("jobId: unsaved\n");
			final var tabs = new JTabbedPane();
			final var targets = new EnumMap<MigrationStartPanel.Destination, Component>(
					MigrationStartPanel.Destination.class);
			for (var destination : MigrationStartPanel.Destination.values()) {
				final Component tool = destination == MigrationStartPanel.Destination.SQL_EDITOR ? sql
						: destination == MigrationStartPanel.Destination.JOB_EDITOR ? yaml : new JPanel();
				targets.put(destination, tool);
				tabs.addTab(destination.name(), tool);
			}
			final var start = MigrationStartPanel.install(tabs, targets);
			start.more.doClick(0);
			start.buttons.get(MigrationStartPanel.Destination.SQL_EDITOR).doClick(0);
			tabs.setSelectedComponent(start);
			start.buttons.get(MigrationStartPanel.Destination.JOB_EDITOR).doClick(0);
			assertEquals("SELECT 1;", sql.text.getText());
			assertEquals("jobId: unsaved\n", yaml.text.getText());
			assertTrue(sql.isDirty());
			assertTrue(yaml.isDirty());
			assertTrue(sql.navigation.history.undo.isEnabled());
			assertTrue(yaml.navigation.history.undo.isEnabled());
		});
	}

	@Test
	void missingDestinationFailsBeforeChangingExistingTabs() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var tabs = new JTabbedPane();
			final var existing = new JPanel();
			tabs.addTab("Existing", existing);
			assertThrows(IllegalArgumentException.class, () -> MigrationStartPanel.install(tabs, java.util.Map.of()));
			assertEquals(1, tabs.getTabCount());
			assertSame(existing, tabs.getSelectedComponent());
		});
	}
}
