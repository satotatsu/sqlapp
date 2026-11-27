/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractDbTest;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class BulkMigrationPreflightRegressionTest extends AbstractDbTest {
	@Test
	void mappedOverloadsRejectCallerTransactionsBeforeAccessingMetadata() {
		final Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, arguments) -> {
					if (method.getName().equals("getAutoCommit")) return false;
					throw new AssertionError("Unexpected JDBC call before rejection: " + method.getName());
				});
		final var options = ChunkedBulkMigrationOption.builder().migrationId("preflight").build();
		assertThrows(IllegalStateException.class, () -> ChunkedBulkMigrationExecutor.executeWithListener(
				connection, (BulkMigrationKeysetSource) null, (Table) null, options, ChunkedBulkMigrationListener.NO_OP));
		assertThrows(IllegalStateException.class, () -> ChunkedBulkMigrationExecutor.executeWithListener(
				connection, (BulkMigrationKeysetSource) null, (Table) null, Map.of(), options,
				ChunkedBulkMigrationListener.NO_OP));
		final var file = ChunkedBulkMigrationOption.builder().migrationId("file")
				.checkpointMode(BulkMigrationCheckpointMode.FILE).build();
		assertThrows(IllegalArgumentException.class, () -> ChunkedBulkMigrationExecutor.executeWithListener(
				connection, (BulkMigrationKeysetSource) null, (Table) null, Map.of(), file,
				ChunkedBulkMigrationListener.NO_OP));
	}

	@Test
	void directJobEntryPointsRejectOccupiedFullScanTargetBeforeMaintenance() throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE TABLE PREFLIGHT_TARGET (ID INTEGER, TXT VARCHAR(30))",
					"INSERT INTO PREFLIGHT_TARGET VALUES (1, 'existing')");
			final Table source = table("PREFLIGHT_SOURCE");
			final Table target = table("PREFLIGHT_TARGET");
			final var task = BulkMigrationJobTask.builder().taskId("copy").sourceTable(source).targetTable(target)
					.fullScan(true).requireEmptyTarget(true)
					.options(ChunkedBulkMigrationOption.builder().migrationId("copy").mode(BulkMigrationMode.INSERT)
							.resume(false).build()).build();
			assertThrows(SQLException.class, () -> BulkMigrationJobExecutor.execute(connection, List.of(task)));
			assertThrows(SQLException.class, () -> BulkMigrationJobExecutor.executePlan(connection,
					BulkMigrationJobPlanner.plan(List.of(task))));
			try (var statement = connection.createStatement();
					var rows = statement.executeQuery("SELECT COUNT(*) FROM PREFLIGHT_TARGET")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
			}
		});
	}

	@Test
	void repairSelectsDuplicatesAcrossReplayChunksBeforeWriting() {
		final Table target = table("REPAIR_TARGET");
		final Row first = row(target, "first");
		final Row last = row(target, "last");
		final List<Table> chunks = List.of(ChunkedBulkMigrationExecutor.chunkTable(target, List.of(first)),
				ChunkedBulkMigrationExecutor.chunkTable(target, List.of(last)));
		for (final var strategy : BulkUpsertDuplicateKeyStrategy.values()) {
			final var builder = BulkUpsertOption.builder().keyColumn("ID").duplicateKeyStrategy(strategy);
			if (strategy == BulkUpsertDuplicateKeyStrategy.CUSTOM) {
				builder.duplicateRowSelector((retained, candidate) -> retained)
						.duplicateRowSelectorFingerprint("first-v1");
			}
			final var option = builder.build();
			if (strategy == BulkUpsertDuplicateKeyStrategy.ERROR) {
				assertThrows(IllegalArgumentException.class,
						() -> BulkMigrationRepairExecutor.selectReplayChunks(target, chunks, option));
			} else {
				final var selected = BulkMigrationRepairExecutor.selectReplayChunks(target, chunks, option);
				assertEquals(1, selected.size());
				assertEquals(1, selected.getFirst().getRows().size());
				assertEquals(strategy == BulkUpsertDuplicateKeyStrategy.KEEP_LAST ? "last" : "first",
						selected.getFirst().getRows().getFirst().get("TXT"));
			}
		}
	}

	@Test
	void publicRepairRejectsCrossChunkDuplicatesBeforeResolvingTheProvider() {
		final Table expected = table("EXPECTED");
		expected.getRows().add(row(expected, "first"));
		expected.getRows().add(row(expected, "last"));
		final Table actual = table("ACTUAL");
		final var verification = BulkMigrationVerifier.verify(expected, actual, 1);
		final Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, arguments) -> {
					throw new AssertionError("Unexpected JDBC call before duplicate rejection: " + method.getName());
				});
		final var options = BulkMigrationRepairOption.builder()
				.bulkUpsertOption(BulkUpsertOption.builder().keyColumn("ID").build()).build();
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationRepairExecutor.execute(connection, expected, actual, verification, options));
	}

	@Test
	void directJobAllowsAnEmptyFullScanTarget() throws Exception {
		testDb(connection -> {
			connection.setAutoCommit(true);
			execute(connection, "CREATE TABLE EMPTY_TARGET (ID INTEGER, TXT VARCHAR(30))");
			final Table source = table("SOURCE");
			source.getRows().add(row(source, "copied"));
			final var task = BulkMigrationJobTask.builder().taskId("copy").sourceTable(source)
					.targetTable(table("EMPTY_TARGET")).fullScan(true).requireEmptyTarget(true)
					.options(ChunkedBulkMigrationOption.builder().migrationId("empty-copy")
							.mode(BulkMigrationMode.INSERT).resume(false).build()).build();
			BulkMigrationJobExecutor.execute(connection, List.of(task));
			try (var statement = connection.createStatement();
					var rows = statement.executeQuery("SELECT TXT FROM EMPTY_TARGET")) {
				assertTrue(rows.next());
				assertEquals("copied", rows.getString(1));
			}
		});
	}

	private static Table table(String name) {
		final Table table = new Table(name);
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(30));
		return table;
	}

	private static Row row(Table table, String text) {
		final Row row = table.newRow();
		row.put("ID", 1);
		row.put("TXT", text);
		return row;
	}
}
