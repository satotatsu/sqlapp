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
	@Test
	void retainsManagedFlagsForFortyIndexesThroughXmlWithoutExtraQueries() throws Exception {
		final var reader = new Reader();
		for (String schema : List.of("a", "b"))
			for (int i = 0; i < 20; i++) {
				header(reader, schema, "idx" + i, "INDEX");
				reader.headers.get(reader.headers.size() - 1).put("SPANNER_IS_MANAGED", i % 2 == 0);
			}
		final var indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(40, indexes.size());
		assertEquals(3, reader.queries);
		for (var index : indexes) {
			final var table = new Table("events");
			table.getIndexes().add(index);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals(index.getSpecifics().get(SpannerCreateIndexFactory.SPANNER_IS_MANAGED),
					restored.getIndexes().get(0).getSpecifics().get(SpannerCreateIndexFactory.SPANNER_IS_MANAGED));
		}
	}

	@Test
	void rejectsInvalidAndInconsistentManagedFlags() {
		for (String value : List.of("", "unknown")) {
			final var reader = new Reader();
			header(reader, "app", "idx", "INDEX");
			reader.headers.get(0).put("SPANNER_IS_MANAGED", value);
			assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
			assertEquals(1, reader.queries);
		}
		final var reader = new Reader();
		header(reader, "app", "idx", "INDEX");
		reader.headers.get(0).put("SPANNER_IS_MANAGED", true);
		final var second = new HashMap<>(reader.headers.get(0));
		second.put("ordinal_position", 2L);
		second.put("column_name", "other");
		second.put("SPANNER_IS_MANAGED", false);
		reader.headers.add(second);
		assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		assertEquals(1, reader.queries);
	}

	@Test
	void retainsFortySearchLayoutsNativeFiltersAndInterleaveThroughXmlAndDiff() throws Exception {
		final var reader = new Reader();
		for (String schema : List.of("a", "b"))
			for (int i = 0; i < 20; i++) {
				final String name = "search" + i;
				header(reader, schema, name, "SEARCH");
				final var token = reader.headers.get(reader.headers.size() - 1);
				token.put("is_unique", true); // native SEARCH header, not a UNIQUE declaration
				token.put("PARENT_TABLE_NAME", "Parents");
				token.put("FILTER", "`sort key` IS NOT NULL AND `payload one` IS NOT NULL");
				token.put("SEARCH_PARTITION_BY", new String[] { "tenant" });
				token.put("SEARCH_ORDER_BY", new String[] { "sort key" });
				final var partition = new HashMap<>(token);
				partition.put("column_name", "tenant");
				partition.put("ordinal_position", 2L);
				reader.headers.add(partition);
				final var order = new HashMap<>(token);
				order.put("column_name", "sort key");
				order.put("ordinal_position", 3L);
				order.put("column_ordering", "DESC");
				reader.headers.add(order);
				final var storing = new HashMap<>(token);
				storing.put("column_name", "payload one");
				storing.remove("ordinal_position");
				storing.remove("column_ordering");
				reader.headers.add(storing);
				option(reader, schema, name, "sort_order_sharding", "true");
			}
		Collections.reverse(reader.headers);
		final var indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(40, indexes.size());
		assertEquals(3, reader.queries);
		for (var index : indexes) {
			assertFalse(index.isUnique());
			assertEquals(List.of("embedding"), index.getColumns().stream().map(ReferenceColumn::getName).toList());
			assertEquals("tenant", index.getPartitioning().getPartitioningColumns().get(0).getName());
			final var table = new Table("events").setSchemaName(index.getSchemaName());
			table.getColumns().add(new Column("embedding").setDataType(DataType.OTHER).setDataTypeName("TOKENLIST"));
			table.getColumns().add(new Column("tenant").setDataType(DataType.BIGINT).setNotNull(true));
			table.getColumns().add(new Column("sort key").setDataType(DataType.BIGINT));
			table.getColumns().add(new Column("payload one").setDataType(DataType.VARCHAR).setLength(100));
			table.getIndexes().add(index);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			final String sql = registry.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("PARTITION BY `tenant` ORDER BY `sort key` DESC WHERE " + index.getWhere()), sql);
			assertTrue(sql.contains(", INTERLEAVE IN `" + index.getSchemaName() + "`.`Parents` OPTIONS"), sql);
			final var target = restored.clone();
			target.getIndexes().get(0).getSpecifics().put(SpannerCreateIndexFactory.SEARCH_ORDER_BY_DIRECTION, "ASC");
			final var changes = registry.createSql(restored.diff(target), SqlType.ALTER);
			assertEquals(2, changes.size());
			assertTrue(changes.get(0).getSqlText().startsWith("DROP SEARCH INDEX"));
			assertTrue(changes.get(1).getSqlText().contains("ORDER BY `sort key` ASC"));
		}
	}

	@Test
	void nativeFilterWinsWithoutGuessingFromUnavailableNullabilityAndRetainsOrdinaryInterleave() throws Exception {
		final var reader = new Reader();
		reader.available = false;
		header(reader, "app", "idx", "INDEX");
		final var values = reader.headers.get(0);
		values.put("FILTER", "embedding IS NOT NULL");
		values.put("PARENT_TABLE_NAME", "Parents");
		values.remove("base_is_nullable");
		values.remove("index_is_nullable");
		final var index = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertEquals("embedding IS NOT NULL", index.getWhere());
		final var table = new Table("events").setSchemaName("app");
		table.getIndexes().add(index);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		final String sql = registry.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("WHERE embedding IS NOT NULL, INTERLEAVE IN `app`.`Parents`"), sql);
		assertEquals(2, reader.queries);
	}

	@Test
	void rejectsMalformedMissingAndConflictingSearchLayoutMetadata() {
		for (int mode = 0; mode < 7; mode++) {
			final var reader = new Reader();
			header(reader, "app", "idx", "SEARCH");
			final var values = reader.headers.get(0);
			switch (mode) {
			case 0 -> values.put("SEARCH_PARTITION_BY", "tenant");
			case 1 -> values.put("SEARCH_PARTITION_BY", new String[] { "tenant", "tenant" });
			case 2 -> values.put("SEARCH_PARTITION_BY", new Object[] { 42 });
			case 3 -> values.put("SEARCH_PARTITION_BY", new String[] { "missing" });
			case 4 -> values.put("SEARCH_ORDER_BY", new String[] { "missing" });
			case 5 -> values.put("SEARCH_ORDER_BY", new String[] { " " });
			case 6 -> {
				final var second = new HashMap<>(values);
				second.put("FILTER", "embedding IS NOT NULL");
				second.put("ordinal_position", 2L);
				second.put("column_name", "other");
				reader.headers.add(second);
			}
			}
			assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
			assertEquals(1, reader.queries);
		}
	}

	@Test
	void releasesJdbcLayoutArraysOnSuccessAndFailure() {
		for (boolean invalid : List.of(false, true)) {
			final int[] frees = { 0 };
			final var array = (java.sql.Array) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { java.sql.Array.class }, (p, method, args) -> switch (method.getName()) {
					case "getArray" -> new String[] { invalid ? " " : "tenant" };
					case "free" -> {
						frees[0]++;
						yield null;
					}
					default -> throw new UnsupportedOperationException(method.getName());
					});
			final var reader = new Reader();
			reader.available = false;
			header(reader, "app", "idx", "SEARCH");
			reader.headers.get(0).put("SEARCH_PARTITION_BY", array);
			if (invalid) {
				assertThrows(IllegalArgumentException.class,
						() -> reader.doGetAll(null, new ParametersContext(), null));
			} else {
				final var partition = new HashMap<>(reader.headers.get(0));
				partition.put("SEARCH_PARTITION_BY", new String[] { "tenant" });
				partition.put("column_name", "tenant");
				partition.put("ordinal_position", 2L);
				reader.headers.add(partition);
				assertEquals(1, reader.doGetAll(null, new ParametersContext(), null).size());
			}
			assertEquals(1, frees[0]);
		}
	}

	@Test
	void reconstructsWhereForFortyIndexesOfEachTypeThroughXmlAndDdl() throws Exception {
		final var reader = new Reader();
		for (String type : List.of("INDEX", "SEARCH", "VECTOR"))
			for (String schema : List.of("a", "b"))
				for (int i = 0; i < 20; i++) {
					final String name = type + i;
					header(reader, schema, name, type);
					final var key = reader.headers.get(reader.headers.size() - 1);
					key.put("index_is_nullable", "NO");
					if (type.equals("SEARCH"))
						key.put("base_is_nullable", "NO");
					final var storing = new HashMap<>(key);
					storing.put("column_name", "payload one");
					storing.put("base_is_nullable", "YES");
					storing.remove("ordinal_position");
					storing.remove("column_ordering");
					reader.headers.add(storing);
					if (type.equals("VECTOR"))
						option(reader, schema, name, "distance_type", "COSINE");
				}
		final var indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(120, indexes.size());
		assertEquals(3, reader.queries);
		for (var index : indexes) {
			final String expected = (index.getIndexType() == IndexType.FullText ? "" : "`embedding` IS NOT NULL AND ")
					+ "`payload one` IS NOT NULL";
			assertEquals(expected, index.getWhere());
			final var table = new Table("events").setSchemaName(index.getSchemaName());
			final var key = new Column("embedding");
			if (index.getIndexType() == IndexType.FullText)
				key.setDataType(DataType.OTHER).setDataTypeName("TOKENLIST");
			else if (index.getIndexType() == IndexType.Vector) {
				key.setDataType(DataType.DOUBLE).setArrayDimension(1);
				key.getSpecifics().put(SpannerSqlBuilder.VECTOR_LENGTH, 8);
			} else
				key.setDataType(DataType.BIGINT);
			table.getColumns().add(key);
			table.getColumns().add(new Column("payload one").setDataType(DataType.VARCHAR).setLength(100));
			table.getIndexes().add(index);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals(expected, restored.getIndexes().get(0).getWhere());
			final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			final String sql = registry.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("WHERE " + expected), sql);
			final var target = restored.clone();
			target.getIndexes().get(0).setWhere("`payload one` IS NOT NULL");
			if (index.getIndexType() != IndexType.FullText) {
				final var changes = registry.createSql(restored.diff(target), SqlType.ALTER);
				assertEquals(2, changes.size());
				assertTrue(changes.get(0).getSqlText().startsWith("DROP"));
				assertTrue(changes.get(1).getSqlText().contains("WHERE `payload one` IS NOT NULL"));
			}
		}
	}

	@Test
	void doesNotInventFiltersFromBaseNotNullOrDuplicateGlobalNullFiltering() {
		final var reader = new Reader();
		reader.available = false;
		header(reader, "app", "not_null", "INDEX");
		reader.headers.get(0).put("base_is_nullable", "NO");
		reader.headers.get(0).put("index_is_nullable", "NO");
		header(reader, "app", "global_filter", "INDEX");
		reader.headers.get(1).put("is_null_filtered", true);
		reader.headers.get(1).put("index_is_nullable", "NO");
		final var indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertNull(indexes.get(0).getWhere());
		assertNull(indexes.get(1).getWhere());
		assertEquals(Boolean.TRUE,
				indexes.get(1).getSpecifics().get(SpannerCreateIndexFactory.IS_NULL_FILTERED, Boolean.class));
		assertEquals(2, reader.queries);
	}

	@Test
	void rejectsUnavailableOrUnknownNullabilityRatherThanDroppingWhere() {
		for (String field : List.of("base_is_nullable", "index_is_nullable"))
			for (String invalid : Arrays.asList(null, "", "unknown")) {
				final var reader = new Reader();
				header(reader, "app", "idx", "INDEX");
				reader.headers.get(0).put(field, invalid);
				assertThrows(IllegalArgumentException.class,
						() -> reader.doGetAll(null, new ParametersContext(), null));
				assertEquals(1, reader.queries);
			}
	}

	@Test
	void queryJoinsBaseColumnsOnFullIdentityAndBindsFilters() {
		final var context = new ParametersContext();
		context.put("schemaName", List.of("app' OR TRUE"));
		context.put("tableName", List.of("events"));
		context.put("indexName", List.of("idx"));
		final var parameters = new SqlParameterCollection();
		new Reader().getSqlSqlNode(null).eval(context, parameters);
		assertTrue(parameters.getSql().contains("LEFT JOIN INFORMATION_SCHEMA.COLUMNS bc"));
		assertTrue(parameters.getSql().contains("bc.TABLE_CATALOG=cc.TABLE_CATALOG"));
		assertTrue(parameters.getSql().contains("bc.TABLE_SCHEMA=cc.TABLE_SCHEMA"));
		assertTrue(parameters.getSql().contains("bc.TABLE_NAME=cc.TABLE_NAME"));
		assertTrue(parameters.getSql().contains("bc.COLUMN_NAME=cc.COLUMN_NAME"));
		assertFalse(parameters.getSql().contains("app' OR TRUE"));
		assertEquals(3, parameters.getParameterSize());
	}

	@Test
	void retainsTrueHeaderFlagsThroughXmlAndDdl() throws Exception {
		final var reader = new Reader();
		reader.available = false;
		header(reader, "app", "idx", "INDEX");
		reader.headers.get(0).put("is_unique", true);
		reader.headers.get(0).put("is_null_filtered", true);
		final var index = reader.doGetAll(null, new ParametersContext(), null).get(0);
		final var table = new Table("events");
		table.getIndexes().add(index);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertTrue(restored.getIndexes().get(0).isUnique());
		assertEquals(Boolean.TRUE, restored.getIndexes().get(0).getSpecifics()
				.get(SpannerCreateIndexFactory.IS_NULL_FILTERED, Boolean.class));
		final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("UNIQUE") && sql.contains("NULL_FILTERED"), sql);
		assertEquals(2, reader.queries);
	}

	@Test
	void rejectsConflictingAttributesBeforeReadingOptions() {
		for (String field : List.of("index_type", "is_unique", "is_null_filtered")) {
			final var reader = new Reader();
			header(reader, "app", "idx", "INDEX");
			final var second = new HashMap<>(reader.headers.get(0));
			second.put("column_name", "other");
			second.put("ordinal_position", 2L);
			second.put(field, field.equals("index_type") ? "SEARCH" : true);
			reader.headers.add(second);
			assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
			assertEquals(1, reader.queries);
		}
	}

	@Test
	void rejectsMissingOrInvalidHeaderAttributes() {
		for (String field : List.of("index_type", "is_unique", "is_null_filtered"))
			for (String invalid : Arrays.asList(null, "", "unknown")) {
				final var reader = new Reader();
				header(reader, "app", "idx", "INDEX");
				reader.headers.get(0).put(field, invalid);
				assertThrows(IllegalArgumentException.class,
						() -> reader.doGetAll(null, new ParametersContext(), null));
				assertEquals(1, reader.queries);
			}
	}

	@Test
	void reordersFortyCompositeIndexesAndSeparatesStoringThroughXmlAndDdl() throws Exception {
		final var reader = new Reader();
		reader.available = false;
		for (String schema : List.of("a", "b"))
			for (int i = 0; i < 20; i++) {
				header(reader, schema, "idx" + i, "INDEX");
				final var first = reader.headers.get(reader.headers.size() - 1);
				first.put("column_name", "later");
				first.put("ordinal_position", 2L);
				first.put("column_ordering", "DESC");
				final var storing = new HashMap<>(first);
				storing.put("column_name", "payload");
				storing.remove("ordinal_position");
				storing.remove("column_ordering");
				reader.headers.add(storing);
				final var second = new HashMap<>(first);
				second.put("column_name", "first");
				second.put("ordinal_position", 1L);
				second.put("column_ordering", "ASC");
				reader.headers.add(second);
			}
		final var indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(40, indexes.size());
		assertEquals(2, reader.queries);
		for (var index : indexes) {
			assertEquals(List.of("first", "later"), index.getColumns().stream().map(ReferenceColumn::getName).toList());
			assertEquals(List.of("payload"), index.getIncludes().stream().map(ReferenceColumn::getName).toList());
			final var table = new Table(index.getTableName()).setSchemaName(index.getSchemaName());
			table.getIndexes().add(index);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals(Order.Desc, restored.getIndexes().get(0).getColumns().get(1).getOrder());
			final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("`first`") && sql.contains("`later` DESC"), sql);
			assertTrue(sql.contains("STORING") && sql.contains("`payload`"), sql);
		}
	}

	@Test
	void rejectsMalformedIndexColumnsBeforeOptionQueries() {
		for (int mode = 0; mode < 10; mode++) {
			final var reader = new Reader();
			header(reader, "app", "idx", "INDEX");
			final var first = reader.headers.get(0);
			final var second = new HashMap<>(first);
			second.put("ordinal_position", 2L);
			second.put("column_name", "other");
			switch (mode) {
			case 0 -> second.put("ordinal_position", 0L);
			case 1 -> second.put("ordinal_position", 1L);
			case 2 -> second.put("ordinal_position", 3L);
			case 3 -> second.put("column_name", "embedding");
			case 4 -> second.put("column_name", " ");
			case 5 -> second.remove("table_name");
			case 6 -> second.put("table_name", "other");
			case 7 -> second.remove("column_ordering");
			case 8 -> second.put("column_ordering", "unknown");
			case 9 -> {
				second.remove("ordinal_position");
				second.put("column_name", "embedding");
			}
			}
			reader.headers.add(second);
			assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
			assertEquals(1, reader.queries);
		}
	}

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
		header.put("base_is_nullable", "YES");
		header.put("index_is_nullable", "YES");
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
