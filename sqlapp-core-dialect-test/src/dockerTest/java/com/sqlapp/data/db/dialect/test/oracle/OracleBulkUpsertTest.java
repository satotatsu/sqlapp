/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.sql.Connection;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.oracle.OracleContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.dialect.test.BulkMigrationJobAssertions;
import com.sqlapp.data.db.dialect.test.BulkMigrationTransactionAssertions;
import com.sqlapp.data.db.dialect.test.BulkMigrationRepairAssertions;
import com.sqlapp.data.db.command.migration.FileBulkMigrationCheckpointStore;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

/** Exercises JDBC staging and MERGE against Oracle Database 23ai Free. */
class OracleBulkUpsertTest {
	private static final OracleContainer ORACLE = ReusableTestcontainers.configure(
			new OracleContainer("gvenzl/oracle-free:23-slim-faststart"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(ORACLE);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(ORACLE);
	}

	@Test
	void fencesJdbcJobLeaseOwnersAcrossConnections() throws Exception {
		try (Connection first = ORACLE.createConnection("");
				Connection second = ORACLE.createConnection("")) {
			BulkMigrationJobAssertions.assertJdbcLeaseOwnerFencing(first, second);
		}
	}

	@Test
	void migratesParentBeforeChildAndAggregatesFileCheckpointStatus(
			@TempDir final Path checkpointDirectory) throws Exception {
		try (Connection connection = ORACLE.createConnection("");
				var statement = connection.createStatement()) {
			dropTable(statement, "SQLAPP_BULK_JOB_CHILD_ORACLE");
			dropTable(statement, "SQLAPP_BULK_JOB_PARENT_ORACLE");
			statement.execute("CREATE TABLE SQLAPP_BULK_JOB_PARENT_ORACLE "
					+ "(ID NUMBER(10) PRIMARY KEY, TXT VARCHAR2(100))");
			statement.execute("CREATE TABLE SQLAPP_BULK_JOB_CHILD_ORACLE "
					+ "(ID NUMBER(10) PRIMARY KEY, PARENT_ID NUMBER(10) NOT NULL, "
					+ "TXT VARCHAR2(100), CONSTRAINT FK_SQLAPP_JOB_CHILD_ORACLE "
					+ "FOREIGN KEY (PARENT_ID) REFERENCES SQLAPP_BULK_JOB_PARENT_ORACLE(ID))");
			final Table parent = jobTable("SQLAPP_BULK_JOB_PARENT_ORACLE", false);
			parent.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "parent"); });
			final Table child = jobTable("SQLAPP_BULK_JOB_CHILD_ORACLE", true);
			child.getConstraints().addForeignKeyConstraint("FK_SQLAPP_JOB_CHILD_ORACLE",
					new Column[] { child.getColumns().get("PARENT_ID") },
					new Column[] { parent.getColumns().get("ID") });
			child.getRows().add(row -> {
				row.put("ID", 10); row.put("PARENT_ID", 1); row.put("TXT", "child");
			});

