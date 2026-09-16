/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.sqlapp.jdbc.bulk.JdbcStreamingMigrationSnapshotExecutor;

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

	@Test void jdbcEntryPointSelectsSetBasedProvider() throws Exception {
		final String url = "jdbc:h2:mem:scd2_jdbc_entry;DB_CLOSE_DELAY=-1";
		try (var source = DriverManager.getConnection(url); var target = DriverManager.getConnection(url)) {
			try (var statement = source.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER(ID INT, NAME VARCHAR(20))");
				statement.execute("INSERT INTO CUSTOMER VALUES(1,'same'),(2,'new'),(4,'added')");
				statement.execute("CREATE TABLE CUSTOMER_HISTORY(ID INT, NAME VARCHAR(20), VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES(1,'same',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE),(2,'old',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE),(3,'removed',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"),
					List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);
			final var result = JdbcStreamingMigrationSnapshotExecutor.execute(source, target, sourceTable(), table(),
					definition, Instant.parse("2026-09-16T00:00:00Z"), 2, 2);
			assertEquals(new MigrationSnapshotExecutionResult(2, 2, 1), result);
		}
	}

	@Test void preservesCallerOwnedTransactionBoundary() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:scd2_caller_tx")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CALLER_WORK(ID INT)");
				statement.execute("CREATE TABLE CUSTOMER_HISTORY(ID INT, NAME VARCHAR(20), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES"
						+ "(1,'old',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE)");
			}
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement()) {
				statement.execute("INSERT INTO CALLER_WORK VALUES(1)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"),
					List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);

			SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(), definition,
					Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1, "new")), 2);

			assertFalse(connection.getAutoCommit());
			connection.rollback();
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery(
							"SELECT NAME, VALID_TO, IS_CURRENT FROM CUSTOMER_HISTORY WHERE ID=1")) {
				assertTrue(rs.next());
				assertEquals("old", rs.getString(1));
				assertNull(rs.getTimestamp(2));
				assertTrue(rs.getBoolean(3));
				assertFalse(rs.next());
			}
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT COUNT(*) FROM CALLER_WORK")) {
				rs.next();
				assertEquals(0, rs.getInt(1));
			}
		}
	}
	private static Table table() {
		final Table t=new Table("CUSTOMER_HISTORY").setSchemaName("PUBLIC");
		t.getColumns().add(new Column("ID").setDataType(DataType.INT)); t.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		t.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP)); t.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
		t.getColumns().add(new Column("IS_CURRENT").setDataType(DataType.BOOLEAN)); return t;
	}
	private static Table sourceTable() {
		final Table t=new Table("CUSTOMER").setSchemaName("PUBLIC");
		t.getColumns().add(new Column("ID").setDataType(DataType.INT));
		t.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		return t;
	}
	private static Map<String,Object> row(int id,String name){final Map<String,Object> r=new LinkedHashMap<>();r.put("ID",id);r.put("NAME",name);return r;}
}
