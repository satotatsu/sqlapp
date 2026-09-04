/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLifecycle;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.InMemoryBulkMigrationCheckpointStore;

class BulkMigrationTest {
	@TempDir
	Path directory;

	@Test
	void verifiesAndPlansRepairThroughTheSimpleFacade() throws Exception {
		final JDBCDataSource source = dataSource("facade_source");
		final JDBCDataSource target = dataSource("facade_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'target only')");
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).tables("ITEMS").chunkSize(1).build();

		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				migration.inspect().getTasks().get(0).getState());
		try (var connection = target.getConnection(); var tables = connection.getMetaData()
				.getTables(connection.getCatalog(), null,
						"SQLAPP_BULK_MIGRATION_CHECKPOINT", new String[] { "TABLE" })) {
			assertFalse(tables.next());
		}
		final var verification = migration.verify();

		assertFalse(verification.isMatch());
		assertEquals(0, verification.getExpectedRows());
		assertEquals(1, verification.getActualRows());
		final var repair = migration.planRepair(verification);
		final var report = repair.writeJson(directory.resolve("repair.json"));
		assertEquals(0, report.estimatedReplayRows());
		assertEquals(1, report.mismatchChunks());
		final var result = repair.executeApproved(directory.resolve("repair.json"));
		assertEquals(0, result.getReplayedRows());
		assertEquals(List.of(0L), result.getTasks().get(0).getRepairResult()
				.getChunksWithoutExpectedRows());
	}

	@Test
	void keepsResumeExplicitBecauseSchemaIsNotADataFingerprint() {
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("invalid_source")).target(dataSource("invalid_target"))
				.schema(schema).resume(true).build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("invalid_file_source"))
				.target(dataSource("invalid_file_target")).schema(schema)
				.fileCheckpoints(null).build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("invalid_custom_source"))
				.target(dataSource("invalid_custom_target")).schema(schema)
				.customCheckpointStore(null).build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("invalid_lease_source"))
				.target(dataSource("invalid_lease_target")).schema(schema)
				.fileLease("worker", null));
		assertThrows(NullPointerException.class, () -> BulkMigration.builder()
				.operationalReport(null));

		final var customStore = new InMemoryBulkMigrationCheckpointStore();
		final BulkMigration custom = BulkMigration.builder()
				.source(dataSource("custom_source")).target(dataSource("custom_target"))
				.schema(schema).customCheckpointStore(customStore).build();
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				assertDoesNotThrow(custom::inspect).getTasks().get(0).getState());
	}

	@Test
	void appliesOnlyTheRequestedAdvancedTableOverrides() throws Exception {
		final JDBCDataSource source = dataSource("facade_override_source");
		final JDBCDataSource target = dataSource("facade_override_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection();
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
			}
		}
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("INSERT INTO ITEMS VALUES (1, 'source')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("INSERT INTO ITEMS VALUES (1, 'different')");
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).tableOption("ITEMS", BulkMigrationTableOption.builder()
						.verificationColumns(List.of("ID")).chunkSize(1).build()).build();

		final var verification = migration.verify();

		assertEquals(List.of("ID"), verification.getTasks().get(0).getColumns());
		assertTrue(verification.isMatch());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(source).target(target).schema(schema).tableOption("MISSING",
						BulkMigrationTableOption.defaults()).build());
	}

	@Test
	void executesWithAnOptionalDatabaseLeaseOnASeparateConnection() throws Exception {
		final JDBCDataSource source = dataSource("facade_database_lease_source");
		final JDBCDataSource target = dataSource("facade_database_lease_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).tables("ITEMS").mode(BulkMigrationMode.INSERT)
				.databaseLease("database-worker").build();

		assertEquals(0, migration.execute().getProcessedRows());
		try (var connection = target.getConnection(); var tables = connection.getMetaData()
				.getTables(connection.getCatalog(), null, "sqlapp_bulk_job_lease",
						new String[] { "TABLE" })) {
			assertTrue(tables.next());
		}
	}

	@Test
	void invokesAnOptionalLifecycleOnlyDuringExecution() throws Exception {
		final JDBCDataSource source = dataSource("facade_lifecycle_source");
		final JDBCDataSource target = dataSource("facade_lifecycle_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection();
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY)");
			}
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final List<String> events = new ArrayList<>();
		final BulkMigrationJobLifecycle lifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "test-lifecycle-v1";
			}

			@Override
			public void before(final Connection connection,
					final BulkMigrationJobPlan plan) throws SQLException {
				events.add("before");
			}

			@Override
			public void after(final Connection connection, final BulkMigrationJobPlan plan,
					final BulkMigrationJobResult result) throws SQLException {
				events.add("after");
			}
		};
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).mode(BulkMigrationMode.INSERT).lifecycle(lifecycle).build();

		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				migration.inspect().getTasks().get(0).getState());
		assertTrue(events.isEmpty());
		migration.execute();
		assertEquals(List.of("before", "after"), events);
	}

	private static JDBCDataSource dataSource(final String name) {
		final JDBCDataSource dataSource = new JDBCDataSource();
		dataSource.setUrl("jdbc:hsqldb:mem:" + name);
		dataSource.setUser("SA");
		return dataSource;
	}
}
