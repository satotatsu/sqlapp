/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.DialectHolder;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;

class SqlServer2025NativeTypesTest {
	@Test
	void nativeIndexDdlRequires2025() {
		var table = new Table("documents").setSchemaName("dbo");
		var column = new Column("document").setDataType(DataType.JSON);
		table.getColumns().add(column);
		var index = new Index("IX_document", column).setIndexType(IndexType.Json);
		table.getIndexes().add(index);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect2022.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		var sql = DialectHolder.defaultDialect2025.createSqlFactoryRegistry().createSql(index, SqlType.CREATE)
				.getFirst().getSqlText();
		assertTrue(sql.startsWith("CREATE JSON INDEX "), sql);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "SQLAPP_TEST_SQLSERVER_2025_FLOAT16", matches = "true")
	void roundTripsNativeIndexMetadataAndDdl() throws Exception {
		var dialect = DialectHolder.defaultDialect2025;
		var registry = dialect.createSqlFactoryRegistry();
		String filegroup = System.getenv("SQLAPP_TEST_SQLSERVER_2025_INDEX_FILEGROUP");
		String name = "native_indexes_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = DriverManager.getConnection(System.getenv("SQLAPP_TEST_SQLSERVER_2025_URL"), "sa",
				System.getenv("SQLAPP_TEST_SQLSERVER_2025_PASSWORD")); var statement = connection.createStatement()) {
			statement.execute("SET QUOTED_IDENTIFIER ON");
			statement.execute("CREATE TABLE dbo." + name + " (id int PRIMARY KEY CLUSTERED, j json, v vector(3))");
			try {
				statement.execute("INSERT INTO dbo." + name + " VALUES (1, '{\"a\":42}', '[1,2,3]')");
				statement.execute("CREATE JSON INDEX IX_json ON dbo." + name
						+ " (j) FOR ('$.a', '$.b') WITH (OPTIMIZE_FOR_ARRAY_SEARCH = ON)");
				statement.execute("CREATE VECTOR INDEX IX_vector ON dbo." + name
						+ " (v) WITH (METRIC = 'cosine', TYPE = 'DiskANN')"
						+ (filegroup == null ? "" : " ON [" + filegroup.replace("]", "]]") + "]"));
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName("dbo");
				reader.setObjectName(name);
				var indexes = reader.getAllFull(connection).getFirst().getIndexes();
				assertEquals(2, indexes.size());
				var json = indexes.stream().filter(index -> "IX_json".equals(index.getName())).findFirst()
						.orElseThrow();
				assertEquals(IndexType.Json, json.getIndexType());
				assertEquals("j", json.getColumns().getFirst().getName());
				assertEquals("[\"$.a\",\"$.b\"]", json.getSpecifics().get("json_index_paths"));
				assertEquals("true", json.getSpecifics().get("optimize_for_array_search"));
				var vector = indexes.stream().filter(index -> "IX_vector".equals(index.getName())).findFirst()
						.orElseThrow();
				assertEquals(IndexType.Vector, vector.getIndexType());
				assertEquals("v", vector.getColumns().getFirst().getName());
				assertEquals(VectorDistanceType.Cosine, vector.getVectorDistanceType());
				assertEquals(filegroup, vector.getSpecifics().get(SqlServer2000IndexReader.FILE_GROUP_NAME));
				for (var index : indexes) {
					statement.execute("DROP INDEX " + index.getName() + " ON dbo." + name);
					String sql = registry.createSql(index, SqlType.CREATE).getFirst().getSqlText();
					assertDoesNotThrow(() -> statement.execute(sql), sql);
				}
				if (filegroup != null) {
					var recreated = reader.getAllFull(connection).getFirst().getIndexes().get("IX_vector");
					assertEquals(filegroup, recreated.getSpecifics().get(SqlServer2000IndexReader.FILE_GROUP_NAME));
				}
			} finally {
				statement.execute("DROP TABLE dbo." + name);
			}
		}
	}

	@Test
	void nativeTypesAreRegisteredOnlyFrom2025AndVectorDdlUsesDimensions() {
		var old = DialectHolder.defaultDialect2022;
		var dialect = SqlServerDialectResolver.instance.getDialect(17, 0);
		var json = new Column("document");
		assertTrue(dialect.setDbType("json", null, null, json));
		assertEquals(DataType.JSON, json.getDataType());
		assertEquals("JSON", dialect.createSqlBuilder().addTypeDefinition(json).toString());
		old.setDbType("json", null, null, json);
		assertEquals(DataType.OTHER, json.getDataType());

		var vector = new Column("embedding").setDataType(DataType.VECTOR).setVectorDimension(3)
				.setVectorElementDataType(DataType.REAL);
		assertEquals("VECTOR(3)", dialect.createSqlBuilder().addTypeDefinition(vector).toString());
		assertThrows(IllegalArgumentException.class, () -> old.createSqlBuilder().addTypeDefinition(vector));
		vector.setVectorDimension(null);
		assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder().addTypeDefinition(vector));
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "SQLAPP_TEST_SQLSERVER_2025_URL", matches = ".+")
	void roundTripsNativeValuesAndCatalogDimensions() throws Exception {
		var dialect = DialectHolder.defaultDialect2025;
		String name = "native_types_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = DriverManager.getConnection(System.getenv("SQLAPP_TEST_SQLSERVER_2025_URL"), "sa",
				System.getenv("SQLAPP_TEST_SQLSERVER_2025_PASSWORD")); var statement = connection.createStatement()) {
			Table model = new Table(name).setSchemaName("dbo");
			model.getColumns().add(new Column("id").setDataType(DataType.INT));
			model.getColumns().add(new Column("j").setDataType(DataType.JSON));
			model.getColumns().add(new Column("v").setDataType(DataType.VECTOR).setVectorDimension(3));
			for (var op : dialect.createSqlFactoryRegistry().createSql(model, SqlType.CREATE))
				statement.execute(op.getSqlText());
			try {
				var json = dialect.getDbDataType(model.getColumns().get("j")).getJdbcTypeHandler();
				var vector = dialect.getDbDataType(model.getColumns().get("v")).getJdbcTypeHandler();
				try (var insert = connection.prepareStatement("INSERT INTO dbo." + name + "(id,j,v) VALUES (?,?,?)")) {
					insert.setInt(1, 1);
					json.setObject(insert, 2, "{\"value\":42}");
					vector.setObject(insert, 3, "[1.25,-2.5,3.0]");
					assertEquals(1, insert.executeUpdate());
					insert.setInt(1, 2);
					json.setObject(insert, 2, null);
					vector.setObject(insert, 3, null);
					assertEquals(1, insert.executeUpdate());
				}
				try (var rows = statement.executeQuery("SELECT j,v FROM dbo." + name + " ORDER BY id")) {
					assertTrue(rows.next());
					assertEquals("{\"value\":42}", json.getObject(rows, "j"));
					assertEquals("[1.25, -2.5, 3.0]", vector.getObject(rows, 2));
					assertTrue(rows.next());
					assertNull(json.getObject(rows, 1));
					assertNull(vector.getObject(rows, "v"));
				}
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
				assertInstanceOf(SqlServer2025ColumnReader.class, reader);
				reader.setSchemaName("dbo");
				reader.setObjectName(name);
				var columns = reader.getAll(connection);
				var readVector = columns.stream().filter(c -> c.getName().equals("v")).findFirst().orElseThrow();
				assertEquals(DataType.VECTOR, readVector.getDataType());
				assertEquals(3, readVector.getVectorDimension());
				assertNull(readVector.getLength());
				assertEquals("VECTOR(3)", dialect.createSqlBuilder().addTypeDefinition(readVector).toString());
				assertEquals(DataType.JSON,
						columns.stream().filter(c -> c.getName().equals("j")).findFirst().orElseThrow().getDataType());
			} finally {
				statement.execute("DROP TABLE dbo." + name);
			}
		}
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "SQLAPP_TEST_SQLSERVER_2025_URL", matches = ".+")
	void nativeBulkCopyPreservesJsonVectorAndNull() throws Exception {
		var dialect = DialectHolder.defaultDialect2025;
		String name = "bulk_native_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = DriverManager.getConnection(System.getenv("SQLAPP_TEST_SQLSERVER_2025_URL"), "sa",
				System.getenv("SQLAPP_TEST_SQLSERVER_2025_PASSWORD")); var statement = connection.createStatement()) {
			Table table = new Table(name).setSchemaName("dbo");
			table.getColumns().add(new Column("id").setDataType(DataType.INT));
			table.getColumns().add(new Column("j").setDataType(DataType.JSON));
			table.getColumns().add(new Column("v").setDataType(DataType.VECTOR).setVectorDimension(3));
			table.getRows().add(row -> {
				row.put("id", 1);
				row.put("j", "{\"value\":\"\u65e5\u672c\ud83d\ude00\"}");
				row.put("v", "[1.25,-2.5,3.0]");
			});
			table.getRows().add(row -> row.put("id", 2));
			statement.execute(
					dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).getFirst().getSqlText());
			try {
				assertEquals(2, com.sqlapp.jdbc.bulk.BulkInsertResolver.resolve(dialect).execute(connection, table,
						com.sqlapp.jdbc.bulk.BulkOption.defaults()));
				try (var rows = statement.executeQuery("SELECT j,v FROM dbo." + name + " ORDER BY id")) {
					var vector = dialect.getDbDataType(table.getColumns().get("v")).getJdbcTypeHandler();
					assertTrue(rows.next());
					assertEquals("{\"value\":\"\u65e5\u672c\ud83d\ude00\"}", rows.getString(1));
					assertEquals("[1.25, -2.5, 3.0]", vector.getObject(rows, 2));
					assertTrue(rows.next());
					assertNull(rows.getString(1));
					assertNull(vector.getObject(rows, 2));
				}
			} finally {
				statement.execute("DROP TABLE dbo." + name);
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "v1", "v2" })
	@EnabledIfEnvironmentVariable(named = "SQLAPP_TEST_SQLSERVER_2025_FLOAT16", matches = "true")
	void roundTripsHalfPrecisionVectorAndCatalogBaseType(String vectorSupport) throws Exception {
		var dialect = DialectHolder.defaultDialect2025;
		String name = "float16_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = DriverManager.getConnection(
				System.getenv("SQLAPP_TEST_SQLSERVER_2025_URL") + ";vectorTypeSupport=" + vectorSupport, "sa",
				System.getenv("SQLAPP_TEST_SQLSERVER_2025_PASSWORD")); var statement = connection.createStatement()) {
			var table = new Table(name).setSchemaName("dbo");
			var column = new Column("v").setDataType(DataType.VECTOR).setVectorDimension(3);
			column.getSpecifics().put("vector_base_type", "FLOAT16");
			table.getColumns().add(column);
			statement.execute(
					dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).getFirst().getSqlText());
			try {
				var handler = dialect.getDbDataType(column).getJdbcTypeHandler();
				try (var insert = connection.prepareStatement("INSERT INTO dbo." + name + " VALUES (?)")) {
					handler.setObject(insert, 1, "[1.25,-2.5,3.0]");
					assertEquals(1, insert.executeUpdate());
				}
				try (var rows = statement.executeQuery("SELECT v FROM dbo." + name)) {
					assertTrue(rows.next());
					String text = (String) handler.getObject(rows, "v");
					double[] values = Arrays.stream(text.substring(1, text.length() - 1).split(","))
							.mapToDouble(Double::parseDouble).toArray();
					assertArrayEquals(new double[] { 1.25, -2.5, 3.0 }, values);
				}
				assertThrows(IllegalArgumentException.class,
						() -> new com.sqlapp.data.db.dialect.sqlserver.bulk.BulkData(table, null));

				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
				reader.setSchemaName("dbo");
				reader.setObjectName(name);
				var read = reader.getAll(connection).getFirst();
				assertEquals(3, read.getVectorDimension());
				assertEquals("FLOAT16", read.getSpecifics().get("vector_base_type"));
				assertEquals("VECTOR(3, FLOAT16)", dialect.createSqlBuilder().addTypeDefinition(read).toString());
			} finally {
				statement.execute("DROP TABLE dbo." + name);
			}
		}
	}
}
