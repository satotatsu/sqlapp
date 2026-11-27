/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.datatype.DataType;

class TransferNameRulesSchemaTest {
	@TempDir
	Path directory;

	private Path schema() throws Exception {
		var schema = new Schema("PUBLIC");
		var table = new Table("ITEMS");
		table.getColumns().add(new Column("A").setDataType(DataType.INT));
		table.getColumns().add(new Column("B").setDataType(DataType.INT));
		schema.getTables().add(table);
		var file = directory.resolve("schema.xml");
		schema.writeXml(file.toFile());
		return file;
	}

	private TransferNameMappingPanel editor(String second) {
		var editor = new TransferNameMappingPanel();
		editor.tables.addRow(new String[] { "PUBLIC.ITEMS", null });
		editor.columns.addRow(new String[] { "PUBLIC.ITEMS", "A", "First Name" });
		editor.columns.addRow(new String[] { "PUBLIC.ITEMS", "B", second });
		editor.columnGrid.setRowSelectionInterval(0, 0);
		return editor;
	}

	private void awaitIdle(TransferNameRulesPanel panel) throws Exception {
		var idle = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			var timer = new javax.swing.Timer(10, event -> {
				if (!panel.isBusy()) {
					((javax.swing.Timer) event.getSource()).stop();
					idle.countDown();
				}
			});
			timer.start();
		});
		assertTrue(idle.await(8, TimeUnit.SECONDS));
	}

	@Test
	void candidateMatchesAppliedMappingAndPreservesHiddenOverridesAndTableDefaults() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = editor("KEEP");
			var original = editor.configuration();
			editor.columnTools.filter.setText("First");
			var changes = editor.columnTools
					.previewRules(new TransferNameRules(TransferNameRules.NameCase.LOWER, true));
			var candidate = editor.proposedConfiguration(editor.columnTools, changes);
			assertEquals(original, editor.configuration());
			assertEquals("KEEP", candidate.tables().get("PUBLIC.ITEMS").columns().get("B"));
			editor.columnTools.applyRules(changes);
			assertEquals(candidate, editor.configuration());
			editor.tableGrid.setRowSelectionInterval(0, 0);
			var tableChanges = editor.tableTools
					.previewRules(new TransferNameRules(TransferNameRules.NameCase.LOWER, false));
			var tableCandidate = editor.proposedConfiguration(editor.tableTools, tableChanges);
			editor.tableTools.applyRules(tableChanges);
			assertEquals(tableCandidate, editor.configuration());
			editor.tableTools.undoRules();
			assertNull(editor.configuration().tables().get("PUBLIC.ITEMS").targetTable());
			editor.columns.setValueAt("FIRST_NAME", 0, 1);
			editor.columnGrid.setRowSelectionInterval(0, 0);
			var sameName = editor.columnTools
					.previewRules(new TransferNameRules(TransferNameRules.NameCase.UPPER, false));
			var concise = editor.proposedConfiguration(editor.columnTools, sameName);
			assertFalse(concise.tables().get("PUBLIC.ITEMS").columns().containsKey("FIRST_NAME"));
			editor.columnTools.applyRules(sameName);
			assertEquals(concise, editor.configuration());
		});
	}

	@Test
	void detectsCollisionsWithUnselectedColumnsBeforeApplyingAndAllowsDraftRefinement() throws Exception {
		var file = schema();
		var panel = new AtomicReference<TransferNameRulesPanel>();
		var editor = new AtomicReference<TransferNameMappingPanel>();
		SwingUtilities.invokeAndWait(() -> {
			var e = editor("first_name");
			editor.set(e);
			var p = new TransferNameRulesPanel(e.columnTools, () -> {
			}, changes -> {
				var candidate = e.proposedConfiguration(e.columnTools, changes);
				return () -> {
					assertFalse(SwingUtilities.isEventDispatchThread());
					return new TransferNameMappingInspectionService().inspect(file, List.of("PUBLIC.ITEMS"), candidate);
				};
			});
			panel.set(p);
			p.letterCase.setSelectedItem(TransferNameRules.NameCase.LOWER);
			p.spaces.doClick(0);
			p.preview.doClick(0);
			p.checkSchema.doClick(0);
		});
		awaitIdle(panel.get());
		SwingUtilities.invokeAndWait(() -> {
			var p = panel.get();
			assertTrue(p.schemaCheck.evidence.getText().contains("collide"));
			assertEquals("First Name", editor.get().columns.getValueAt(0, 2));
			assertTrue(p.apply.isEnabled());
			p.apply.doClick(0);
			assertEquals("first_name", editor.get().columns.getValueAt(0, 2));
		});
	}

	@Test
	void checksValidProposedNamesWithoutChangingTheEditorAndRestoresControls() throws Exception {
		var file = schema();
		var panel = new AtomicReference<TransferNameRulesPanel>();
		var editor = new AtomicReference<TransferNameMappingPanel>();
		SwingUtilities.invokeAndWait(() -> {
			var e = editor("OTHER");
			editor.set(e);
			var p = new TransferNameRulesPanel(e.columnTools, () -> {
			}, changes -> {
				var candidate = e.proposedConfiguration(e.columnTools, changes);
				return () -> new TransferNameMappingInspectionService().inspect(file, List.of("PUBLIC.ITEMS"),
						candidate);
			});
			panel.set(p);
			p.letterCase.setSelectedItem(TransferNameRules.NameCase.LOWER);
			p.spaces.doClick(0);
			p.preview.doClick(0);
			p.checkSchema.doClick(0);
		});
		awaitIdle(panel.get());
		SwingUtilities.invokeAndWait(() -> {
			var p = panel.get();
			assertEquals(2, p.schemaCheck.model.getRowCount());
			assertEquals("first_name", p.schemaCheck.model.getValueAt(0, 3));
			assertEquals("First Name", editor.get().columns.getValueAt(0, 2));
			assertTrue(p.apply.isEnabled());
			assertTrue(p.cancel.isEnabled());
			assertFalse(p.isBusy());
		});
	}

	@Test
	void busyCloseIsRefusedAndLateResultsCannotReplaceNewerRuleChoices() throws Exception {
		var release = new CountDownLatch(1);
		var started = new CountDownLatch(1);
		var panel = new AtomicReference<TransferNameRulesPanel>();
		var closed = new AtomicBoolean();
		try {
			SwingUtilities.invokeAndWait(() -> {
				var e = editor("OTHER");
				var p = new TransferNameRulesPanel(e.columnTools, () -> closed.set(true), changes -> () -> {
					started.countDown();
					assertTrue(release.await(6, TimeUnit.SECONDS));
					return new TransferNameMappingInspectionService.Result(Path.of("schema.xml"), "old-source",
							"old-mapping", List.of(), List.of());
				});
				panel.set(p);
				p.letterCase.setSelectedItem(TransferNameRules.NameCase.LOWER);
				p.preview.doClick(0);
				p.checkSchema.doClick(0);
				assertTrue(p.isBusy());
				p.requestClose();
				assertFalse(closed.get());
				assertFalse(p.apply.isEnabled());
				p.letterCase.setSelectedItem(TransferNameRules.NameCase.UPPER);
			});
			assertTrue(started.await(5, TimeUnit.SECONDS));
		} finally {
			release.countDown();
		}
		awaitIdle(panel.get());
		SwingUtilities.invokeAndWait(() -> {
			var p = panel.get();
			assertFalse(p.schemaCheck.evidence.getText().contains("old-source"));
			assertFalse(p.apply.isEnabled());
			assertTrue(p.cancel.isEnabled());
			p.requestClose();
			assertTrue(closed.get());
		});
	}
}
