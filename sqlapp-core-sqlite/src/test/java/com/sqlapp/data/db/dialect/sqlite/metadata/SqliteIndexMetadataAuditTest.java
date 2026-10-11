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
					new Class<?>[] {Connection.class}, (proxy, method, args) -> {
				if (method.getName().equals("prepareStatement")) queries.incrementAndGet();
				try { return method.invoke(raw, args); }
				catch (InvocationTargetException e) { throw e.getCause(); }
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
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t (value TEXT, active INT)");
			statement.execute("CREATE UNIQUE INDEX other.ix ON t(value COLLATE NOCASE DESC, lower(value)) WHERE(active=1)");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("other");
			reader.setObjectName("t");
			var table = reader.getAllFull(connection).get(0);
			com.sqlapp.data.schemas.Table restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			var index = restored.getIndexes().get("ix");
			assertEquals("(active=1)", index.getWhere());
			statement.execute("DROP INDEX other.ix");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			for (var operation : registry.createSql(index, com.sqlapp.data.db.sql.SqlType.CREATE)) statement.execute(operation.getSqlText());
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
				statement.execute("CREATE TABLE t" + i + " (a INTEGER PRIMARY KEY, b TEXT UNIQUE, c TEXT AS (lower(b)) STORED, CHECK(length(b)>0), FOREIGN KEY(a) REFERENCES t0(a))");
				statement.execute("CREATE INDEX ix" + i + " ON t" + i + "(lower(b) DESC) WHERE a>0");
			}
			var queries = new AtomicInteger();
			Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] {Connection.class}, (proxy, method, args) -> {
				if (method.getName().equals("prepareStatement")) queries.incrementAndGet();
				try { return method.invoke(raw, args); }
				catch (InvocationTargetException e) { throw e.getCause(); }
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
			assertEquals(6, one);
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
		assertFalse(SqliteIndexReader.supportsBulkIndexes(new ProductVersionInfo().setMajorVersion(3).setMinorVersion(15)));
		assertTrue(SqliteIndexReader.supportsBulkIndexes(new ProductVersionInfo().setMajorVersion(3).setMinorVersion(16)));
		assertFalse(SqliteIndexReader.supportsBulkIndexes(new ProductVersionInfo()));
	}
}
