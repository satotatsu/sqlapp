/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;
import com.sqlapp.jdbc.bulk.MigrationSnapshotExecutionResult;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

class HsqlSetBasedMigrationSnapshotExecutorTest {

	@Test
	void stagesOnceAndAppliesSetBasedUpdateAndInsert() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_set_scd2", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, NAME VARCHAR(20), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES "
						+ "(1, 'same', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE), "
						+ "(2, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE), "
						+ "(3, 'removed', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"),
					List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);

			final var result = SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(), definition,
					Instant.parse("2026-09-16T00:00:00Z"),
					List.of(row(1, "same"), row(2, "new"), row(4, "added")), 100);

			assertEquals(new MigrationSnapshotExecutionResult(2, 2, 1), result);
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE VALID_TO IS NULL")) {
				rs.next();
				assertEquals(3, rs.getInt(1));
			}
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT NAME FROM CUSTOMER_HISTORY WHERE ID=2 AND VALID_TO IS NULL")) {
				rs.next();
				assertEquals("new", rs.getString(1));
			}
		}
	}

	private static Table table() {
		final Table table = new Table("CUSTOMER_HISTORY").setSchemaName("PUBLIC");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(20));
		table.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP).setNotNull(true));
		table.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
		table.getColumns().add(new Column("IS_CURRENT").setDataType(DataType.BOOLEAN).setNotNull(true));
		return table;
	}

	private static Map<String, Object> row(final int id, final String name) {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("ID", id);
		row.put("NAME", name);
		return row;
	}
}
