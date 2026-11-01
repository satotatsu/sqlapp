/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class PhoenixIndexReaderTest {
	private ExResultSet row(Map<String, Object> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return (String) values.get(name);
			}

			@Override
			public Long getLongValue(String name) {
				return (Long) values.get(name);
			}

			@Override
			public Integer getInteger(String name) {
				return (Integer) values.get(name);
			}

			@Override
			public int getInt(String name) {
				return values.get(name) == null ? 0 : ((Number) values.get(name)).intValue();
			}
		};
	}

	private class Reader extends PhoenixIndexReader {
		List<Map<String, Object>> headers = new ArrayList<>(), columns = new ArrayList<>();
		int queries;

		Reader() {
			super(DialectHolder.defaultDialect);
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (var values : queries == 1 ? headers : columns)
					h.handleResultSetNext(row(values));
			} catch (java.sql.SQLException e) {
				throw new IllegalStateException(e);
			}
			return null;
		}

		SqlNode query(String resource) {
			return getSqlNodeCache().getString(resource);
		}
	}

	private Map<String, Object> identity(String tenant, String name) {
		final var values = new HashMap<String, Object>();
		values.put("catalog_name", tenant);
		values.put("schema_name", "app");
		values.put("index_name", name);
		return values;
	}

	@Test
	void readsFortyIndexesInTwoQueriesAndRetainsKeysIncludesAndPlacementThroughXml() throws Exception {
		final var reader = new Reader();
		for (String tenant : List.of("a", "b"))
			for (int i = 0; i < 20; i++) {
				var header = identity(tenant, "idx" + i);
				header.put("table_name", "table" + i);
				header.put("index_type", i % 2 == 0 ? 1 : 2);
				header.put("index_state", "a");
				if (i % 2 == 0)
					header.put("salt_buckets", 16L);
				reader.headers.add(header);
				for (int ordinal : new int[] { 3, 1, 2 }) {
					var column = identity(tenant, "idx" + i);
					column.put("column_name", ordinal == 3 ? "F:payload" : ordinal == 1 ? "F:value" : ":id");
					column.put("ordinal_position", ordinal);
					column.put("sort_order", ordinal == 1 ? -1 : 1);
					if (ordinal == 3)
						column.put("column_family", "0");
					reader.columns.add(column);
				}
			}
		final var indexes = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo().setMajorVersion(5));
		assertEquals(2, reader.queries);
		assertEquals(40, indexes.size());
		for (var index : indexes) {
			assertEquals(2, index.getColumns().size());
			assertEquals("value", index.getColumns().get(0).getName());
			assertEquals(Order.Desc, index.getColumns().get(0).getOrder());
			assertEquals(1, index.getIncludes().size());
			final var table = new Table(index.getTableName()).setCatalogName(index.getCatalogName())
					.setSchemaName(index.getSchemaName());
			table.getIndexes().add(index);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setQuateColumnName(true);
			final String sql = registry.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("F.\"value\" DESC"), sql);
			assertTrue(sql.matches("(?s).*INCLUDE \\(\\s*F\\.\"payload\"\\s*\\).*"), sql);
			assertEquals("LOCAL".equals(index.getSpecifics().get("phoenix.indexType")),
					sql.startsWith("CREATE LOCAL INDEX"), sql);
		}
	}

	@Test
	void avoidsSecondQueryForEmptySetsAndChecksVersionAndSqlBoundaries() {
		final var reader = new Reader();
		assertTrue(reader.doGetAll(null, new ParametersContext(), null).isEmpty());
		assertEquals(1, reader.queries);
		assertEquals("indexes.sql",
				PhoenixIndexReader.headerResource(new ProductVersionInfo().setMajorVersion(4).setMinorVersion(0)));
		assertEquals("indexes31.sql",
				PhoenixIndexReader.headerResource(new ProductVersionInfo().setMajorVersion(4).setMinorVersion(1)));
		for (String resource : List.of("indexes.sql", "indexes31.sql", "indexColumns.sql")) {
			final var parameters = new SqlParameterCollection();
			final var context = new ParametersContext();
			context.put("indexNames", List.of("idx"));
			reader.query(resource).eval(context, parameters);
			assertTrue(parameters.getSql().contains("TENANT_ID IS NULL"));
			if (resource.equals("indexes.sql"))
				assertFalse(parameters.getSql().contains("INDEX_TYPE AS index_type"));
		}
	}

	@Test
	void rejectsMissingAndUnrepresentableKeys() {
		for (String name : new String[] { null, ":UPPER(value)" }) {
			final var reader = new Reader();
			var header = identity("a", "idx");
			header.put("table_name", "t");
			reader.headers.add(header);
			if (name != null) {
				var column = identity("a", "idx");
				column.put("column_name", name);
				column.put("ordinal_position", 1);
				reader.columns.add(column);
			}
			assertThrows(RuntimeException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
	}
}
