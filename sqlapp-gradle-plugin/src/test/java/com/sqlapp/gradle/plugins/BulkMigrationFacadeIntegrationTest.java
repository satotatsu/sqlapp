/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.BulkMigration;
import com.sqlapp.data.db.command.migration.BulkMigrationTableOption;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress;

class BulkMigrationFacadeIntegrationTest {
	@TempDir
	Path directory;

	@Test
	void executesUpsertAndVerifiesThroughTheFacade() throws Exception {
		final DataSource source = sqlite(directory.resolve("source.db"));
		final DataSource target = sqlite(directory.resolve("target.db"));
		final List<String> events = new ArrayList<>();
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'one'), (2, 'two')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'old')");
		}
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema()).tables("ITEMS").resume(true)
				.tableOption("ITEMS", BulkMigrationTableOption.builder()
						.migrationId("items-copy").chunkSize(1).build())
				.jobListener(new BulkMigrationJobListener() {
					@Override
					public void onTaskStarted(String taskId, int taskIndex, int taskCount) {
						events.add("task:" + taskId);
					}
				})
				.chunkListener(new ChunkedBulkMigrationListener() {
					@Override
					public void onChunkCompleted(ChunkedBulkMigrationProgress progress) {
						events.add("chunk:" + progress.getChunkIndex());
					}
				})
				.fingerprints("source-v1", "target-v1").build();

		final var outcome = migration.executeAndVerify();

		assertEquals(2, outcome.migration().getProcessedRows());
		assertTrue(outcome.isMatch());
		assertEquals(List.of("task:ITEMS", "chunk:0", "chunk:1"), events);
		assertEquals(BulkMigrationJobTaskState.COMPLETE,
				migration.inspect().getTasks().get(0).getState());
		assertEquals("items-copy", migration.inspect().getTasks().get(0)
				.getCheckpoint().getMigrationId());
		final var resumed = migration.execute();
		assertEquals(0, resumed.getProcessedRows());
		assertEquals(1, resumed.getAlreadyCompleteTasks());
		try (var connection = target.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT ID, TXT FROM ITEMS ORDER BY ID")) {
			rows.next();
			assertEquals(1, rows.getInt(1));
			assertEquals("one", rows.getString(2));
			rows.next();
			assertEquals(2, rows.getInt(1));
			assertEquals("two", rows.getString(2));
		}
	}

	private static Schema schema() {
		final Schema schema = new Schema();
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("TXT").setDataType(DataType.CLOB));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		return schema;
	}

	private static DataSource sqlite(final Path file) throws Exception {
		final Object value = Class.forName("org.sqlite.SQLiteDataSource")
				.getConstructor().newInstance();
		value.getClass().getMethod("setUrl", String.class)
				.invoke(value, "jdbc:sqlite:" + file.toAbsolutePath());
		return (DataSource) value;
	}
}
