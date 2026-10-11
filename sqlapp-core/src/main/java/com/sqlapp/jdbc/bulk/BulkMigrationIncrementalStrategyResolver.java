/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;

/** Resolves implemented strategies through the existing dialect bulk SPIs. */
public final class BulkMigrationIncrementalStrategyResolver {

	private BulkMigrationIncrementalStrategyResolver() {
	}

	public static BulkMigrationMode resolve(final Dialect dialect,
			final BulkMigrationIncrementalStrategy strategy) {
		Objects.requireNonNull(dialect, "dialect");
		Objects.requireNonNull(strategy, "strategy");
		if (!strategy.isImplemented()) {
			throw new IllegalArgumentException("Incremental strategy " + strategy
					+ " is not implemented for " + dialect.getProductName());
		}
		if (strategy == BulkMigrationIncrementalStrategy.APPEND) {
			BulkInsertResolver.resolve(dialect);
		} else {
			BulkUpsertResolver.resolve(dialect);
		}
		return strategy.mode();
	}
}
