/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.migration.schema.MigrationSqlInspection;

class SqlSplitPreviewPanelTest {
	private static MigrationSqlInspection.Result result() {
		return new MigrationSqlInspection.Result("HSQL", "2.7", "Hsql",
				List.of(new MigrationSqlInspection.Part(1, true, "-- comment"),
						new MigrationSqlInspection.Part(10, false, "SELECT '[a].*'\n-- body token"),
						new MigrationSqlInspection.Part(2, false, "SELECT 2")));
	}

	@Test
	void copiesCorrectParserOutputAfterNumericSortingFilteringAndNavigation() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var copied = new AtomicReference<String>();
			final var panel = new SqlSplitPreviewPanel(copied::set);
			panel.showResult(result());
			panel.copy.doClick();
			assertEquals("-- comment", copied.get());
			panel.parts.getRowSorter().toggleSortOrder(0);
			assertEquals(2, panel.parts.getValueAt(1, 0));
			assertEquals(10, panel.parts.getValueAt(2, 0));
			panel.hideComments.doClick();
			assertEquals("2 / 3 parts", panel.count.getText());
			assertFalse(panel.previous.isEnabled());
			assertTrue(panel.next.isEnabled());
			panel.copy.doClick();
			assertEquals("SELECT 2", copied.get());
			panel.next.doClick();
			assertFalse(panel.next.isEnabled());
			assertTrue(panel.previous.isEnabled());
			panel.copy.doClick();
			assertEquals("SELECT '[a].*'\n-- body token", copied.get());
			assertEquals(copied.get(), panel.sql.getText());
			panel.previous.doClick();
			assertEquals("SELECT 2", panel.sql.getText());
			panel.filter.setText("BODY TOKEN");
			assertEquals(1, panel.parts.getRowCount());
			panel.copy.doClick();
			assertEquals("SELECT '[a].*'\n-- body token", copied.get());
			assertFalse(panel.parts.isCellEditable(0, 0));
			assertFalse(panel.sql.isEditable());
		});
	}

	@Test
	void filterIsLiteralAndNoMatchesOrClearingCannotCopyStalePart() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var copied = new AtomicReference<String>();
			final var panel = new SqlSplitPreviewPanel(copied::set);
			panel.showResult(result());
			panel.filter.setText("[A].*");
			assertEquals(1, panel.parts.getRowCount());
			panel.copy.doClick();
			assertTrue(copied.get().contains("'[a].*'"));
			panel.filter.setText("not present");
			assertEquals("0 / 3 parts", panel.count.getText());
			assertFalse(panel.copy.isEnabled());
			assertFalse(panel.next.isEnabled());
			assertTrue(panel.sql.getText().isEmpty());
			copied.set(null);
			panel.copy.doClick();
			assertNull(copied.get());
			panel.clear();
			assertFalse(panel.hideComments.isSelected());
			assertTrue(panel.filter.getText().isEmpty());
			assertEquals("0 / 0 parts", panel.count.getText());
			assertFalse(panel.copy.isEnabled());
		});
	}

	@Test
	void clipboardFailureIsReportedAndRetryRetainsSamePart() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var calls = new AtomicInteger();
			final var copied = new AtomicReference<String>();
			final var panel = new SqlSplitPreviewPanel(text -> {
				if (calls.getAndIncrement() == 0)
					throw new IllegalStateException("clipboard busy");
				copied.set(text);
			});
			panel.showResult(result());
			panel.copy.doClick();
			assertTrue(panel.copyStatus.getText().contains("Copy failed"));
			assertTrue(panel.copyStatus.getText().contains("clipboard busy"));
			assertEquals("-- comment", panel.sql.getText());
			assertTrue(panel.copy.isEnabled());
			panel.copy.doClick();
			assertEquals("-- comment", copied.get());
			assertTrue(panel.copyStatus.getText().contains("Copied parser output for part 1"));
		});
	}

	@Test
	void emptyResultHasNoNavigationOrCopyTarget() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SqlSplitPreviewPanel(text -> fail("No selected part"));
			panel.showResult(new MigrationSqlInspection.Result("HSQL", "2.7", "Hsql", List.of()));
			assertFalse(panel.copy.isEnabled());
			assertFalse(panel.previous.isEnabled());
			assertFalse(panel.next.isEnabled());
			panel.filter.setText("anything");
			assertTrue(panel.sql.getText().isEmpty());
			assertEquals("0 / 0 parts", panel.count.getText());
		});
	}

	@Test
	void sourceActionUsesSelectedModelPartAndCannotReuseClearedOrFilteredParts() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var located = new AtomicReference<String>();
			final var panel = new SqlSplitPreviewPanel(text -> {
			});
			panel.showResult(result());
			assertFalse(panel.findSource.isEnabled());
			panel.setSourceFinder(located::set);
			assertTrue(panel.findSource.isEnabled());
			panel.parts.getRowSorter().toggleSortOrder(0);
			panel.hideComments.doClick();
			panel.findSource.doClick();
			assertEquals("SELECT 2", located.get());
			panel.next.doClick();
			panel.findSource.doClick();
			assertEquals("SELECT '[a].*'\n-- body token", located.get());
			located.set(null);
			panel.filter.setText("missing");
			assertFalse(panel.findSource.isEnabled());
			panel.findSource.doClick();
			assertNull(located.get());
			panel.clear();
			assertFalse(panel.findSource.isEnabled());
			panel.findSource.doClick();
			assertNull(located.get());
		});
	}
}
