/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.sqlapp.data.db.dialect.mariadb.integration.MariadbSequenceIntegrationTest.*;
import java.sql.Connection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mariadb.Mariadb11_40;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/** Explicit real-server qualification, excluded from ordinary test. */
@Tag("mariadb-integration")
class MariadbObjectIntegrationTest {
	private void checkServer(Connection connection) throws Exception {
		assertTrue(connection.getMetaData().getDatabaseProductName().contains("MariaDB"), "Expected MariaDB server");
	}

	private String qualified(String schema, String name) {
		return "`" + schema + "`.`" + name + "`";
	}

	private void recreate(Connection connection, AbstractSchemaObject<?> object) throws Exception {
		var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		for (var operation : registry.createSql(object, SqlType.CREATE))
			execute(connection, operation.getSqlText());
	}

	private Table readTable(Connection connection, String schema, String name) {
		var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader()
				.getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName(name);
		reader.getReaderOptions().setContainsHiddenColumns(true);
		var tables = reader.getAllFull(connection);
		assertEquals(1, tables.size());
		return tables.get(0);
	}

	@Test
	void applicationPeriodGeneratedInvisibleColumnAndIgnoredIndexRoundTrip() throws Exception {
		try (var connection = connect()) {
			checkServer(connection);
			assumeTrue(DialectResolver.getInstance().getDialect(connection) instanceof Mariadb11_40,
					"Period metadata scenario requires MariaDB 11.4+");
			try (var schema = new TemporarySchema(connection)) {
				execute(connection, "CREATE TABLE " + qualified(schema.name, "source_table")
						+ " (id INT, valid_from DATE NOT NULL, valid_until DATE NOT NULL,"
						+ " payload VARCHAR(40) INVISIBLE, doubled INT AS (id * 2) STORED,"
						+ " PERIOD FOR valid_time(valid_from, valid_until), INDEX idx_payload(payload) IGNORED)");
				Table original = readTable(connection, schema.name, "source_table");
				Table restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
				restored.setName("recreated_table");
				recreate(connection, restored);
				Table recreated = readTable(connection, schema.name, "recreated_table");
				assertEquals("valid_from", recreated.getTemporalPeriods().get("valid_time").getStartColumnName());
				assertEquals("valid_until", recreated.getTemporalPeriods().get("valid_time").getEndColumnName());
				assertTrue(recreated.getColumns().get("payload").isHidden());
				assertTrue(recreated.getColumns().get("doubled").isFormulaPersisted());
				assertEquals(original.getColumns().get("doubled").getFormula(),
						recreated.getColumns().get("doubled").getFormula());
				assertFalse(recreated.getIndexes().get("idx_payload").isEnable());
			}
		}
	}

	@Test
	void periodKeyReplaysAndRejectsOverlappingRows() throws Exception {
		try (var connection = connect()) {
			checkServer(connection);
			assumeTrue(DialectResolver.getInstance().getDialect(connection) instanceof Mariadb11_40,
					"Period key metadata scenario requires MariaDB 11.4+");
			try (var schema = new TemporarySchema(connection)) {
				String name = qualified(schema.name, "bookings");
				execute(connection,
						"CREATE TABLE " + name + " (room_id INT NOT NULL, valid_from DATE NOT NULL,"
								+ " valid_until DATE NOT NULL, PERIOD FOR valid_time(valid_from,valid_until),"
								+ " UNIQUE KEY unique_time(room_id,valid_time WITHOUT OVERLAPS))");
				Table original = readTable(connection, schema.name, "bookings");
				Table restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
				var key = restored.getConstraints().get("unique_time");
				assertEquals("valid_time", key.getSpecifics().get("mariadb.withoutOverlapsPeriod"));
				execute(connection, "ALTER TABLE " + name + " DROP INDEX unique_time");
				recreate(connection, key);
				execute(connection, "INSERT INTO " + name + " VALUES (1,'2020-01-01','2020-01-05')");
				assertThrows(java.sql.SQLException.class,
						() -> execute(connection, "INSERT INTO " + name + " VALUES (1,'2020-01-02','2020-01-04')"));
				execute(connection, "INSERT INTO " + name + " VALUES (1,'2020-01-05','2020-01-08')");
			}
		}
	}

