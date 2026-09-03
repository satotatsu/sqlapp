/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;

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
		final var verification = migration.verify();

		assertFalse(verification.isMatch());
		assertEquals(0, verification.getExpectedRows());
		assertEquals(1, verification.getActualRows());
		final var repair = migration.planRepair(verification);
		final var report = repair.writeJson(directory.resolve("repair.json"));
		assertEquals(0, report.estimatedReplayRows());
		assertEquals(1, report.mismatchChunks());
		final var result = repair.executeApproved(report.planFingerprint());
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
	}

	private static JDBCDataSource dataSource(final String name) {
		final JDBCDataSource dataSource = new JDBCDataSource();
		dataSource.setUrl("jdbc:hsqldb:mem:" + name);
		dataSource.setUser("SA");
		return dataSource;
	}
}
