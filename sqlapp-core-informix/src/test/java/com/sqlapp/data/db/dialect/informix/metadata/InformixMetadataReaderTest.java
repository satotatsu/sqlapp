/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.util.DoubleKeyMap;

class InformixMetadataReaderTest {
	static ExResultSet row(final Map<String, Object> values) {
		return new ExResultSet((ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
				new Class<?>[] { ResultSet.class }, (proxy, method, args) -> switch (method.getName()) {
				case "getString" -> values.get(args[0] instanceof Integer n ? switch (n) {
				case 4 -> "strategy";
				case 5 -> "partition";
				case 6 -> "fragment_expression";
				case 7 -> "dbspace";
				default -> throw new UnsupportedOperationException("column " + n);
				} : args[0]);
				case "getInt" -> values.getOrDefault(args[0], 0);
				case "getLong" -> ((Number) values.getOrDefault(args[0], 0L)).longValue();
				case "getDouble" -> ((Number) values.getOrDefault(args[0], 0)).doubleValue();
				default -> throw new UnsupportedOperationException(method.getName());
				})) {
			@Override
			public boolean contains(final String name) {
				return true;
			}
		};
	}

	static Map<String, Object> indexRow(final String owner, final String table, final String column, final int number) {
		final Map<String, Object> values = new HashMap<>();
		values.put("catalog_name", "testdb");
		values.put("schema_name", owner);
		values.put("table_name", table);
		values.put("index_name", "ix");
		values.put("constraint_name", "uq");
		values.put("index_kind", "U");
		values.put("raw_index_keys", "-2 [1], 1 [1]");
		values.put("access_method", 1);
		values.put("access_method_name", "btree");
		values.put("access_method_owner", "informix");
		values.put("column_name", column);
		values.put("column_number", number);
		return values;
	}

