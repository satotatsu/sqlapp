/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.virtica.Virtica;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider;

/** Vertica staging-table SCD2 provider (legacy module spelling retained). */
public class VirticaSetBasedMigrationSnapshotProvider implements SetBasedMigrationSnapshotProvider {
	@Override public boolean supports(final Dialect dialect) { return dialect instanceof Virtica; }
	@Override public SetBasedMigrationSnapshotExecutor create(final Dialect dialect) { return new VirticaSetBasedMigrationSnapshotExecutor(dialect); }
}
