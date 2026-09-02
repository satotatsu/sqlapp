/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;

/** Shared real-database assertions for boundary-based repair. */
public final class BulkMigrationRepairAssertions {
	private BulkMigrationRepairAssertions() {
	}

	public static void assertDifferentTargetRepair(final Connection sourceConnection,
			final Connection targetConnection, final Table sourceTable,
			final Table targetTable, final List<String> verificationColumns)
			throws Exception {
		assertDifferentTargetRepair(sourceConnection, targetConnection, sourceTable,
				targetTable, verificationColumns, true);
	}

	public static void assertDifferentTargetRepair(final Connection sourceConnection,
			final Connection targetConnection, final Table sourceTable,
			final Table targetTable, final List<String> verificationColumns,
			final boolean useTransaction) throws Exception {
		final var expected = new JdbcBulkMigrationKeysetSource(sourceConnection, sourceTable);
		final var actual = new JdbcBulkMigrationKeysetSource(targetConnection, targetTable);
		final var before = BulkMigrationVerifier.verify(expected, actual,
				verificationColumns, 1);
		assertEquals(1, before.getMismatches().size());

		final var repair = BulkMigrationRepairExecutor.execute(targetConnection, expected,
				targetTable, before, BulkMigrationRepairOption.builder()
						.bulkUpsertOption(BulkUpsertOption.builder()
								.useTransaction(useTransaction).build())
						.build());

		assertEquals(1, repair.getReplayedChunks());
		assertEquals(1, repair.getReplayedRows());
		assertTrue(BulkMigrationVerifier.verify(expected,
				new JdbcBulkMigrationKeysetSource(targetConnection, targetTable),
				verificationColumns, 1).isMatch());
	}
}
