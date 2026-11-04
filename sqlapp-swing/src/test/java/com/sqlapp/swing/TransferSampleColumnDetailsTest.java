/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;

class TransferSampleColumnDetailsTest {
	private TransferFileImportService.Preview preview() {
		var table = new TransferFilePackageIO.TableFile("task", null, "PUBLIC", "元テーブル", List.of("元ID"), "table-1.csv",
				0, 0, "sha256:" + "a".repeat(64));
		return new TransferFileImportService.Preview(
				new TransferFilePackageIO.Snapshot(Path.of("absent.json"), "hash", null), table, List.of("INT"),
				List.of(), 20, false);
	}

	@Test
	void mappedDetailsRetainSourceConstraintsAndRequestedNamesWithoutInventingTargetConstraints() {
		var mapped = new TransferFileImportService.PackageTable("task", null, "PUBLIC", "元テーブル", "Target",
				"table-1.csv", 0, false, false,
				List.of(new TransferFileImportService.PackageColumn("元ID", "TARGET_ID", "INTEGER", true, true)));
		var text = TransferSampleColumnDetails.text(preview(), mapped, 1);
		assertTrue(text.contains("Source column: 元ID"));
		assertTrue(text.contains("Source Schema type: INTEGER"));
		assertTrue(text.contains("Source NOT NULL: true"));
		assertTrue(text.contains("Source primary key: true"));
		assertTrue(text.contains("Requested target column: TARGET_ID"));
		assertTrue(text.contains("require target Review"));
	}

	@Test
	void standaloneDetailsDoNotGuessMissingMetadataAndRowNumberCannotBeInspected() {
		var text = TransferSampleColumnDetails.text(preview(), null, 1);
		assertTrue(text.contains("Source value type: INT"));
		assertTrue(text.contains("unavailable in this standalone preview"));
		assertFalse(text.contains("Source NOT NULL: false"));
		assertThrows(IllegalArgumentException.class, () -> TransferSampleColumnDetails.text(preview(), null, 0));
		assertThrows(IllegalArgumentException.class, () -> TransferSampleColumnDetails.text(preview(), null, 2));
	}
}
