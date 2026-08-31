/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractDbTest;

class JdbcBulkMigrationMaintenanceStateStoreTest extends AbstractDbTest {
	@Test
	void createsSavesReplacesLoadsAndDeletesStateThroughSqlFactories()
			throws Exception {
		testDb(connection -> {
			final var store = new JdbcBulkMigrationMaintenanceStateStore(connection,
					"SQLAPP_MAINTENANCE_STATE_TEST");
			final String fingerprint = "plan-v1";
			final var prepared = new BulkMigrationMaintenanceState(fingerprint,
					BulkMigrationMaintenanceStatus.PREPARED,
					Instant.parse("2026-08-31T02:00:00Z"), null);
			store.save(prepared);
			assertEquals(prepared, store.load(fingerprint).orElseThrow());

			final var failed = new BulkMigrationMaintenanceState(fingerprint,
					BulkMigrationMaintenanceStatus.RESTORE_FAILED,
					Instant.parse("2026-08-31T02:01:00Z"), "restore failed");
			store.save(failed);
			assertEquals(failed, store.load(fingerprint).orElseThrow());

			store.delete(fingerprint);
			assertFalse(store.load(fingerprint).isPresent());
		});
	}
}
