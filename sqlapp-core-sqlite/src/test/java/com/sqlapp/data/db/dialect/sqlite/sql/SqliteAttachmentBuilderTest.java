/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;

class SqliteAttachmentBuilderTest {
	@TempDir
	Path directory;

	@Test
	void quotedFileAndSchemaSupportMetadataDmlDetachAndReattach() throws Exception {
		Path file = directory.resolve("archive's copy.db");
		var schema = new Schema("Mixed]Db");
		var builder = new SqliteAttachmentBuilder(DialectHolder.defaultDialect);
		String attachSql = builder.attach(file.toString(), schema);
		assertFalse(Files.exists(file));
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			execute(connection, attachSql);
			execute(connection, "CREATE TABLE \"Mixed]Db\".t(id INTEGER PRIMARY KEY,value TEXT)");
			execute(connection, "INSERT INTO \"Mixed]Db\".t VALUES(7,'kept')");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName(schema.getName());
			reader.setObjectName("t");
			var table = reader.getAllFull(connection).get(0);
			assertEquals(schema.getName(), table.getSchemaName());
			var row = table.getRows().newElement();
			row.put("id", 7);
			table.getRows().add(row);
			var node = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSqlNodes(table, SqlType.SELECT)
					.get(0);
			try (var parameters = node.eval(row); var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				try (var result = query.executeQuery()) {
					assertTrue(result.next());
					assertEquals("kept", result.getString("value"));
				}
			}
			execute(connection, builder.detach(schema));
			assertThrows(SQLException.class, () -> execute(connection, "SELECT * FROM \"Mixed]Db\".t"));
			execute(connection, attachSql);
			try (var statement = connection.createStatement();
					var result = statement.executeQuery("SELECT value FROM \"Mixed]Db\".t WHERE id=7")) {
				assertTrue(result.next());
				assertEquals("kept", result.getString(1));
			}
		}
	}

	@Test
	void attachmentIsConnectionLocalAndNamesCannotInjectCommands() throws Exception {
		var builder = new SqliteAttachmentBuilder(DialectHolder.defaultDialect);
		String name = "odd'; DROP TABLE kept; --]";
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var other = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			execute(connection, "CREATE TABLE kept(id INT)");
			execute(connection, builder.attach(":memory:", name));
			String quoted = DialectHolder.defaultDialect.quote(name);
			execute(connection, "CREATE TABLE " + quoted + ".t(id INT)");
			assertThrows(SQLException.class, () -> execute(other, "SELECT * FROM " + quoted + ".t"));
			assertThrows(SQLException.class, () -> execute(connection, builder.attach(":memory:", name)));
			execute(connection, builder.detach(name));
			assertThrows(SQLException.class, () -> execute(connection, builder.detach(name)));
			execute(connection, "SELECT * FROM kept");
		}
	}

	@Test
	void detachReportsAnActiveTransactionWithoutCommittingIt() throws Exception {
		var builder = new SqliteAttachmentBuilder(DialectHolder.defaultDialect);
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			execute(connection, builder.attach(":memory:", "other"));
			execute(connection, "CREATE TABLE other.t(id INT)");
			connection.setAutoCommit(false);
			execute(connection, "INSERT INTO other.t VALUES(1)");
			assertThrows(SQLException.class, () -> execute(connection, builder.detach("other")));
			assertFalse(connection.getAutoCommit());
			connection.rollback();
			connection.setAutoCommit(true);
			try (var statement = connection.createStatement();
					var result = statement.executeQuery("SELECT count(*) FROM other.t")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
			execute(connection, builder.detach("other"));
		}
	}

	@Test
	void validatesInputsAndRetainsLegacySyntax() {
		var builder = new SqliteAttachmentBuilder(DialectHolder.defaultDialect);
		for (String value : new String[] { null, " ", "bad\0name", "MAIN", "Temp" }) {
			assertThrows(IllegalArgumentException.class, () -> builder.attach(":memory:", value));
			assertThrows(IllegalArgumentException.class, () -> builder.detach(value));
		}
		for (String value : new String[] { null, " ", "bad\0name" })
			assertThrows(IllegalArgumentException.class, () -> builder.attach(value, "other"));
		assertThrows(NullPointerException.class, () -> new SqliteAttachmentBuilder(null));
		assertThrows(IllegalArgumentException.class,
				() -> new SqliteAttachmentBuilder(DialectResolver.getInstance().getDialect("PostgreSQL", 15, 0, 0)));
		var old = new SqliteAttachmentBuilder(DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0));
		assertEquals(builder.attach(":memory:", "other"), old.attach(":memory:", "other"));
		assertEquals(builder.detach("other"), old.detach("other"));
	}

	private void execute(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}
}
