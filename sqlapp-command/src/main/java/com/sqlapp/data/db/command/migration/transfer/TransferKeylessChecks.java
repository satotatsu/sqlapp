/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import com.sqlapp.data.schemas.Table;

/** Shared classification of tables requiring non-resumable full-scan INSERT. */
public final class TransferKeylessChecks {
	public static final String CODE = "[KEYLESS_INSERT]";
	public static final String GENERATED_TARGET_KEY = "[TARGET_GENERATED_PK]";
	public static final String GENERATED_COLUMN = "SQLAPP_ROW_ID";

	private TransferKeylessChecks() {
	}

	public static boolean requiresFullScan(Table table) {
		var key = table.getPrimaryKeyConstraint();
		return key == null || key.getColumns().isEmpty()
				|| key.getColumns().stream().anyMatch(column -> table.getColumns().get(column.getName()) == null
						|| !table.getColumns().get(column.getName()).isNotNull());
	}
}