	@Test
	void viewAlgorithmSecurityChecksAndOutputNamesRoundTrip() throws Exception {
		try (var connection = connect()) {
			checkServer(connection);
			try (var schema = new TemporarySchema(connection)) {
				connection.setCatalog(schema.name);
				execute(connection, "CREATE TABLE " + qualified(schema.name, "items") + " (id INT)");
				execute(connection,
						"CREATE ALGORITHM=MERGE SQL SECURITY INVOKER VIEW " + qualified(schema.name, "source_view")
								+ " (`order id`) AS SELECT id FROM " + qualified(schema.name, "items")
								+ " WHERE id > 0 WITH CASCADED CHECK OPTION");
				var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader()
						.getViewReader();
				reader.setSchemaName(schema.name);
				reader.setObjectName("source_view");
				var originals = reader.getAllFull(connection);
				assertEquals(1, originals.size());
				View restored = SchemaUtils.readXml(new java.io.StringReader(originals.get(0).asXml()));
				restored.setName("recreated_view");
				recreate(connection, restored);
				reader.setObjectName("recreated_view");
				var copies = reader.getAllFull(connection);
				assertEquals(1, copies.size());
				View copy = (View) copies.get(0);
				assertEquals("order id", copy.getColumns().get(0).getName());
				assertEquals("MERGE", copy.getSpecifics().get("algorithm"));
				assertEquals("INVOKER", copy.getSpecifics().get("security_type"));
				assertEquals("CASCADED", copy.getSpecifics().get("check_option"));
				assertThrows(java.sql.SQLException.class, () -> execute(connection,
						"INSERT INTO " + qualified(schema.name, "recreated_view") + " VALUES (-1)"));
			}
		}
	}

	@Test
	void procedureEnumArgumentAndFunctionReturnRoundTrip() throws Exception {
		try (var connection = connect()) {
			checkServer(connection);
			try (var schema = new TemporarySchema(connection)) {
				connection.setCatalog(schema.name);
				execute(connection, "CREATE PROCEDURE " + qualified(schema.name, "source_procedure")
						+ " (INOUT choice ENUM('a, b','done')) SQL SECURITY INVOKER BEGIN SET choice='done'; END");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var procedures = dialect.getCatalogReader().getSchemaReader().getProcedureReader();
				procedures.setSchemaName(schema.name);
				procedures.setObjectName("source_procedure");
				var originals = procedures.getAllFull(connection);
				assertEquals(1, originals.size());
				Procedure restored = SchemaUtils.readXml(new java.io.StringReader(originals.get(0).asXml()));
				restored.setName("recreated_procedure");
				recreate(connection, restored);
				procedures.setObjectName("recreated_procedure");
				var copies = procedures.getAllFull(connection);
				assertEquals(1, copies.size());
				assertEquals(originals.get(0).getArguments().get(0).getValues(),
						copies.get(0).getArguments().get(0).getValues());
				assertEquals(originals.get(0).getSqlSecurity(), copies.get(0).getSqlSecurity());
				execute(connection, "CREATE FUNCTION " + qualified(schema.name, "source_function")
						+ " () RETURNS DECIMAL(12,3) DETERMINISTIC NO SQL SQL SECURITY INVOKER RETURN 1.250");
				var functions = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
				functions.setSchemaName(schema.name);
				functions.setObjectName("source_function");
				var originalsF = functions.getAllFull(connection);
				assertEquals(1, originalsF.size());
				Function restoredF = SchemaUtils.readXml(new java.io.StringReader(originalsF.get(0).asXml()));
				restoredF.setName("recreated_function");
				recreate(connection, restoredF);
				functions.setObjectName("recreated_function");
				var copiesF = functions.getAllFull(connection);
				assertEquals(1, copiesF.size());
				assertEquals(originalsF.get(0).getReturning().getDataType(),
						copiesF.get(0).getReturning().getDataType());
				assertEquals(originalsF.get(0).getReturning().getScale(), copiesF.get(0).getReturning().getScale());
				try (var statement = connection.createStatement();
						var rows = statement
								.executeQuery("SELECT " + qualified(schema.name, "recreated_function") + "()")) {
					assertTrue(rows.next());
					assertEquals(0, new java.math.BigDecimal("1.250").compareTo(rows.getBigDecimal(1)));
				}
			}
		}
	}
}
