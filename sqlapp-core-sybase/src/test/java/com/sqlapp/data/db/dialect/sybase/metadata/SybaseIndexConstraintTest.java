/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sybase.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SybaseIndexConstraintTest {
	private List<Map<String, Object>> rows() {
		final List<Map<String, Object>> rows = new ArrayList<>();
		for (String owner : List.of("a", "b")) {
			for (String table : List.of("first_table", "second_table")) {
				for (int i = 0; i < 2; i++) {
					final var row = new HashMap<String, Object>();
					row.put("catalog_name", "db");
					row.put("schema_name", owner);
					row.put("table_name", table);
					row.put("index_name", "shared");
					row.put("column_name", i == 0 ? "id" : "value");
					row.put("type", 1);
					row.put("is_primary_key", 1);
					row.put("is_unique", 1);
					row.put("is_descending_key", i);
					row.put("is_descending", i);
					rows.add(row);
				}
			}
		}
		return rows;
	}

	@Test
	void keepsCompositeConstraintOrderAndIdentityThroughXml() throws Exception {
		final int[] queries = { 0 };
		final var reader = new SybaseUniqueConstraintReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				queries[0]++;
				SybaseReaderTestSupport.feed(h, rows());
				return null;
			}
		};
		final var constraints = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, queries[0]);
		assertEquals(4, constraints.size());
		for (var constraint : constraints) {
			final UniqueConstraint restored = SchemaUtils.readXml(new java.io.StringReader(constraint.asXml()));
			assertTrue(restored.isPrimaryKey());
			assertEquals(IndexType.Clustered, restored.getIndexType());
			assertEquals("id", restored.getColumns().get(0).getName());
			assertEquals(Order.Asc, constraint.getColumns().get(0).getOrder());
			assertNull(restored.getColumns().get(0).getOrder()); // XML omits the default ASC.
			assertEquals("value", restored.getColumns().get(1).getName());
			assertEquals(Order.Desc, restored.getColumns().get(1).getOrder());
		}
	}

	@Test
	void keepsCompositeIndexOrderAndIdentityThroughXml() throws Exception {
		final int[] queries = { 0 };
		final var reader = new SybaseIndexReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				queries[0]++;
				SybaseReaderTestSupport.feed(h, rows());
				return null;
			}
		};
		final var indexes = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, queries[0]);
		assertEquals(4, indexes.size());
		for (var index : indexes) {
			final String tableName = index.getTableName();
			final var table = new Table(tableName).setSchemaName(index.getSchemaName())
					.setCatalogName(index.getCatalogName());
			table.getIndexes().add(index);
			final Table restoredTable = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			final Index restored = restoredTable.getIndexes().get(0);
			assertTrue(restored.isUnique());
			assertEquals(IndexType.Clustered, restored.getIndexType());
			assertEquals(Order.Asc, index.getColumns().get(0).getOrder());
			assertNull(restored.getColumns().get(0).getOrder()); // XML omits the default ASC.
			assertEquals(Order.Desc, restored.getColumns().get(1).getOrder());
			assertEquals(index.getSchemaName(), restored.getSchemaName());
			assertEquals(tableName, restored.getTableName());
		}
	}
}
