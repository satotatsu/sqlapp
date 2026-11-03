/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.aurora.mysql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.aurora.mysql.AuroraMySQL;
import com.sqlapp.data.db.dialect.test.DataTypeServerAssertions;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.bulk.*;

/**
 * Upstream MySQL only: this suite does not establish AWS Aurora compatibility.
 */
class AuroraMySqlCompatibilityTest {
	private static final MySQLContainer MYSQL = new MySQLContainer(
			System.getProperty("sqlapp.test.aurora.mysqlImage", "mysql:8.4")).withCommand("--local-infile=1");

	@BeforeAll
	static void start() {
		MYSQL.start();
		System.out.println("Upstream compatibility image: " + MYSQL.getDockerImageName());
	}

	@AfterAll
	static void stop() {
		MYSQL.stop();
	}

	private Connection connect() throws Exception {
		return MYSQL.createConnection("?allowLoadLocalInfile=true&characterEncoding=UTF-8");
	}

	private Dialect dialect(Connection connection) throws Exception {
		var metadata = connection.getMetaData();
		assertEquals(8, metadata.getDatabaseMajorVersion());
		assertEquals(Integer.parseInt(System.getProperty("sqlapp.test.aurora.mysqlMinor", "4")),
				metadata.getDatabaseMinorVersion());
		return DialectResolver.getInstance().getDialect("Aurora MySQL", 8, metadata.getDatabaseMinorVersion(), null);
	}

	private String name() {
		return "sqlapp_aurora_" + UUID.randomUUID().toString().replace("-", "");
	}

	private Table table(String name) {
		var table = new Table(name);
		table.getColumns().add("Id", c -> c.setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add("value", c -> c.setDataType(DataType.VARCHAR).setLength(200));
		table.getColumns().add("payload", c -> c.setDataType(DataType.VARBINARY).setLength(20));
		table.setPrimaryKey("pk_" + name, table.getColumns().get("Id"));
		return table;
	}

	private void create(Connection connection, Dialect dialect, Table table) throws Exception {
		try (var statement = connection.createStatement()) {
			for (var sql : dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE)) {
				statement.execute(sql.getSqlText());
			}
		}
	}

