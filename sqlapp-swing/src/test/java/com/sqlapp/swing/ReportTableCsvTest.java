/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportTableCsvTest {
	@TempDir Path directory;

	@Test
	void formatsFilteredSortedAndReorderedViewWithQuotedMultilineUnicodeFields() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var table = new JTable(new DefaultTableModel(new Object[][] {
					{ "z", "excluded" }, { "b", "日,\"本\"\r\n語" }, { "a", "" } }, new Object[] { "Task", "Note" }));
			table.setAutoCreateRowSorter(true);
			((javax.swing.table.TableRowSorter<?>) table.getRowSorter()).setRowFilter(RowFilter.regexFilter("[ab]", 0));
			table.getRowSorter().toggleSortOrder(0);
			table.moveColumn(1, 0);
			assertEquals("\"Note\",\"Task\"\r\n\"\",\"a\"\r\n\"日,\"\"本\"\"\r\n語\",\"b\"\r\n", ReportTableCsv.format(table));
			assertEquals(3, table.getModel().getRowCount());
		});
	}

	@Test
	void rejectsOversizedSnapshotBeforeWriting() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var table = new JTable(new Object[][] { { "あ".repeat(ReportTableCsv.MAX_BYTES / 3) } }, new String[] { "Task" });
			assertTrue(assertThrows(IllegalArgumentException.class, () -> ReportTableCsv.format(table))
					.getMessage().contains("16 MiB"));
		});
	}

	@Test
	void savesCapturedViewAndRetriesAfterInvalidDestinationWithoutOverwritingInput() throws Exception {
		var input = Files.writeString(directory.resolve("original.json"), "original");
		var failure = new CountDownLatch(1);
		var saved = new CountDownLatch(1);
		var openFailed = new CountDownLatch(1);
		var opened = new CountDownLatch(1);
		var openAttempts = new AtomicInteger();
		var enabled = new AtomicBoolean(true);
		var openedFolder = new AtomicReference<Path>();
		var actionsRef = new AtomicReference<ReportTableCsvSaveActions>();
		var tableRef = new AtomicReference<JTable>();
		SwingUtilities.invokeAndWait(() -> {
			var table = new JTable(new Object[][] { { "captured" } }, new String[] { "Task" });
			tableRef.set(table);
			var actions = new ReportTableCsvSaveActions(table, enabled::get, folder -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				if (openAttempts.incrementAndGet() == 1) throw new java.io.IOException("unavailable");
				openedFolder.set(folder);
			});
			actionsRef.set(actions);
			actions.status.addPropertyChangeListener("text", event -> {
				if (event.getNewValue().toString().startsWith("Could not save")) failure.countDown();
				if (event.getNewValue().toString().startsWith("Saved CSV snapshot")) saved.countDown();
				if (event.getNewValue().toString().startsWith("Could not open folder")) openFailed.countDown();
				if ("Opened saved CSV folder".equals(event.getNewValue())) opened.countDown();
			});
			assertFalse(actions.openFolder.isEnabled());
			actions.saveTo(input);
		});
		assertTrue(failure.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			var actions = actionsRef.get();
			assertTrue(actions.save.isEnabled());
			actions.saveTo(directory);
			tableRef.get().setValueAt("changed after capture", 0, 0);
			assertFalse(actions.save.isEnabled());
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		var output = new AtomicReference<Path>();
		SwingUtilities.invokeAndWait(() -> {
			var actions = actionsRef.get();
			output.set(Path.of(actions.status.getToolTipText()));
			assertEquals(0, openAttempts.get());
			assertTrue(actions.openFolder.isEnabled());
			enabled.set(false);
			actions.refresh();
			assertFalse(actions.openFolder.isEnabled());
			actions.openFolder.setEnabled(true);
			actions.openFolder.doClick(0);
			assertEquals(0, openAttempts.get());
			enabled.set(true);
			actions.refresh();
			actions.openFolder.doClick(0);
			assertFalse(actions.openFolder.isEnabled());
			assertFalse(actions.save.isEnabled());
		});
		assertTrue(openFailed.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(actionsRef.get().openFolder.isEnabled());
			assertEquals(output.get().toString(), actionsRef.get().status.getToolTipText());
			actionsRef.get().openFolder.doClick(0);
		});
		assertTrue(opened.await(5, TimeUnit.SECONDS));
		assertEquals(output.get().getParent(), openedFolder.get());
		assertEquals("\"Task\"\r\n\"captured\"\r\n", Files.readString(output.get()));
		assertEquals("original", Files.readString(input));
	}
}
