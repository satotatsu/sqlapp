/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Order;

class SqliteIndexMetadataAuditTest {
	@Test
	void legacyIndexDefinitionsRecreateAfterXmlWithoutChangingOtherSchemas() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE main.t(value TEXT,active INT)");
			statement.execute("CREATE INDEX main.ix ON t(value)");
			statement.execute("CREATE TABLE other.t(value TEXT,active INT)");
			statement.execute(
					"CREATE UNIQUE INDEX other.ix ON t(value COLLATE NOCASE DESC, lower(value) DESC /* key order */) WHERE active=1");
			var context = new ParametersContext();
			context.put("schemaName", "other");
			context.put("indexName", "ix");
			var index = new SqliteIndexReader(DialectHolder.defaultDialect)
					.doGetAll(connection, context, new ProductVersionInfo().setMajorVersion(3).setMinorVersion(15))
					.get(0);
			assertEquals("lower(value)", index.getColumns().get(1).getName());
			assertEquals(Order.Desc, index.getColumns().get(1).getOrder());
			var table = new com.sqlapp.data.schemas.Table("t").setSchemaName("other");
			table.getColumns().add(new com.sqlapp.data.schemas.Column("value")
					.setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR));
			table.getColumns().add(
					new com.sqlapp.data.schemas.Column("active").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
			table.getIndexes().add(index);
			com.sqlapp.data.schemas.Table restored = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(table.asXml()));
			statement.execute("DROP INDEX other.ix");
			for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restored.getIndexes().get("ix"), com.sqlapp.data.db.sql.SqlType.CREATE))
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO other.t VALUES('a',1)");
			assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO other.t VALUES('A',1)"));
			statement.execute("INSERT INTO other.t VALUES('A',0)");
			statement.execute("INSERT INTO main.t VALUES('a',1),('A',1)");
			var main = new SqliteIndexReader(DialectHolder.defaultDialect).doGetAll(connection, new ParametersContext(),
					new ProductVersionInfo());
			assertEquals(1, main.size());
			assertEquals("main", main.get(0).getSchemaName());
			assertFalse(main.get(0).isUnique());
		}
	}

	@Test
	void legacyIndexesUseOneCatalogQueryAndVersionAppropriatePragmas() throws Exception {
		try (var raw = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = raw.createStatement()) {
			statement.execute("CREATE TABLE t(other TEXT)");
			statement.execute("CREATE INDEX ix ON t(other)");
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(id INT PRIMARY KEY,value TEXT,active INT)");
			statement.execute(
					"CREATE UNIQUE INDEX other.ix ON t(value COLLATE NOCASE DESC /* final order */) WHERE active=1");
			statement.execute("CREATE TABLE other.u(value TEXT)");
			statement.execute("CREATE INDEX other.iu ON u(value)");
			var prepared = new AtomicInteger();
			var pragmas = new java.util.ArrayList<String>();
			Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						try {
							if (method.getName().equals("prepareStatement"))
								prepared.incrementAndGet();
							if (method.getName().equals("createStatement")) {
								var delegate = (java.sql.Statement) method.invoke(raw, args);
								return Proxy.newProxyInstance(getClass().getClassLoader(),
										new Class<?>[] { java.sql.Statement.class }, (p, m, a) -> {
											if (m.getName().equals("executeQuery"))
												pragmas.add((String) a[0]);
											try {
												return m.invoke(delegate, a);
											} catch (InvocationTargetException e) {
												throw e.getCause();
											}
										});
							}
							return method.invoke(raw, args);
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
					});
			var reader = new SqliteIndexReader(DialectHolder.defaultDialect);
			var context = new ParametersContext();
			context.put("schemaName", "other");
			for (var version : List.of(new ProductVersionInfo().setMajorVersion(3).setMinorVersion(7),
					new ProductVersionInfo().setMajorVersion(3).setMinorVersion(8).setRevision(8),
					new ProductVersionInfo().setMajorVersion(3).setMinorVersion(8).setRevision(9),
					new ProductVersionInfo().setMajorVersion(3).setMinorVersion(15))) {
				prepared.set(0);
				pragmas.clear();
				var indexes = reader.doGetAll(connection, context, version);
				assertEquals(3, indexes.size());
				assertEquals(1, prepared.get());
				assertEquals(1, pragmas.size());
				String expected = SqliteIndexReader.supportsExtendedIndexes(version) ? ".index_xinfo(" : ".index_info(";
				assertTrue(pragmas.stream().allMatch(sql -> sql.contains(expected)));
				var index = indexes.stream().filter(i -> i.getName().equals("ix")).findFirst().orElseThrow();
				assertEquals("other", index.getSchemaName());
				assertTrue(index.isUnique());
				assertEquals("value", index.getColumns().get(0).getName());
				assertEquals(Order.Desc, index.getColumns().get(0).getOrder());
				assertEquals("active=1", index.getWhere());
				assertTrue(index.getSpecifics().get("sqlite.index.keys").contains("COLLATE NOCASE"));
			}
			context.put("tableName", new String[] { "u" });
			assertEquals("iu", reader.doGetAll(raw, context, new ProductVersionInfo()).get(0).getName());
			context.put("tableName", List.of("t", "u"));
			context.put("indexName", "ix");
			assertEquals(1, reader.doGetAll(raw, context, new ProductVersionInfo()).size());
			context.put("tableName", List.of("missing"));
			assertTrue(reader.doGetAll(raw, context, new ProductVersionInfo()).isEmpty());
		}
	}

	@Test
	void batchesIndexesAcrossTablesAndKeepsAttachedSchemaSeparate() throws Exception {
		try (var raw = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = raw.createStatement()) {
			statement.execute("CREATE TABLE a(id INTEGER PRIMARY KEY, value TEXT UNIQUE)");
			statement.execute("CREATE TABLE b(id INTEGER, value TEXT)");
			statement.execute("CREATE INDEX ix_a ON a(lower(value) DESC) WHERE id > 2");
			statement.execute("CREATE INDEX ix_b ON b(value DESC)");
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.a(value TEXT)");
			statement.execute("CREATE INDEX other.ix_a ON a(value)");
			var queries = new AtomicInteger();
			Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						if (method.getName().equals("prepareStatement"))
							queries.incrementAndGet();
						try {
							return method.invoke(raw, args);
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
					});
			var context = new ParametersContext();
			context.put("schemaName", "main");
			context.put("tableName", List.of("a", "b"));
			var version = new ProductVersionInfo().setMajorVersion(3).setMinorVersion(53);
			var reader = new SqliteIndexReader(DialectHolder.defaultDialect);
			var indexes = reader.doGetAll(connection, context, version);
			assertEquals(1, queries.get());
			var expression = indexes.stream().filter(i -> i.getName().equals("ix_a")).findFirst().orElseThrow();
			assertEquals("lower(value)", expression.getColumns().get(0).getName());
			assertEquals(Order.Desc, expression.getColumns().get(0).getOrder());
			assertEquals("id > 2", expression.getWhere());
			context.put("tableName", "a");
			queries.set(0);
			assertEquals(2, reader.doGetAll(connection, context, version).size());
			assertEquals(1, queries.get());
			context.put("schemaName", "other");
			var attached = reader.doGetAll(connection, context, version);
			assertEquals(1, attached.size());
			assertEquals("value", attached.get(0).getColumns().get(0).getName());
			assertNull(attached.get(0).getWhere());
			var schemas = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getAllFull(raw);
			assertEquals(2, schemas.stream().filter(s -> s.getName().equals("main")).findFirst().orElseThrow()
					.getTables().get("a").getIndexes().size());
		}
	}

	@Test
	void indexXmlAndDdlRetainCollationPredicateAndAttachedSchema() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t (value TEXT, active INT)");
			statement.execute(
					"CREATE UNIQUE INDEX other.ix ON t(value COLLATE NOCASE DESC, lower(value)) WHERE(active=1)");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("other");
			reader.setObjectName("t");
			var table = reader.getAllFull(connection).get(0);
			com.sqlapp.data.schemas.Table restored = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(table.asXml()));
			var index = restored.getIndexes().get("ix");
			assertEquals("(active=1)", index.getWhere());
			statement.execute("DROP INDEX other.ix");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			for (var operation : registry.createSql(index, com.sqlapp.data.db.sql.SqlType.CREATE))
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO other.t VALUES ('a',1)");
			assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO other.t VALUES ('A',1)"));
			statement.execute("INSERT INTO other.t VALUES ('A',0)");
			var after = reader.getAllFull(connection).get(0).getIndexes().get("ix");
			assertEquals(index.getSpecifics().get("sqlite.index.keys"), after.getSpecifics().get("sqlite.index.keys"));
			assertEquals(index.getWhere(), after.getWhere());
		}
	}

	@Test
	void fullTableMetadataUsesConstantQueries() throws Exception {
		try (var raw = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = raw.createStatement()) {
			for (int i = 0; i < 8; i++) {
				statement.execute("CREATE TABLE t" + i
						+ " (a INTEGER PRIMARY KEY, b TEXT UNIQUE, c TEXT AS (lower(b)) STORED, CHECK(length(b)>0), FOREIGN KEY(a) REFERENCES t0(a))");
				statement.execute("CREATE INDEX ix" + i + " ON t" + i + "(lower(b) DESC) WHERE a>0");
			}
			var queries = new AtomicInteger();
			Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						if (method.getName().equals("prepareStatement"))
							queries.incrementAndGet();
						try {
							return method.invoke(raw, args);
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
					});
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("t0");
			var single = reader.getAllFull(connection);
			assertEquals(1, single.size());
			int one = queries.get();
			queries.set(0);
			reader.setObjectName(null);
			var tables = reader.getAllFull(connection);
			assertEquals(8, tables.size());
			assertEquals(one, queries.get());
			assertEquals(7, one); // Includes the schema-bound catalog enumeration.
			for (var table : tables) {
				assertEquals("lower(b)", table.getColumns().get("c").getFormula());
				assertEquals(4, table.getConstraints().size());
			}
		}
	}

	@Test
	void whereInsideExpressionIdentifierOrCommentIsNotAPredicate() {
		assertNull(SqliteIndexReader.extractWhere("CREATE INDEX ix ON t(instr(value, ' WHERE fake'))"));
		assertEquals("id > 0", SqliteIndexReader.extractWhere(
				"CREATE INDEX [WHERE ix] ON t(instr(value, ' WHERE fake')) /* WHERE fake */ WHERE id > 0"));
	}

	@Test
	void bulkPragmaStartsAt316() {
		assertFalse(
				SqliteIndexReader.supportsBulkIndexes(new ProductVersionInfo().setMajorVersion(3).setMinorVersion(15)));
		assertTrue(
				SqliteIndexReader.supportsBulkIndexes(new ProductVersionInfo().setMajorVersion(3).setMinorVersion(16)));
		assertFalse(SqliteIndexReader.supportsBulkIndexes(new ProductVersionInfo()));
	}
}