	private void drop(Connection connection, Dialect dialect, String name) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS " + dialect.quote(name));
		}
	}

	private long count(Connection connection, Dialect dialect, String name) throws Exception {
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM " + dialect.quote(name))) {
			assertTrue(rows.next());
			return rows.getLong(1);
		}
	}

	@Test
	void ordinaryMySqlDetectionPreservesCallerTransaction() throws Exception {
		try (var connection = connect()) {
			var dialect = dialect(connection);
			var table = table(name());
			create(connection, dialect, table);
			try {
				connection.setAutoCommit(false);
				try (var statement = connection.createStatement()) {
					statement.executeUpdate("INSERT INTO " + dialect.quote(table.getName()) + " (`Id`) VALUES (1)");
				}
				var resolved = DialectResolver.getInstance().getDialect(connection);
				assertFalse(resolved instanceof AuroraMySQL);
				assertEquals("MySQL", resolved.getProductName());
				assertFalse(connection.getAutoCommit());
				assertEquals(1, count(connection, dialect, table.getName()), "Detection must not roll back");
				connection.rollback();
				assertEquals(0, count(connection, dialect, table.getName()), "Detection must not commit");
			} finally {
				connection.rollback();
				connection.setAutoCommit(true);
				drop(connection, dialect, table.getName());
			}
		}
	}

	@Test
	void roundTripsTypesThroughMetadataAndGeneratedDdl() throws Exception {
		try (var connection = connect()) {
			System.out.println("Upstream server: " + connection.getMetaData().getDatabaseProductVersion());
			DataTypeServerAssertions.verify(connection, dialect(connection), "FLOAT", "FLOAT(53)", "REAL",
					"DOUBLE PRECISION", "DECIMAL(12,3)", "VARCHAR(12)", "VARBINARY(12)", "BLOB", "JSON");
		}
	}

	@Test
	void recreatesTableAndViewFromSchemaMetadata() throws Exception {
		try (var connection = connect()) {
			var dialect = dialect(connection);
			String name = name(), copy = name + "_copy", view = name + "_view";
			create(connection, dialect, table(name));
			try (var statement = connection.createStatement()) {
				try {
					statement.execute("CREATE VIEW " + dialect.quote(view) + " AS SELECT `Id`, `value` FROM "
							+ dialect.quote(name));
					var reader = dialect.getCatalogReader().getSchemaReader();
					String schemaName = connection.getCatalog();
					reader.setSchemaName(schemaName);
					var schema = reader.getAllFull(connection).stream().filter(s -> schemaName.equals(s.getName()))
							.findFirst().orElseThrow();
					var read = schema.getTables().get(name);
					assertNotNull(read);
					assertEquals(3, read.getColumns().size());
					assertEquals(DataType.INT, read.getColumns().get("Id").getDataType());
					assertEquals(DataType.VARCHAR, read.getColumns().get("value").getDataType());
					var pk = assertInstanceOf(UniqueConstraint.class, read.getConstraints().get("PRIMARY"));
					assertEquals("Id", pk.getColumns().getFirst().getName());
					var readView = schema.getViews().get(view);
					assertNotNull(readView);
					assertEquals(2, readView.getColumns().size());
					var recreated = read.clone().setName(copy);
					create(connection, dialect, recreated);
					statement.execute("DROP VIEW " + dialect.quote(view));
					for (var sql : dialect.createSqlFactoryRegistry().createSql(readView, SqlType.CREATE)) {
						statement.execute(sql.getSqlText());
					}
					statement.executeUpdate(
							"INSERT INTO " + dialect.quote(copy) + " (`Id`, `value`) VALUES (7, 'round trip')");
					try (var rows = statement
							.executeQuery("SELECT `value` FROM " + dialect.quote(copy) + " WHERE `Id` = 7")) {
						assertTrue(rows.next());
						assertEquals("round trip", rows.getString(1));
					}
					assertThrows(SQLException.class,
							() -> statement.executeUpdate("INSERT INTO " + dialect.quote(copy) + " (`Id`) VALUES (7)"),
							"Recreated primary key must be enforced");
					statement.executeUpdate(
							"INSERT INTO " + dialect.quote(name) + " (`Id`, `value`) VALUES (8, 'view round trip')");
					try (var rows = statement.executeQuery("SELECT * FROM " + dialect.quote(view))) {
						assertEquals(2, rows.getMetaData().getColumnCount());
						assertTrue(rows.next());
						assertEquals(8, rows.getInt("Id"));
						assertEquals("view round trip", rows.getString("value"));
						assertFalse(rows.next());
					}
				} finally {
					statement.execute("DROP VIEW IF EXISTS " + dialect.quote(view));
					drop(connection, dialect, copy);
					drop(connection, dialect, name);
				}
			}
		}
	}

	@Test
	void bulkInsertPreservesEscapesNullEmptyAndBinary() throws Exception {
		try (var connection = connect()) {
			var dialect = dialect(connection);
			var table = table(name());
			create(connection, dialect, table);
			try {
				table.getRows().add(r -> {
					r.put("Id", 1);
					r.put("value", "comma,\"quote\"\nline\t日本語\\path");
					r.put("payload", new byte[] { 0, (byte) 255 });
				});
				table.getRows().add(r -> {
					r.put("Id", 2);
					r.put("value", null);
				});
				table.getRows().add(r -> {
					r.put("Id", 3);
					r.put("value", "");
				});
				assertEquals(3, BulkInsertResolver.resolve(dialect).execute(connection, table, BulkOption.defaults()));
				try (var statement = connection.createStatement();
						var rows = statement
								.executeQuery("SELECT * FROM " + dialect.quote(table.getName()) + " ORDER BY `Id`")) {
					assertTrue(rows.next());
					assertEquals("comma,\"quote\"\nline\t日本語\\path", rows.getString("value"));
					assertArrayEquals(new byte[] { 0, (byte) 255 }, rows.getBytes("payload"));
					assertTrue(rows.next());
					assertNull(rows.getString("value"));
					assertNull(rows.getBytes("payload"));
					assertTrue(rows.next());
					assertEquals("", rows.getString("value"));
					assertFalse(rows.next());
				}
			} finally {
				drop(connection, dialect, table.getName());
			}
		}
	}

	@Test
	void bulkInsertAndUpsertRemainInCallerTransaction() throws Exception {
		try (var connection = connect()) {
			var dialect = dialect(connection);
			var table = table(name());
			create(connection, dialect, table);
			try {
				try (var statement = connection.createStatement()) {
					statement.executeUpdate(
							"INSERT INTO " + dialect.quote(table.getName()) + " (`Id`, `value`) VALUES (1, 'old')");
				}
				connection.setAutoCommit(false);
				table.getRows().add(r -> {
					r.put("Id", 2);
					r.put("value", "inserted");
				});
				assertEquals(1, BulkInsertResolver.resolve(dialect).execute(connection, table, BulkOption.defaults()));
				assertFalse(connection.getAutoCommit());
				assertEquals(2, count(connection, dialect, table.getName()));
				connection.rollback();
				assertEquals(1, count(connection, dialect, table.getName()));
				table.getRows().clear();
				table.getRows().add(r -> {
					r.put("Id", 1);
					r.put("value", "updated");
					r.put("payload", new byte[] { 1, 2 });
				});
				table.getRows().add(r -> {
					r.put("Id", 3);
					r.put("value", "new");
				});
				BulkUpsertResolver.resolve(dialect).execute(connection, table, BulkUpsertOption.defaults());
				assertFalse(connection.getAutoCommit());
				assertEquals(2, count(connection, dialect, table.getName()));
				try (var statement = connection.createStatement();
						var rows = statement.executeQuery(
								"SELECT `value`, payload FROM " + dialect.quote(table.getName()) + " WHERE `Id`=1")) {
					assertTrue(rows.next());
					assertEquals("updated", rows.getString(1));
					assertArrayEquals(new byte[] { 1, 2 }, rows.getBytes(2));
				}
				connection.rollback();
				assertEquals(1, count(connection, dialect, table.getName()));
				try (var statement = connection.createStatement();
						var rows = statement.executeQuery(
								"SELECT `value`, payload FROM " + dialect.quote(table.getName()) + " WHERE `Id`=1")) {
					assertTrue(rows.next());
					assertEquals("old", rows.getString(1));
					assertNull(rows.getBytes(2));
				}
			} finally {
				connection.rollback();
				connection.setAutoCommit(true);
				drop(connection, dialect, table.getName());
			}
		}
	}

	@Test
	void bulkUpsertCommitsOwnedTransactionAndRestoresAutocommit() throws Exception {
		try (var connection = connect()) {
			var dialect = dialect(connection);
			var table = table(name());
			create(connection, dialect, table);
			try {
				try (var statement = connection.createStatement()) {
					statement.executeUpdate(
							"INSERT INTO " + dialect.quote(table.getName()) + " (`Id`, `value`) VALUES (1, 'old')");
				}
				table.getRows().add(r -> {
					r.put("Id", 1);
					r.put("value", "updated");
				});
				table.getRows().add(r -> {
					r.put("Id", 2);
					r.put("value", "new");
				});
				BulkUpsertResolver.resolve(dialect).execute(connection, table, BulkUpsertOption.defaults());
				assertTrue(connection.getAutoCommit());
				try (var observer = connect();
						var statement = observer.createStatement();
						var rows = statement.executeQuery(
								"SELECT `Id`, `value` FROM " + dialect.quote(table.getName()) + " ORDER BY `Id`")) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
					assertEquals("updated", rows.getString(2));
					assertTrue(rows.next());
					assertEquals(2, rows.getInt(1));
					assertEquals("new", rows.getString(2));
					assertFalse(rows.next());
				}
			} finally {
				drop(connection, dialect, table.getName());
			}
		}
	}

}
