/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService.*;
import com.sqlapp.data.db.command.migration.transfer.TransferMappingPreviewReportIO;

class TransferPreviewReportCaptureTest {
	private Result result() {
		return new Result(Path.of("source.xml"), "source", "mapping",
				List.of(new Table("PUBLIC.A", "TARGET",
						List.of(new Column("Z", "Z_COPY", "INT", false, true, "INT", null),
								new Column("B", "B_COPY", "OTHER", false, true, null, "Preview unsupported")),
						"A")),
				List.of(), "Builder");
	}

	@Test
	void capturesExactFilteredSortedIdentitiesWithoutChangingTheFullSnapshot() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			var result = result();
			panel.showResult(result);
			panel.details.table.getRowSorter().toggleSortOrder(1);
			panel.details.table.moveColumn(0, 3);
			assertEquals(
					List.of(new TransferMappingPreviewReportIO.ColumnRef("PUBLIC.A", "B"),
							new TransferMappingPreviewReportIO.ColumnRef("PUBLIC.A", "Z")),
					panel.visibleReportColumns());
			panel.previewIssuesOnly.doClick(0);
			var refs = panel.visibleReportColumns();
			assertEquals(1, refs.size());
			assertEquals("B", refs.getFirst().sourceColumn());
			assertSame(result, panel.reportSnapshot());
			assertEquals(2, panel.reportSnapshot().tables().getFirst().columns().size());
			assertThrows(UnsupportedOperationException.class, refs::clear);
			String html = new TransferMappingPreviewReportIO().render(panel.reportSnapshot(), refs);
			assertTrue(html.contains("1 / 2 columns included"));
			assertFalse(html.contains("Z_COPY"));
		});
	}

	@Test
	void invalidationNotifiesTheOwnerAndCannotExportOldRows() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			var changes = new java.util.concurrent.atomic.AtomicInteger();
			panel.setCaptureObserver(changes::incrementAndGet);
			assertFalse(panel.hasCapturedResult());
			assertThrows(IllegalArgumentException.class, panel::reportSnapshot);
			panel.showResult(result());
			assertTrue(panel.hasCapturedResult());
			assertEquals(1, changes.get());
			panel.changed();
			assertFalse(panel.hasCapturedResult());
			assertEquals(2, changes.get());
			assertThrows(IllegalArgumentException.class, panel::visibleReportColumns);
			panel.showResult(result());
			assertTrue(panel.hasCapturedResult());
			assertEquals(3, changes.get());
		});
	}
}
