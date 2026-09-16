/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.bulk;

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

class H2SetBasedMigrationSnapshotExecutorTest {
	@Test void appliesSetBasedSnapshot() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:scd2")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY(ID INT, NAME VARCHAR(20), VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES(1,'same',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE),(2,'old',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE),(3,'removed',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"), List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);
			final var result = SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(), definition,
					Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1,"same"),row(2,"new"),row(4,"added")), 2);
			assertEquals(new MigrationSnapshotExecutionResult(2, 2, 1), result);
		}
	}
	private static Table table() {
		final Table t=new Table("CUSTOMER_HISTORY").setSchemaName("PUBLIC");
		t.getColumns().add(new Column("ID").setDataType(DataType.INT)); t.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		t.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP)); t.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
		t.getColumns().add(new Column("IS_CURRENT").setDataType(DataType.BOOLEAN)); return t;
	}
	private static Map<String,Object> row(int id,String name){final Map<String,Object> r=new LinkedHashMap<>();r.put("ID",id);r.put("NAME",name);return r;}
}
