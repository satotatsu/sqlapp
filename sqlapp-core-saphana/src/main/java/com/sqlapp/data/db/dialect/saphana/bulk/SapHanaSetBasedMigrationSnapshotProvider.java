/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.saphana.SapHana;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider;

/** SAP HANA local-temporary-table SCD2 provider. */
public class SapHanaSetBasedMigrationSnapshotProvider implements SetBasedMigrationSnapshotProvider {
	@Override public boolean supports(final Dialect dialect) { return dialect instanceof SapHana; }
	@Override public SetBasedMigrationSnapshotExecutor create(final Dialect dialect) { return new SapHanaSetBasedMigrationSnapshotExecutor(dialect); }
}