			BulkMigrationJobAssertions.assertDependencyOrderAndAggregatedStatus(
					connection, parent, child,
					new FileBulkMigrationCheckpointStore(checkpointDirectory),
					BulkMigrationCheckpointMode.FILE);
			try (var resultSet = statement.executeQuery(
					"SELECT COUNT(*) FROM SQLAPP_BULK_JOB_CHILD_ORACLE c "
					+ "JOIN SQLAPP_BULK_JOB_PARENT_ORACLE p ON p.ID = c.PARENT_ID")) {
				resultSet.next();
				assertEquals(1, resultSet.getInt(1));
			}
		}
	}

	@Test
	void databaseCheckpointRejectsTransactionBreakingStaging(
			@TempDir final Path checkpointDirectory) throws Exception {
		try (Connection connection = ORACLE.createConnection("");
				var statement = connection.createStatement()) {
			try { statement.execute("DROP TABLE SQLAPP_CHUNK_MIGRATION_ORACLE"); }
			catch (java.sql.SQLException ignored) { }
			statement.execute("CREATE TABLE SQLAPP_CHUNK_MIGRATION_ORACLE "
					+ "(CODE VARCHAR2(20) PRIMARY KEY, NAME VARCHAR2(100))");
			final Table table = new Table("SQLAPP_CHUNK_MIGRATION_ORACLE");
			final Column code = new Column("CODE").setDataType(DataType.VARCHAR).setLength(20);
			table.getColumns().add(code);
			table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(100));
			table.setPrimaryKey("PK_SQLAPP_CHUNK_MIGRATION_ORACLE", code);
			BulkMigrationTransactionAssertions.assertDatabaseCheckpointRejected(connection,
					table, "CODE", "NAME", "SELECT COUNT(*) FROM SQLAPP_CHUNK_MIGRATION_ORACLE");
			BulkMigrationTransactionAssertions.assertDatabaseCheckpointInsertAtomic(connection,
					table, "CODE", "NAME", "SELECT COUNT(*) FROM SQLAPP_CHUNK_MIGRATION_ORACLE");
			statement.execute("DELETE FROM SQLAPP_CHUNK_MIGRATION_ORACLE");
			BulkMigrationTransactionAssertions.assertFileCheckpointCompletes(connection,
					table, "CODE", "NAME", "SELECT COUNT(*) FROM SQLAPP_CHUNK_MIGRATION_ORACLE",
					checkpointDirectory);
		}
	}

	@Test
	void updatesMatchesAndInsertsMissingRowsThroughBatchStaging()
			throws Exception {
		try (Connection connection = ORACLE.createConnection("");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE SQLAPP_BULK_UPSERT_ORACLE ("
					+ "ID NUMBER GENERATED BY DEFAULT AS IDENTITY UNIQUE, "
					+ "CODE VARCHAR2(20) PRIMARY KEY, NAME NVARCHAR2(100), "
					+ "AMOUNT NUMBER(12,2), PAYLOAD RAW(20))");
			statement.execute("INSERT INTO SQLAPP_BULK_UPSERT_ORACLE "
					+ "(CODE, NAME, AMOUNT, PAYLOAD) VALUES "
					+ "('A', 'old', 1.00, HEXTORAW('01'))");

			final Table table = createTable();
			table.getRows().add(row -> {
				row.put("CODE", "A");
				row.put("NAME", "更新後\nline");
				row.put("AMOUNT", new BigDecimal("12.34"));
				row.put("PAYLOAD", new byte[] { 0, (byte) 0xff });
			});
			table.getRows().add(row -> {
				row.put("CODE", "B");
				row.put("NAME", null);
				row.put("AMOUNT", null);
			});
			table.getRows().add(row -> {
				row.put("CODE", "C");
				row.put("NAME", "");
				row.put("AMOUNT", new BigDecimal("0.00"));
				row.put("PAYLOAD", new byte[] { 2 });
			});

			assertEquals(3, BulkUpsertResolver.execute(connection, table,
					BulkUpsertOption.builder().bulkOption(
							BulkOption.builder().batchSize(2).build()).build()));

			try (var resultSet = statement.executeQuery("SELECT ID, CODE, NAME, "
					+ "AMOUNT, PAYLOAD FROM SQLAPP_BULK_UPSERT_ORACLE ORDER BY CODE")) {
				resultSet.next();
				assertEquals(1L, resultSet.getLong("ID"));
				assertEquals("A", resultSet.getString("CODE"));
				assertEquals("更新後\nline", resultSet.getString("NAME"));
				assertEquals(new BigDecimal("12.34"),
						resultSet.getBigDecimal("AMOUNT"));
				assertArrayEquals(new byte[] { 0, (byte) 0xff },
						resultSet.getBytes("PAYLOAD"));
				resultSet.next();
				assertEquals("B", resultSet.getString("CODE"));
				assertNull(resultSet.getString("NAME"));
				assertNull(resultSet.getBigDecimal("AMOUNT"));
				resultSet.next();
				assertEquals("C", resultSet.getString("CODE"));
				// Oracle stores an empty string as NULL.
				assertNull(resultSet.getString("NAME"));
				assertArrayEquals(new byte[] { 2 }, resultSet.getBytes("PAYLOAD"));
			}
		}
	}

	private static Table createTable() {
		final Table table = new Table("SQLAPP_BULK_UPSERT_ORACLE");
		final Column id = new Column("ID").setDataType(DataType.DECIMAL)
				.setIdentity(true);
		final Column code = new Column("CODE").setDataType(DataType.VARCHAR)
				.setLength(20).setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(code);
		table.getColumns().add(new Column("NAME").setDataType(DataType.NVARCHAR)
				.setLength(100));
		table.getColumns().add(new Column("AMOUNT").setDataType(DataType.DECIMAL)
				.setLength(12).setScale(2));
		table.getColumns().add(new Column("PAYLOAD")
				.setDataType(DataType.VARBINARY).setLength(20));
		table.setPrimaryKey("PK_SQLAPP_BULK_UPSERT_ORACLE", code);
		return table;
	}

	@Test
	void repairsJdbcKeysetMismatchIntoADifferentTargetAndReverifies() throws Exception {
		try (Connection sourceConnection = ORACLE.createConnection("");
				Connection targetConnection = ORACLE.createConnection("");
				var statement = targetConnection.createStatement()) {
			dropTable(statement, "SQLAPP_BULK_REPAIR_SOURCE_ORA");
			dropTable(statement, "SQLAPP_BULK_REPAIR_TARGET_ORA");
			statement.execute("CREATE TABLE SQLAPP_BULK_REPAIR_SOURCE_ORA "
					+ "(ID NUMBER(10) PRIMARY KEY, TXT VARCHAR2(100))");
			statement.execute("CREATE TABLE SQLAPP_BULK_REPAIR_TARGET_ORA "
					+ "(ID NUMBER(10) PRIMARY KEY, TXT VARCHAR2(100))");
			for (int id = 1; id <= 4; id++) {
				statement.executeUpdate("INSERT INTO SQLAPP_BULK_REPAIR_SOURCE_ORA VALUES ("
						+ id + ",'value-" + id + "')");
				statement.executeUpdate("INSERT INTO SQLAPP_BULK_REPAIR_TARGET_ORA VALUES ("
						+ id + ",'" + (id == 2 ? "wrong" : "value-" + id) + "')");
			}
			BulkMigrationRepairAssertions.assertDifferentTargetRepair(sourceConnection,
					targetConnection, jobTable("SQLAPP_BULK_REPAIR_SOURCE_ORA", false),
					jobTable("SQLAPP_BULK_REPAIR_TARGET_ORA", false),
					List.of("ID", "TXT"), false);
		}
	}

	private static Table jobTable(final String name, final boolean child) {
		final Table table = new Table(name);
		final Column id = new Column("ID").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(id);
		if (child) {
			table.getColumns().add(new Column("PARENT_ID").setDataType(DataType.INT)
					.setNotNull(true));
		}
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(100));
		table.setPrimaryKey("PK_" + name, id);
		return table;
	}

	private static void dropTable(final java.sql.Statement statement, final String tableName)
			throws java.sql.SQLException {
		try {
			statement.execute("DROP TABLE " + tableName);
		} catch (java.sql.SQLException e) {
			if (e.getErrorCode() != 942) {
				throw e;
			}
		}
	}
}
