/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Vendor-neutral incremental write strategy recorded in migration plans. */
public enum BulkMigrationIncrementalStrategy {
	APPEND(BulkMigrationMode.INSERT, true),
	MERGE(BulkMigrationMode.UPSERT, true),
	DELETE_INSERT(null, false),
	INSERT_OVERWRITE(null, false),
	MICROBATCH(null, false),
	FULL_REPLACE(null, false);

	private final BulkMigrationMode mode;
	private final boolean implemented;

	BulkMigrationIncrementalStrategy(final BulkMigrationMode mode, final boolean implemented) {
		this.mode = mode;
		this.implemented = implemented;
	}

	public BulkMigrationMode mode() {
		return mode;
	}

	public boolean isImplemented() {
		return implemented;
	}

	public static BulkMigrationIncrementalStrategy fromMode(final BulkMigrationMode mode) {
		return switch (mode) {
		case INSERT -> APPEND;
		case UPSERT -> MERGE;
		};
	}
}
