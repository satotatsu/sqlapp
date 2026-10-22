/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.hsql.HsqlTextTableSources;
import com.sqlapp.data.db.dialect.hsql.HsqlTextTableSources.Source;
import com.sqlapp.data.db.dialect.hsql.sql.HsqlCreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

class HsqlTextTablesTest {
	@TempDir
	Path directory;

	@Test
	void bindingsRejectUnknownIdsAndStaleMetadata() {
		var table = new com.sqlapp.data.schemas.Table("T").setSchemaName("APP");
		HsqlTextTableSources.record(table, "original.csv", false);
		assertThrows(IllegalArgumentException.class,
				() -> HsqlTextTableSources.validateBindings(Map.of("unknown", new Source("target.csv", false)),
						java.util.Set.of(HsqlTextTableSources.id(table))));
		assertThrows(IllegalArgumentException.class, () -> new Source("", false));
		table.setName("RENAMED");
		assertThrows(IllegalArgumentException.class, () -> HsqlTextTableSources.get(table));
	}

	@Test
	void storageAndReadonlyChangesExecuteWithoutLosingData() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:file:" + directory.resolve("alter"), "SA", "");
				var statement = connection.createStatement()) {
			try {
				statement.execute("CREATE MEMORY TABLE T (V INTEGER)");
				statement.execute("INSERT INTO T VALUES 7");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName("PUBLIC");
				var original = reader.getAllFull(connection).get(0);
				var target = original.clone().setTableType(com.sqlapp.data.schemas.Table.TableType.Cache)
						.setReadonly(true);
				var registry = dialect.createSqlFactoryRegistry();
				assertTrue(registry.createSql(original.diff(original.clone()
						.setTableType((com.sqlapp.data.schemas.Table.TableType) null).setReadonly(null)), SqlType.ALTER)
						.isEmpty());
				var forward = registry.createSql(original.diff(target), SqlType.ALTER);
				assertEquals(2, forward.size());
				for (var operation : forward)
					statement.execute(operation.getSqlText());
				assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO T VALUES 8"));
				for (var operation : registry.createSql(target.diff(original), SqlType.ALTER))
					statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO T VALUES 8");
				try (var rs = statement.executeQuery("SELECT SUM(V) FROM T")) {
					assertTrue(rs.next());
					assertEquals(15, rs.getInt(1));
				}
				var text = original.clone();
				HsqlTextTableSources.record(text, "other.csv", false);
				assertThrows(IllegalArgumentException.class,
						() -> registry.createSql(original.diff(text), SqlType.ALTER));
			} finally {
				statement.execute("SHUTDOWN");
			}
		}
	}

	@Test
	void textSourceRequiresExplicitBindingAndSurvivesXml() throws Exception {
		Files.writeString(directory.resolve("input'name.csv"), "1|original\n");
		Files.writeString(directory.resolve("target.csv"), "9|replacement\n");
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:file:" + directory.resolve("database"), "SA",
				""); var statement = connection.createStatement()) {
			try {
				statement.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				statement.execute("CREATE TEXT TABLE APP.T (ID INTEGER, NAME VARCHAR(50))");
				statement.execute("SET TABLE APP.T SOURCE 'input''name.csv;fs=|;encoding=UTF-8'");
				statement.execute("SET TABLE APP.T READ ONLY");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("APP");
				var schema = reader.getAllFull(connection).get(0);
				Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
				var table = restored.getTables().get("T");
				assertTrue(HsqlTextTableSources.isText(table));
				assertTrue(HsqlTextTableSources.get(table).definition().contains("input'name.csv"));
				assertTrue(table.getReadonly());
				var registry = dialect.createSqlFactoryRegistry();
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(restored, SqlType.CREATE));
				var factory = (HsqlCreateSchemaFactory) registry.getSqlFactory(restored, SqlType.CREATE);
				var operations = factory.createSql(restored,
						Map.of(HsqlTextTableSources.id(table), new Source("target.csv;fs=|;encoding=UTF-8", false)));
				assertFalse(operations.toString().contains("input'name.csv"));
				assertTrue(HsqlTextTableSources.get(table).definition().contains("input'name.csv"));
				statement.execute("DROP SCHEMA APP CASCADE");
				for (var operation : operations)
					statement.execute(operation.getSqlText());
				try (var rs = statement.executeQuery("SELECT ID, NAME FROM APP.T")) {
					assertTrue(rs.next());
					assertEquals(9, rs.getInt(1));
					assertEquals("replacement", rs.getString(2));
				}
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("INSERT INTO APP.T VALUES (10, 'fail')"));
				assertEquals("1|original\n", Files.readString(directory.resolve("input'name.csv")));
			} finally {
				statement.execute("SHUTDOWN");
			}
		}
	}

	@Test
	void unboundTextTableAndCachedTablePreserveStorageType() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:file:" + directory.resolve("storage"), "SA", "");
				var statement = connection.createStatement()) {
			try {
				statement.execute("CREATE CACHED TABLE CACHED_T (V INTEGER)");
				statement.execute("CREATE TEXT TABLE TEXT_T (V INTEGER)");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName("PUBLIC");
				for (var table : reader.getAllFull(connection)) {
					var operations = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
					statement.execute("DROP TABLE " + table.getName());
					for (var operation : operations)
						statement.execute(operation.getSqlText());
				}
				try (var rs = statement.executeQuery(
						"SELECT HSQLDB_TYPE FROM INFORMATION_SCHEMA.SYSTEM_TABLES WHERE TABLE_NAME='CACHED_T'")) {
					assertTrue(rs.next());
					assertEquals("CACHED", rs.getString(1));
				}
				try (var rs = statement.executeQuery(
						"SELECT HSQLDB_TYPE FROM INFORMATION_SCHEMA.SYSTEM_TABLES WHERE TABLE_NAME='TEXT_T'")) {
					assertTrue(rs.next());
					assertEquals("TEXT", rs.getString(1));
				}
			} finally {
				statement.execute("SHUTDOWN");
			}
		}
	}
}
