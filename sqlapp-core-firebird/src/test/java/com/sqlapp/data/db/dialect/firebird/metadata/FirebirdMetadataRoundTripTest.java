/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class FirebirdMetadataRoundTripTest {
	@Test
	void retainsGeneratedUniqueIndexNamesInOneQueryWithoutReusingThemInDdl() {
		int[] queries = { 0 };
		var reader = new FirebirdUniqueConstraintReader(DialectHolder.defaultDialect25) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				for (int i = 0; i < 50; i++)
					for (int column = 0; column < 2; column++)
						emit(handler, Map.of("TABLE_NAME", "orders", "CONSTRAINT_NAME", "key_" + i, "COLUMN_NAME",
								"ID" + column, "CONSTRAINT_TYPE", "PRIMARY KEY", "INDEX_NAME", "RDB$PRIMARY" + i));
				return null;
			}
		};
		var constraints = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, queries[0]);
		assertEquals(50, constraints.size());
		for (var constraint : constraints) {
			assertEquals(2, constraint.getColumns().size());
			assertTrue(constraint.getSpecifics().get("firebird.catalog.indexName").startsWith("RDB$PRIMARY"));
			assertFalse(constraint.getSpecifics().containsKey("firebird.indexName"));
			Table table = new Table("orders");
			table.getColumns().add(
					new com.sqlapp.data.schemas.Column("ID0").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
			table.getColumns().add(
					new com.sqlapp.data.schemas.Column("ID1").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
			table.getConstraints().add(constraint);
			String ddl = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(constraint, SqlType.CREATE)
					.get(0).getSqlText();
			assertFalse(ddl.contains("RDB$PRIMARY"), ddl);
			assertTrue(DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(table, SqlType.ANALYZE)
					.get(0).getSqlText().contains("RDB$PRIMARY"));
		}
	}

	@Test
	void retainsGeneratedForeignIndexNamesInOneQuery() {
		int[] queries = { 0 };
		var reader = new FirebirdForeignKeyConstraintReader(DialectHolder.defaultDialect25) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				for (int i = 0; i < 50; i++)
					for (int column = 0; column < 2; column++) {
						var values = new java.util.HashMap<String, Object>();
						values.putAll(Map.of("PKTABLE_NAME", "parent", "PKCOLUMN_NAME", "P" + column, "FKTABLE_NAME",
								"child", "FKCOLUMN_NAME", "C" + column, "FK_NAME", "fk_" + i, "INDEX_NAME",
								"RDB$FOREIGN" + i, "DEFERRABLE", "NO", "INITIALLY_DEFERRED", "NO"));
						emit(handler, values);
					}
				return null;
			}
		};
		var constraints = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, queries[0]);
		assertEquals(50, constraints.size());
		for (var constraint : constraints) {
			assertEquals(2, constraint.getColumns().size());
			assertTrue(constraint.getSpecifics().get("firebird.catalog.indexName").startsWith("RDB$FOREIGN"));
			assertFalse(constraint.getSpecifics().containsKey("firebird.indexName"));
		}
	}

	@Test
	void readsAllArrayDimensionsInOneAdditionalBulkQuery() {
		int[] queries = { 0 };
		var reader = new FirebirdColumnReader(DialectHolder.defaultDialect25) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				for (int i = 0; i < 50; i++) {
					if (queries[0] == 1) {
						emit(handler, Map.of("TABLE_NAME", "orders", "COLUMN_NAME", "array_" + i, "FIELD_TYPE", 8,
								"FIELD_LENGTH", 4, "ARRAY_DIMENSIONS", 2));
					} else {
						for (int dimension = 0; dimension < 2; dimension++)
							emit(handler, Map.of("TABLE_NAME", "orders", "COLUMN_NAME", "array_" + i,
									"DIMENSION_NUMBER", dimension, "LOWER_BOUND", -2, "UPPER_BOUND", 3));
					}
				}
				return null;
			}
		};
		var columns = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(50, columns.size());
		assertEquals(2, queries[0]);
		for (var column : columns)
			assertEquals("-2:3,-2:3", column.getSpecifics().get("firebird.array.bounds"));
	}

	@Test
	void recreatesComputedPartialIndexFromOneCatalogRead() {
		int[] queries = { 0 };
		var reader = new Firebird50IndexReader(DialectHolder.defaultDialect50) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				emit(handler, Map.of("INDEX_NAME", "ix_customer", "TABLE_NAME", "orders", "EXPRESSION_SOURCE",
						"(UPPER(customer))", "IS_DESC", true, "CONDITION_SOURCE", "WHERE customer IS NOT NULL"));
				return null;
			}
		};
		var indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, queries[0]);
		assertEquals(IndexType.Function, indexes.get(0).getIndexType());
		var table = new Table("orders");
		table.getIndexes().add(indexes.get(0));
		String sql = DialectHolder.defaultDialect50.createSqlFactoryRegistry().createSql(indexes.get(0), SqlType.CREATE)
				.get(0).getSqlText();
		assertTrue(sql.contains("DESCENDING INDEX"), sql);
		assertTrue(sql.contains("COMPUTED BY ((UPPER(customer)))"), sql);
		assertTrue(sql.contains("WHERE customer IS NOT NULL"), sql);
	}

	@Test
	void recreatesDatabaseTriggerAndHonorsReadStatement() {
		var reader = new FirebirdTriggerReader(DialectHolder.defaultDialect30) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				emit(handler, Map.of("RDB$TRIGGER_NAME", "connect_audit", "RDB$TRIGGER_TYPE", 8192L,
						"RDB$TRIGGER_SOURCE", "AS BEGIN END", "RDB$TRIGGER_SEQUENCE", 7));
				return null;
			}
		};
		reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
		var trigger = reader.doGetAll(null, new ParametersContext(), null).get(0);
		String sql = DialectHolder.defaultDialect30.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE).get(0)
				.getSqlText();
		assertFalse(sql.contains("FOR"), sql);
		assertTrue(sql.contains("ON CONNECT"), sql);
		assertTrue(sql.contains("POSITION 7"), sql);
		assertTrue(sql.contains("BEGIN END"), sql);
		reader.getReaderOptions().setReadStatement(false);
		assertTrue(reader.doGetAll(null, new ParametersContext(), null).get(0).getStatement().isEmpty());
	}

	private static void emit(ResultSetNextHandler handler, Map<String, Object> input) {
		var values = new java.util.TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
		values.putAll(input);
		var row = new ExResultSet(null) {
			@Override
			public boolean contains(String key) {
				return true;
			}

			@Override
			public short getShort(String key) {
				return values.get(key) == null ? 0 : ((Number) values.get(key)).shortValue();
			}

			@Override
			public String getString(String key) {
				return values.get(key) == null ? null : values.get(key).toString();
			}

			@Override
			public int getInt(String key) {
				return values.get(key) == null ? 0 : ((Number) values.get(key)).intValue();
			}

			@Override
			public long getLong(String key) {
				return values.get(key) == null ? 0L : ((Number) values.get(key)).longValue();
			}

			@Override
			public boolean getBoolean(String key) {
				return Boolean.TRUE.equals(values.get(key));
			}

			@Override
			public Object getObject(String key) {
				return values.get(key);
			}
		};
		try {
			handler.handleResultSetNext(row);
		} catch (SQLException e) {
			throw new AssertionError(e);
		}
	}
}
