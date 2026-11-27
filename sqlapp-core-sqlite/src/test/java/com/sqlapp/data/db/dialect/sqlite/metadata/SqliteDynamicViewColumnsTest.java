/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.View;

class SqliteDynamicViewColumnsTest {
	@ParameterizedTest
	@ValueSource(ints = { 1, 8 })
	void viewTypingUsesOneCatalogAndOneBulkColumnQuery(int count) throws Exception {
		try (var raw = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = raw.createStatement()) {
			for (int i = 0; i < count; i++)
				statement.execute("CREATE VIEW v" + i + " AS SELECT '0001' AS value");
			var queries = new java.util.concurrent.atomic.AtomicInteger();
			var connection = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { java.sql.Connection.class }, (proxy, method, args) -> {
						if (method.getName().equals("prepareStatement") || method.getName().equals("createStatement"))
							queries.incrementAndGet();
						try {
							return method.invoke(raw, args);
						} catch (java.lang.reflect.InvocationTargetException e) {
							throw e.getCause();
						}
					});
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getViewReader();
			reader.setSchemaName("main");
			var views = reader.getAllFull(connection);
			assertEquals(count, views.size());
			assertTrue(views.stream().allMatch(view -> view.getColumns().get(0).getDataType() == DataType.ANY_DATA));
			assertEquals(2, queries.get());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 15, 37 })
	void legacyAndBulkReadersPreserveAnyAndExpressionColumnsWhileKeepingTextTypes(int minor) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(payload ANY, text_value TEXT, blob_value BLOB) STRICT");
			statement.execute(
					"CREATE VIEW v AS SELECT payload,text_value,blob_value,42 AS number,'0001' AS text_expr FROM t");
			var context = new ParametersContext();
			context.put("schemaName", "main");
			context.put("tableName", "v");
			var columns = new SqliteColumnReader(DialectHolder.defaultDialect).doGetAll(connection, context,
					new ProductVersionInfo().setMajorVersion(3).setMinorVersion(minor));
			assertEquals(List.of(DataType.ANY_DATA, DataType.VARCHAR, DataType.ANY_DATA, DataType.ANY_DATA,
					DataType.ANY_DATA), columns.stream().map(column -> column.getDataType()).toList());
			assertEquals("BLOB", columns.get(2).getSpecifics().get("sqlite.declaredType"));
			context.put("tableName", "t");
			assertEquals(DataType.BLOB, new SqliteColumnReader(DialectHolder.defaultDialect)
					.doGetAll(connection, context, new ProductVersionInfo().setMajorVersion(3).setMinorVersion(minor))
					.get(2).getDataType());
		}
	}

	@Test
	void xmlRecreatedViewPreservesMixedValuesWhenCopiedThroughModeledRows() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.source(id INT PRIMARY KEY,payload ANY) STRICT");
			Object[] values = { "000123", "word", 9007199254740993L, 1.25d, new byte[] { 0, 1, (byte) 255 }, null };
			try (var insert = connection.prepareStatement("INSERT INTO other.source VALUES(?,?)")) {
				for (int i = 0; i < values.length; i++) {
					insert.setInt(1, i + 1);
					insert.setObject(2, values[i]);
					insert.executeUpdate();
				}
			}
			statement.execute(
					"CREATE VIEW other.v AS SELECT id,payload,CASE WHEN id>0 THEN payload END AS computed FROM source");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getViewReader();
			reader.setSchemaName("other");
			reader.setObjectName("v");
			View view = SchemaUtils.readXml(new java.io.StringReader(reader.getAllFull(connection).get(0).asXml()));
			assertEquals(DataType.ANY_DATA, view.getColumns().get("payload").getDataType());
			assertEquals(DataType.ANY_DATA, view.getColumns().get("computed").getDataType());
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			statement.execute("DROP VIEW other.v");
			for (var operation : registry.createSql(view, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			statement.execute("CREATE TABLE other.target(id INT PRIMARY KEY,payload ANY,computed ANY) STRICT");
			var tableReader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			tableReader.setSchemaName("other");
			tableReader.setObjectName("target");
			var target = tableReader.getAllFull(connection).get(0);
			var insertNode = registry.createSqlNodes(target, SqlType.INSERT).get(0);
			try (var rows = statement.executeQuery("SELECT * FROM other.v ORDER BY id")) {
				while (rows.next()) {
					var row = view.getRows().newElement();
					for (var column : view.getColumns())
						row.put(column.getName(), rows.getObject(column.getName()));
					try (var parameters = insertNode.eval(row); var insert = parameters.createStatement(connection)) {
						parameters.setBind(insert);
						insert.executeUpdate();
					}
				}
			}
			try (var rows = statement.executeQuery(
					"SELECT id,typeof(payload),quote(payload),typeof(computed),quote(computed) FROM other.target ORDER BY id")) {
				String[] types = { "text", "text", "integer", "real", "blob", "null" };
				String[] literals = { "'000123'", "'word'", "9007199254740993", "1.25", "X'0001FF'", "NULL" };
				for (int i = 0; i < values.length; i++) {
					assertTrue(rows.next());
					assertEquals(i + 1, rows.getInt(1));
					assertEquals(types[i], rows.getString(2));
					assertEquals(literals[i], rows.getString(3));
					assertEquals(types[i], rows.getString(4));
					assertEquals(literals[i], rows.getString(5));
				}
				assertFalse(rows.next());
			}
		}
	}
}
