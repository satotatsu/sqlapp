/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.bulk;

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

class SqliteSetBasedMigrationSnapshotExecutorTest {
	@Test void appliesSetBasedSnapshotWithNullSafeComparison() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY(ID INTEGER, NAME TEXT, VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES(1,NULL,'2026-01-01',NULL,1),(2,'old','2026-01-01',NULL,1),(3,'removed','2026-01-01',NULL,1)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"),
					List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);
			final var result = SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(), definition,
					Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1, null), row(2, "new"), row(4, "added")), 2);
			assertEquals(new MigrationSnapshotExecutionResult(2, 2, 1), result);
			try (var statement = connection.createStatement(); var rs = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE VALID_TO IS NULL")) {
				rs.next(); assertEquals(3, rs.getInt(1));
			}
		}
	}
	private static Table table() {
		final Table table = new Table("CUSTOMER_HISTORY");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP));
		table.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
		table.getColumns().add(new Column("IS_CURRENT").setDataType(DataType.BOOLEAN));
		return table;
	}
	private static Map<String,Object> row(final int id, final String name) {
		final Map<String,Object> row = new LinkedHashMap<>(); row.put("ID", id); row.put("NAME", name); return row;
	}
}
