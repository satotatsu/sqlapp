/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import com.sqlapp.data.db.command.migration.transfer.TransferFileImportService;

/**
 * Recorded source metadata, never inferred target constraints or sample values.
 */
final class TransferSampleColumnDetails {
	private TransferSampleColumnDetails() {
	}

	static String text(TransferFileImportService.Preview preview, TransferFileImportService.PackageTable mapped,
			int modelColumn) {
		int index = modelColumn - 1;
		if (index < 0 || index >= preview.table().columns().size())
			throw new IllegalArgumentException("Choose a packaged data column, not the sample row number.");
		var text = new StringBuilder("Source catalog: ")
				.append(java.util.Objects.toString(preview.table().catalog(), "not recorded"))
				.append("\nSource schema: ")
				.append(java.util.Objects.toString(preview.table().schema(), "not recorded")).append("\nSource table: ")
				.append(preview.table().table()).append("\nSource column: ")
				.append(preview.table().columns().get(index)).append("\nSource value type: ")
				.append(preview.sourceTypes().get(index));
		if (mapped == null) {
			text.append(
					"\n\nRequested target names and source constraints are unavailable in this standalone preview. Open the preview from Package tables to include the recorded correspondence.");
		} else {
			var column = mapped.columns().get(index);
			text.append("\nSource Schema type: ").append(column.sourceType()).append("\nSource NOT NULL: ")
					.append(column.notNull()).append("\nSource primary key: ").append(column.primaryKey())
					.append("\n\nRequested target table: ").append(mapped.targetTable())
					.append("\nRequested target column: ").append(column.targetName());
		}
		return text.append(
				"\n\nRecorded source metadata and requested names only. Target types, constraints and compatibility require target Review. Target-only generated PK columns are not packaged fields.")
				.toString();
	}
}
