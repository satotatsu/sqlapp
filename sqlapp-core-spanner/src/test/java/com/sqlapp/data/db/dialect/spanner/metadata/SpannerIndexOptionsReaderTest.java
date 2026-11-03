/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateIndexFactory;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SpannerIndexOptionsReaderTest {
	private ExResultSet row(Map<String, Object> values) {
		return new ExResultSet(null) {
			private Object last;

			@Override
			public String getString(String name) {
				return Objects.toString(values.get(name), null);
			}

			@Override
			public Long getLongValue(String name) {
				return (Long) values.get(name);
			}

			@Override
			public long getLong(String name) {
				last = values.get(name);
				return last == null ? 0 : ((Number) last).longValue();
			}

			@Override
			public boolean wasNull() {
				return last == null;
			}

			@Override
			public boolean getBoolean(String name) {
				return Boolean.TRUE.equals(values.get(name));
			}

			@Override
			public Object getObject(String name) {
				return values.get(name);
			}
		};
	}

	private class Reader extends SpannerIndexReader {
		List<Map<String, Object>> headers = new ArrayList<>(), options = new ArrayList<>();
		boolean available = true;
		int queries;

		Reader() {
			super(DialectHolder.defaultDialect);
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				final var rows = queries == 1 ? headers
						: queries == 2 ? List.<Map<String, Object>>of(Map.of("option_table_count", available ? 1L : 0L))
								: options;
				for (var values : rows)
					h.handleResultSetNext(row(values));
			} catch (java.sql.SQLException exception) {
				throw new IllegalStateException(exception);
			}
			return null;
		}
	}

	private Map<String, Object> identity(String schema, String name) {
		final var row = new HashMap<String, Object>();
		row.put("table_catalog", "catalog");
		row.put("table_schema", schema);
		row.put("index_name", name);
		return row;
	}

	private void header(Reader reader, String schema, String name, String type) {
		final var header = identity(schema, name);
		header.put("table_name", "events");
		header.put("column_name", "embedding");
		header.put("ordinal_position", 1L);
		header.put("index_type", type);
		header.put("is_unique", false);
		header.put("is_null_filtered", false);
		header.put("column_ordering", "ASC");
		reader.headers.add(header);
	}

	private void option(Reader reader, String schema, String index, String name, String value) {
		final var option = identity(schema, index);
		option.put("option_name", name);
		option.put("option_value", value);
		reader.options.add(option);
	}

	@Test
	void retainsFortyVectorIndexOptionsInThreeQueriesThroughXmlAndCreate() throws Exception {
		final var reader = new Reader();
		for (String schema : List.of("a", "b")) {
			for (int i = 0; i < 20; i++) {
				final String name = "idx" + i;
				header(reader, schema, name, "VECTOR");
				option(reader, schema, name, "distance_type", "COSINE");
				option(reader, schema, name, "tree_depth", "3");
				option(reader, schema, name, "num_leaves", "100");
				option(reader, schema, name, "num_branches", "10");
				option(reader, schema, name, "disable_search", "FALSE");
			}
		}
		final var indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(3, reader.queries);
		assertEquals(40, indexes.size());
		for (var index : indexes) {
			assertEquals(VectorDistanceType.Cosine, index.getVectorDistanceType());
			assertEquals("3", index.getSpecifics().get(SpannerCreateIndexFactory.TREE_DEPTH));
			assertEquals("false", index.getSpecifics().get(SpannerCreateIndexFactory.DISABLE_SEARCH));
			final var table = new Table("events").setSchemaName(index.getSchemaName());
			final var embedding = new Column("embedding").setDataType(DataType.DOUBLE).setArrayDimension(1)
					.setNotNull(true);
			embedding.getSpecifics().put(SpannerSqlBuilder.VECTOR_LENGTH, 8);
			table.getColumns().add(embedding);
			table.getIndexes().add(index);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("tree_depth = 3"), sql);
			assertTrue(sql.contains("num_leaves = 100"), sql);
			assertTrue(sql.contains("num_branches = 10"), sql);
			assertTrue(sql.contains("disable_search = false"), sql);
		}
	}

	@Test
	void preservesPlacementAndSearchFlagsAndSkipsUnavailableOrEmptySets() {
		final var reader = new Reader();
		header(reader, "app", "idx", "INDEX");
		option(reader, "app", "idx", "locality_group", "hot");
		option(reader, "app", "idx", "columnar_policy", "columnar");
		header(reader, "app", "search_idx", "SEARCH");
		option(reader, "app", "search_idx", "sort_order_sharding", "false");
		final var indexes = reader.doGetAll(null, new ParametersContext(), null);
		final var index = indexes.get(0);
		assertEquals("hot", index.getSpecifics().get(SpannerCreateIndexFactory.LOCALITY_GROUP));
		assertEquals("columnar", index.getSpecifics().get(SpannerCreateIndexFactory.COLUMNAR_POLICY));
		assertEquals("false", indexes.get(1).getSpecifics().get(SpannerCreateIndexFactory.SORT_ORDER_SHARDING));
		final var table = new Table("events");
		table.getColumns().add(new Column("embedding").setDataType(DataType.BIGINT));
		table.getIndexes().add(index);
		final String placementSql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(index, SqlType.CREATE).get(0).getSqlText();
		assertTrue(placementSql.contains("locality_group = 'hot'"), placementSql);
		assertTrue(placementSql.contains("columnar_policy = 'columnar'"), placementSql);
		final var searchTable = new Table("events");
		searchTable.getColumns().add(new Column("embedding").setDataType(DataType.OTHER).setDataTypeName("TOKENLIST"));
		searchTable.getIndexes().add(indexes.get(1));
		assertTrue(DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(indexes.get(1), SqlType.CREATE)
				.get(0).getSqlText().contains("sort_order_sharding = false"));
		final var legacy = new Reader();
		legacy.available = false;
		header(legacy, "app", "idx", "INDEX");
		assertEquals(1, legacy.doGetAll(null, new ParametersContext(), null).size());
		assertEquals(2, legacy.queries);
		final var empty = new Reader();
		assertTrue(empty.doGetAll(null, new ParametersContext(), null).isEmpty());
		assertEquals(1, empty.queries);
	}

	@Test
	void rejectsUnrepresentableDistanceAndMalformedNumericOrBooleanMetadata() {
		for (String[] bad : new String[][] { { "distance_type", "UNKNOWN" }, { "tree_depth", "many" },
				{ "num_leaves", "999999999999999999999" }, { "disable_search", "yes" } }) {
			final var reader = new Reader();
			header(reader, "app", "idx", "VECTOR");
			option(reader, "app", "idx", bad[0], bad[1]);
			final Class<? extends RuntimeException> expected = bad[0].equals("distance_type")
					? UnsupportedOperationException.class
					: IllegalArgumentException.class;
			assertThrows(expected, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
	}
}
