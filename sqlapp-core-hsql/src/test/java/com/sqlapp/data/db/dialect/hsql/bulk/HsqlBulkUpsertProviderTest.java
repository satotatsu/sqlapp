/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;

class HsqlBulkUpsertProviderTest {
	@Test
	void resolvesAndMergesInsertedAndMatchedRows() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_bulk_upsert", "SA", "")) {
			assertInstanceOf(HsqlBulkUpsertExecutor.class, BulkUpsertResolver.resolve(connection));
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INT PRIMARY KEY, NAME VARCHAR(30), STATUS VARCHAR(10))");
				statement.execute("INSERT INTO ITEMS VALUES (1, 'old', 'KEEP')");
			}
			final Table table = table();
			row(table, 1, "updated", "CHANGED");
			row(table, 2, "inserted", "NEW");
			final var option = BulkUpsertOption.builder().keyColumn("ID").updateColumn("NAME").build();

			assertEquals(2L, BulkUpsertResolver.execute(connection, table, option));
			assertEquals("updated|KEEP", value(connection, 1));
			assertEquals("inserted|NEW", value(connection, 2));
		}
	}

	@Test
	void supportsUpdateOnlyAndInsertOnlyPolicies() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_bulk_upsert_policies", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INT PRIMARY KEY, NAME VARCHAR(30), STATUS VARCHAR(10))");
				statement.execute("INSERT INTO ITEMS VALUES (1, 'old', 'KEEP')");
			}
			final Table updates = table();
			row(updates, 1, "updated", "IGNORED");
			row(updates, 2, "missing", "IGNORED");
			BulkUpsertResolver.execute(connection, updates, BulkUpsertOption.builder().keyColumn("ID")
					.updateColumn("NAME").insertWhenNotMatched(false).build());
			assertEquals("updated|KEEP", value(connection, 1));
			assertEquals(null, value(connection, 2));

			final Table inserts = table();
			row(inserts, 1, "ignored", "IGNORED");
			row(inserts, 2, "inserted", "NEW");
			BulkUpsertResolver.execute(connection, inserts, BulkUpsertOption.builder().keyColumn("ID")
					.updateWhenMatched(false).build());
			assertEquals("updated|KEEP", value(connection, 1));
			assertEquals("inserted|NEW", value(connection, 2));
		}
	}

	@Test
	void rollsBackTheBatchAndRestoresAutoCommitOnFailure() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_bulk_upsert_rollback", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INT PRIMARY KEY, NAME VARCHAR(30) NOT NULL, STATUS VARCHAR(10))");
				statement.execute("INSERT INTO ITEMS VALUES (1, 'old', 'KEEP')");
			}
			final Table table = table();
			row(table, 1, "updated", "CHANGED");
			row(table, 2, null, "INVALID");

			assertThrows(java.sql.SQLException.class,
					() -> BulkUpsertResolver.execute(connection, table, BulkUpsertOption.defaults()));
			assertEquals(true, connection.getAutoCommit());
			assertEquals("old|KEEP", value(connection, 1));
			assertEquals(null, value(connection, 2));
		}
	}

	@Test
	void repairsMappedAccessColumnsFromAnApprovedPlan() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_mapped_repair", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (TARGET_ID INT PRIMARY KEY, NAME VARCHAR(30), STATUS VARCHAR(10))");
				statement.execute("INSERT INTO ITEMS VALUES (1, 'old', 'KEEP')");
			}
			final Table expected = new Table("ACCESS_ITEMS");
			expected.getColumns().add(new Column("ACCESS_ID").setDataType(DataType.INT).setNotNull(true));
			expected.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(30));
			expected.getColumns().add(new Column("STATUS").setDataType(DataType.VARCHAR).setLength(10));
			expected.setPrimaryKey("PK_ACCESS_ITEMS", expected.getColumns().get("ACCESS_ID"));
			final var expectedRow = expected.newRow();
			expectedRow.put("ACCESS_ID", 1);
			expectedRow.put("NAME", "repaired");
			expectedRow.put("STATUS", "KEEP");
			expected.getRows().add(expectedRow);

			final Table target = new Table("ITEMS");
			target.getColumns().add(new Column("TARGET_ID").setDataType(DataType.INT).setNotNull(true));
			target.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(30));
			target.getColumns().add(new Column("STATUS").setDataType(DataType.VARCHAR).setLength(10));
			target.setPrimaryKey("PK_ITEMS", target.getColumns().get("TARGET_ID"));
			final var actualRow = target.newRow();
			actualRow.put("TARGET_ID", 1);
			actualRow.put("NAME", "old");
			actualRow.put("STATUS", "KEEP");
			target.getRows().add(actualRow);
			final var verification = BulkMigrationVerifier.verify(expected, expected.getRows().iterator(), target,
					target.getRows().iterator(), java.util.List.of("ACCESS_ID", "NAME", "STATUS"),
					java.util.List.of("TARGET_ID", "NAME", "STATUS"), 10, null, null);
			final var options = BulkMigrationRepairOption.builder()
					.columnMappings(java.util.Map.of("ACCESS_ID", "TARGET_ID"))
					.bulkUpsertOption(BulkUpsertOption.builder().keyColumn("TARGET_ID").build()).build();
			final var plan = BulkMigrationRepairPlanner.plan(connection, expected, target, verification, options);

			assertEquals(java.util.List.of("TARGET_ID"), plan.getKeyColumns());
			assertEquals(1, plan.getEstimatedReplayRows());
			final var repaired = BulkMigrationRepairExecutor.execute(connection, plan, plan.getFingerprint());
			assertEquals(1, repaired.getReplayedRows());
			assertEquals("repaired|KEEP", valueByTargetId(connection, 1));
		}
	}

	private static Table table() {
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(30));
		table.getColumns().add(new Column("STATUS").setDataType(DataType.VARCHAR).setLength(10));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		return table;
	}

	private static void row(final Table table, final int id, final String name, final String status) {
		final var row = table.newRow();
		row.put("ID", id);
		row.put("NAME", name);
		row.put("STATUS", status);
		table.getRows().add(row);
	}

	private static String value(final java.sql.Connection connection, final int id) throws Exception {
		try (var statement = connection.prepareStatement("SELECT NAME, STATUS FROM ITEMS WHERE ID = ?")) {
			statement.setInt(1, id);
			try (var result = statement.executeQuery()) {
				return result.next() ? result.getString(1) + "|" + result.getString(2) : null;
			}
		}
	}

	private static String valueByTargetId(final java.sql.Connection connection, final int id) throws Exception {
		try (var statement = connection.prepareStatement("SELECT NAME, STATUS FROM ITEMS WHERE TARGET_ID = ?")) {
			statement.setInt(1, id);
			try (var result = statement.executeQuery()) {
				return result.next() ? result.getString(1) + "|" + result.getString(2) : null;
			}
		}
	}
}