	private static void feed(final List<Map<String, Object>> rows, final ResultSetNextHandler handler) {
		try {
			for (final Map<String, Object> values : rows) {
				handler.handleResultSetNext(row(values));
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	static class Indexes extends InformixIndexReader {
		final List<Map<String, Object>> rows = new ArrayList<>();
		final List<Map<String, Object>> routines = new ArrayList<>();
		final List<Map<String, Object>> operatorClasses = new ArrayList<>();
		int queries;

		Indexes() {
			super(DialectHolder.defaultDialect);
			setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
		}

		@Override
		protected void query(Connection connection, String resource, ParametersContext context,
				ResultSetNextHandler handler) {
			queries++;
			if (resource.equals("indexRoutines.sql")) {
				feed(routines, handler);
			} else if (resource.equals("indexOperatorClasses.sql")) {
				feed(operatorClasses, handler);
			} else if (resource.equals("indexColumns.sql")) {
				feed(rows, handler);
			} else {
				final Map<List<Object>, Map<String, Object>> headers = new java.util.LinkedHashMap<>();
				for (var row : rows)
					headers.putIfAbsent(List.of(row.get("schema_name"), row.get("table_name"), row.get("index_name")),
							row);
				feed(new ArrayList<>(headers.values()), handler);
			}
		}

	}

	static class Tables extends InformixTableReader {
		final List<Map<String, Object>> rows = new ArrayList<>();
		int queries;

		Tables() {
			super(DialectHolder.defaultDialect);
			setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
		}

		@Override
		protected ParametersContext defaultParametersContext(Connection connection) {
			return new ParametersContext();
		}

		@Override
		protected com.sqlapp.jdbc.sql.JdbcQueryHandler execute(Connection connection,
				com.sqlapp.jdbc.sql.node.SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
			queries++;
			feed(rows, handler);
			return null;
		}

		@Override
		protected InformixIndexReader newIndexMetadataReader() {
			final Tables owner = this;
			final Indexes reader = new Indexes() {
				@Override
				protected void query(Connection connection, String resource, ParametersContext context,
						ResultSetNextHandler handler) {
					owner.queries++;
					super.query(connection, resource, context, handler);
				}
			};
			reader.rows.addAll(rows);
			return reader;
		}

	}

	@Test
	void readsManyIndexesInConstantQueriesAndKeepsOwnerAndTableIdentity() {
		final Indexes reader = new Indexes();
		for (String owner : List.of("a", "b")) {
			for (int i = 0; i < 20; i++) {
				// Deliberately deliver columns in the opposite order to the index keys.
				reader.rows.add(indexRow(owner, "items" + i, "first", 1));
				reader.rows.add(indexRow(owner, "items" + i, "second", 2));
			}
		}
		final List<Index> indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(2, reader.queries);
		assertEquals(40, indexes.size());
		for (Index index : indexes) {
			assertTrue(index.isUnique());
			assertEquals("second", index.getColumns().get(0).getName());
			assertEquals(Order.Desc, index.getColumns().get(0).getOrder());
			assertEquals("first", index.getColumns().get(1).getName());
			assertEquals(Order.Asc, index.getColumns().get(1).getOrder());
		}
	}

	@Test
	void rejectsUnresolvedSpecialIndexKeys() {
		final Indexes reader = new Indexes();
		reader.rows.add(indexRow("a", "items", "first", 1));
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void readsFunctionalKeysWithMultipleArgumentsAndMixedSortOrder() {
		final Indexes reader = new Indexes();
		for (int i = 1; i <= 3; i++) {
			final var row = indexRow("a", "items", "col" + i, i);
			row.put("raw_index_keys", "<42>(-1, 3) [1], 2 [1]");
			reader.rows.add(row);
		}
		reader.routines.add(Map.of("routine_id", 42, "routine_schema", "Odd Owner", "routine_name", "sum_pair"));
		final Index index = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertEquals(3, reader.queries);
		assertEquals(com.sqlapp.data.schemas.IndexType.Function, index.getIndexType());
		assertTrue(index.getColumns().get(0).getName().contains("sum_pair"));
		assertEquals(Order.Desc, index.getColumns().get(0).getOrder());
		assertEquals("true",
				index.getColumns().get(0).getSpecifics().get(InformixIndexReader.INFORMIX_INDEX_EXPRESSION));
		assertEquals("col2", index.getColumns().get(1).getName());
	}

	@Test
	void parsesKeysBeyondTheOldSixteenColumnView() {
		final Indexes reader = new Indexes();
		final String keys = java.util.stream.IntStream.rangeClosed(1, 17).mapToObj(i -> i + " [1]")
				.collect(java.util.stream.Collectors.joining(","));
		for (int i = 1; i <= 17; i++) {
			final var row = indexRow("a", "items", "c" + i, i);
			row.put("raw_index_keys", keys);
			reader.rows.add(row);
		}
		assertEquals(17, reader.doGetAll(null, new ParametersContext(), null).get(0).getColumns().size());
		assertEquals(2, reader.queries);
	}

	@Test
	void rejectsMalformedKeysAndInvalidOperatorClassIds() {
		for (String raw : List.of("", "1 [0]", "<42>(1,-2) [1]", "1 [1],", "1 [1] garbage", "0 [1]")) {
			assertThrows(RuntimeException.class, () -> new InformixIndexColumns(raw), raw);
		}
	}

	@Test
	void resolvesManyCustomIndexesAndOperatorClassesInConstantQueries() {
		final Indexes reader = new Indexes();
		for (int i = 0; i < 20; i++) {
			final var values = indexRow("schema", "items" + i, "text", 1);
			values.put("raw_index_keys", "-1 [109]");
			values.put("access_method", 101);
			values.put("access_method_name", "bts");
			values.put("access_parameters", "analyzer='keyword' ");
			reader.rows.add(values);
		}
		reader.operatorClasses.add(Map.of("operator_class_id", 109, "operator_class_name", "bts_varchar_ops",
				"operator_class_owner", "informix", "access_method", 101));
		final var indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(20, indexes.size());
		assertEquals(3, reader.queries);
		for (Index index : indexes) {
			assertEquals("bts", index.getSpecifics().get(InformixIndexReader.INFORMIX_ACCESS_METHOD));
			assertEquals("analyzer='keyword'",
					index.getSpecifics().get(InformixIndexReader.INFORMIX_ACCESS_PARAMETERS));
			assertEquals("bts_varchar_ops",
					index.getColumns().get(0).getSpecifics().get(InformixIndexReader.INFORMIX_OPERATOR_CLASS));
			assertEquals(Order.Desc, index.getColumns().get(0).getOrder());
		}
	}

	@Test
	void rejectsMissingOrMismatchedOperatorClassesAndAccessMethods() {
		final Indexes reader = new Indexes();
		final var values = indexRow("schema", "items", "id", 1);
		values.put("raw_index_keys", "1 [100]");
		reader.rows.add(values);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		reader.operatorClasses.add(Map.of("operator_class_id", 100, "operator_class_name", "custom_ops",
				"operator_class_owner", "informix", "access_method", 99));
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		values.remove("access_method_name");
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void readsManyUniqueConstraintsInConstantQueriesWithoutMixingOwners() throws Exception {
		final Tables reader = new Tables();
		final DoubleKeyMap<String, String, Table> tables = new DoubleKeyMap<>();
		for (String owner : List.of("a", "b")) {
			for (int i = 0; i < 20; i++) {
				final Table table = new Table("items" + i).setCatalogName("testdb").setSchemaName(owner);
				table.getColumns().add(new Column("first"));
				table.getColumns().add(new Column("second"));
				tables.put(owner, table.getName(), table);
				reader.rows.add(indexRow(owner, table.getName(), "first", 1));
				reader.rows.add(indexRow(owner, table.getName(), "second", 2));
			}
		}
		reader.setUniqueConstraints(null, tables);
		assertEquals(2, reader.queries);
		for (String owner : tables.keySet()) {
			for (String name : tables.secondKeySet()) {
				final Table table = tables.get(owner, name);
				assertEquals(1, table.getConstraints().size());
				assertEquals("second",
						table.getConstraints().getUniqueConstraints().get(0).getColumns().get(0).getName());
			}
		}
	}

	@Test
	void emptyTableSetDoesNotIssueQueries() throws Exception {
		final Tables reader = new Tables();
		reader.setUniqueConstraints(null, new DoubleKeyMap<>());
		assertEquals(0, reader.queries);
	}

	@Test
	void readsRollingPolicyAndDisabledIntervalWithoutInventingAnAction() throws Exception {
		final Tables reader = new Tables();
		final Table table = new Table("rolling");
		final Map<String, Object> header = new HashMap<>();
		header.put("strategy", "N");
		header.put("position", -4);
		header.put("fragment_flags", 40); // DISCARD ANY
		header.put("rolling_fragment_limit", 5);
		header.put("rolling_size", 100d);
		header.put("rolling_size_unit", 1024d);
		reader.readFragment(row(header), table);
		assertEquals("5", table.getPartitioning().getSpecifics().get(InformixTableReader.INFORMIX_ROLLING_FRAGMENTS));
		assertEquals("102400",
				table.getPartitioning().getSpecifics().get(InformixTableReader.INFORMIX_ROLLING_SIZE_KB));
		assertEquals("DISCARD",
				table.getPartitioning().getSpecifics().get(InformixTableReader.INFORMIX_ROLLING_ACTION));
		assertEquals("ANY", table.getPartitioning().getSpecifics().get(InformixTableReader.INFORMIX_ROLLING_SCOPE));
		header.put("position", -2);
		header.put("fragment_flags", 3072);
		header.put("fragment_expression", "10");
		reader.readFragment(row(header), table);
		assertEquals("true",
				table.getPartitioning().getSpecifics().get(InformixTableReader.INFORMIX_INTERVAL_DISABLED));
		header.put("position", -4);
		header.put("fragment_flags", 128);
		assertThrows(UnsupportedOperationException.class, () -> reader.readFragment(row(header), new Table("unknown")));
	}

	@Test
	void retainsConstraintOwnerSeparatelyFromTheTableSchemaInOneBatch() {
		final Tables reader = new Tables();
		final var values = indexRow("table_owner", "items", "first", 1);
		values.put("constraint_owner", "actual_owner");
		values.put("constraint_state", "D");
		reader.rows.add(values);
		final Table table = new Table("items").setSchemaName("table_owner").setCatalogName("testdb");
		final var constraint = new com.sqlapp.data.schemas.UniqueConstraint("uq", false);
		table.getConstraints().add(constraint);
		final var tables = new DoubleKeyMap<String, String, Table>();
		tables.put("table_owner", "items", table);
		reader.setConstraintOwners(null, tables);
		assertEquals(1, reader.queries);
		assertEquals("actual_owner",
				constraint.getSpecifics().get(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER));
		assertFalse(constraint.isEnable());
	}

	@Test
	void readsIndexModesForMultipleTablesInTwoQueries() {
		final Indexes reader = new Indexes();
		final var modes = java.util.Arrays.asList("E", "D", "F", "G", null);
		for (int i = 0; i < modes.size(); i++) {
			for (int column = 1; column <= 2; column++) {
				final var values = indexRow("owner", "items" + i, "c" + column, column);
				values.put("index_state", modes.get(i));
				reader.rows.add(values);
			}
		}
		final var indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(2, reader.queries);
		assertEquals(5, indexes.size());
		for (int i = 0; i < indexes.size(); i++) {
			assertEquals(i != 1, indexes.get(i).isEnable());
			final String mode = indexes.get(i).getSpecifics()
					.get(com.sqlapp.data.db.dialect.informix.util.InformixIndexModes.MODE);
			if (i == 2)
				assertEquals("FILTERING WITHOUT ERROR", mode);
			else if (i == 3)
				assertEquals("FILTERING WITH ERROR", mode);
			else
				assertNull(mode);
		}
	}
}
