/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.aurora;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.aurora.AuroraPostgreSQL;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.*;

/**
 * Shared checks; upstream containers are explicitly NOT Aurora engine
 * verification.
 */
abstract class AuroraPostgresAssertions {
	abstract Connection connect() throws Exception;

	abstract boolean realAurora();

	private Dialect dialect(Connection connection) throws Exception {
		if (realAurora()) {
			var result = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(AuroraPostgreSQL.class, result, "Target was not identified as Aurora PostgreSQL");
			return result;
		}
		return DialectResolver.getInstance().getDialect("Aurora PostgreSQL",
				connection.getMetaData().getDatabaseMajorVersion(), 0, null);
	}

	private String schema() {
		return "sqlapp_aurora_" + UUID.randomUUID().toString().replace("-", "");
	}

	private Table table(String schema) {
		var table = new Table("Order").setSchemaName(schema);
		table.getColumns().add("Id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("value", c -> c.setDataType(DataType.LONGVARCHAR));
		table.getColumns().add("payload", c -> c.setDataType(DataType.VARBINARY));
		table.getColumns().add("tags", c -> c.setDataType(DataType.ARRAY).setDataTypeName("text[]"));
		table.setPrimaryKey("pk_order", table.getColumns().get("Id"));
		return table;
	}

	private void create(Connection connection, String schema) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schema);
			statement.execute("CREATE TABLE " + schema
					+ ".\"Order\" (\"Id\" integer PRIMARY KEY, value text, payload bytea, tags text[])");
		}
	}

	private void drop(Connection connection, String schema) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("DROP SCHEMA " + schema + " CASCADE");
		}
	}

	@Test
	void identificationPreservesCallerTransaction() throws Exception {
		try (var connection = connect()) {
			connection.setAutoCommit(false);
			var identified = DialectResolver.getInstance().getDialect(connection);
			assertEquals(realAurora(), identified instanceof AuroraPostgreSQL);
			assertFalse(connection.getAutoCommit());
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT 42")) {
				assertTrue(rows.next());
				assertEquals(42, rows.getInt(1));
			}
			connection.rollback();
		}
	}

	@Test
	void copyPreservesEscapesNullEmptyBinaryAndArrays() throws Exception {
		try (var connection = connect()) {
			String schema = schema();
			create(connection, schema);
			try {
				var table = table(schema);
				table.getRows().add(r -> {
					r.put("Id", 1);
					r.put("value", "comma,\"quote\"\nline\t日本語");
					r.put("payload", new byte[] { 0, (byte) 255 });
					r.put("tags", new String[] { "a,b", "\"q\"", "" });
				});
				table.getRows().add(r -> {
					r.put("Id", 2);
					r.put("value", null);
				});
				table.getRows().add(r -> {
					r.put("Id", 3);
					r.put("value", "");
				});
				assertEquals(3, BulkInsertResolver.resolve(dialect(connection)).execute(connection, table,
						BulkOption.defaults()));
				try (var statement = connection.createStatement();
						var rows = statement.executeQuery("SELECT * FROM " + schema + ".\"Order\" ORDER BY \"Id\"")) {
					assertTrue(rows.next());
					assertEquals("comma,\"quote\"\nline\t日本語", rows.getString("value"));
					assertArrayEquals(new byte[] { 0, (byte) 255 }, rows.getBytes("payload"));
					assertArrayEquals(new String[] { "a,b", "\"q\"", "" }, (String[]) rows.getArray("tags").getArray());
					assertTrue(rows.next());
					assertNull(rows.getString("value"));
					assertTrue(rows.next());
					assertEquals("", rows.getString("value"));
				}
			} finally {
				drop(connection, schema);
			}
		}
	}

	@Test
	void bulkUpsertUpdatesInsertsAndRollsBackWithCaller() throws Exception {
		try (var connection = connect()) {
			String schema = schema();
			create(connection, schema);
			try {
				var dialect = dialect(connection);
				var table = table(schema);
				table.getRows().add(r -> {
					r.put("Id", 1);
					r.put("value", "before");
				});
				BulkInsertResolver.resolve(dialect).execute(connection, table, BulkOption.defaults());
				table.getRows().clear();
				table.getRows().add(r -> {
					r.put("Id", 1);
					r.put("value", "after");
				});
				table.getRows().add(r -> {
					r.put("Id", 2);
					r.put("value", "insert");
				});
				connection.setAutoCommit(false);
				assertEquals(2,
						BulkUpsertResolver.resolve(dialect).execute(connection, table, BulkUpsertOption.defaults()));
				try (var statement = connection.createStatement();
						var rows = statement.executeQuery("SELECT count(*) FROM " + schema + ".\"Order\"")) {
					rows.next();
					assertEquals(2, rows.getInt(1));
				}
				connection.rollback();
				connection.setAutoCommit(true);
				try (var statement = connection.createStatement();
						var rows = statement.executeQuery("SELECT value FROM " + schema + ".\"Order\"")) {
					assertTrue(rows.next());
					assertEquals("before", rows.getString(1));
					assertFalse(rows.next());
				}
			} finally {
				if (!connection.getAutoCommit()) {
					connection.rollback();
					connection.setAutoCommit(true);
				}
				drop(connection, schema);
			}
		}
	}

	@Test
	void metadataAndGeneratedDdlRecreateTable() throws Exception {
		try (var connection = connect()) {
			String schema = schema();
			create(connection, schema);
			try {
				try (var statement = connection.createStatement()) {
					statement.execute("COMMENT ON COLUMN " + schema + ".\"Order\".value IS 'column comment'");
					statement.execute("CREATE INDEX idx_value ON " + schema + ".\"Order\" (value)");
					statement.execute("CREATE SEQUENCE " + schema + ".seq START 10");
					statement.execute(
							"CREATE VIEW " + schema + ".v AS SELECT \"Id\", value FROM " + schema + ".\"Order\"");
					statement.execute("CREATE FUNCTION " + schema
							+ ".twice(integer) RETURNS integer LANGUAGE sql AS 'SELECT $1 * 2'");
				}
				var dialect = dialect(connection);
				var reader = dialect.getCatalogReader();
				reader.setCatalogName(connection.getCatalog());
				reader.getSchemaReader().setSchemaName(schema);
				String catalogName = connection.getCatalog();
				var catalog = reader.getAllFull(connection).stream().filter(c -> c.getName().equals(catalogName))
						.findFirst().orElseThrow();
				var model = catalog.getSchemas().get(schema);
				assertNotNull(model);
				var original = model.getTables().get("Order");
				assertNotNull(original);
				assertEquals("column comment", original.getColumns().get("value").getRemarks());
				assertNotNull(original.getIndexes().get("idx_value"));
				assertNotNull(model.getSequences().get("seq"));
				assertNotNull(model.getViews().get("v"));
				assertNotNull(model.getFunctions().get("twice"));
				var recreated = original.clone().setName("recreated");
				recreated.getIndexes().clear();
				recreated.getConstraints().clear();
				com.sqlapp.data.db.sql.SqlFactory<Table> factory = dialect.createSqlFactoryRegistry()
						.getSqlFactory(recreated, SqlType.CREATE);
				try (var statement = connection.createStatement()) {
					for (var sql : factory.createSql(recreated)) {
						statement.execute(sql.getSqlText());
					}
					try (var rows = statement.executeQuery("SELECT * FROM " + schema + ".recreated")) {
						assertEquals(4, rows.getMetaData().getColumnCount());
					}
				}
			} finally {
				drop(connection, schema);
			}
		}
	}
}
