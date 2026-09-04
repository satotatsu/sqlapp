/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertPlan;

/** Selects the columns actually written by a migration for default verification. */
final class BulkMigrationVerificationColumns {
	private BulkMigrationVerificationColumns() {
	}

	static List<String> resolve(final Table table, final BulkMigrationMode mode,
			final BulkOption bulkOption, final BulkUpsertOption upsertOption) {
		Objects.requireNonNull(table, "table");
		Objects.requireNonNull(mode, "mode");
		if (mode == BulkMigrationMode.UPSERT) {
			return BulkUpsertPlan.resolve(table, upsertOption).getStagingColumns().stream()
					.map(column -> column.getName()).toList();
		}
		final BulkOption option = bulkOption == null ? BulkOption.defaults() : bulkOption;
		return table.getColumns().stream()
				.filter(column -> !column.isHidden())
				.filter(column -> column.getFormula() == null || column.getFormula().isEmpty())
				.filter(column -> !column.isIdentity() || option.isKeepIdentity())
				.map(column -> column.getName()).toList();
	}
}
