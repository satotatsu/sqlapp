/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import com.sqlapp.data.db.dialect.Dialect;

/** SPI for dialect-owned set-based SCD2 implementations. */
public interface SetBasedMigrationSnapshotProvider {
	boolean supports(Dialect dialect);

	SetBasedMigrationSnapshotExecutor create(Dialect dialect);
}
