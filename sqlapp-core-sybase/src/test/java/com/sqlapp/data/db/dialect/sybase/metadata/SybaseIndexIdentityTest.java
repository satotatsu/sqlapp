/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sybase.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SybaseIndexIdentityTest {
	private static void rows(ResultSetNextHandler handler) {
		try {
			for (String table : new String[] { "first", "second" }) {
				handler.handleResultSetNext(new ExResultSet(null) {
					@Override
					public int getInt(String name) {
						return 0;
					}

					@Override
					public String getString(String name) {
						return switch (name) {
						case "catalog_name" -> "db";
						case "schema_name" -> "dbo";
						case "table_name" -> table;
						case "index_name" -> "shared";
						case "column_name" -> table + "_column";
						default -> null;
						};
					}
				});
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void keepsTableScopedIndexesSeparate() {
		var reader = new SybaseIndexReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				rows(handler);
				return null;
			}

			@Override
			protected Index createIndex(ExResultSet rs) throws SQLException {
				return new Index(rs.getString("index_name")).setTableName(rs.getString("table_name"));
			}
		};
		var result = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(2, result.size());
		for (var index : result) {
			assertEquals(1, index.getColumns().size());
			assertEquals(index.getTableName() + "_column", index.getColumns().get(0).getName());
		}
	}

	@Test
	void keepsTableScopedUniqueIndexesSeparate() {
		var reader = new SybaseUniqueConstraintReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				rows(handler);
				return null;
			}

			@Override
			protected UniqueConstraint createUniqueConstraint(ExResultSet rs) throws SQLException {
				var constraint = new UniqueConstraint(rs.getString("index_name"));
				constraint.setTableName(rs.getString("table_name"));
				return constraint;
			}
		};
		var result = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(2, result.size());
		for (var index : result) {
			assertEquals(1, index.getColumns().size());
			assertEquals(index.getTableName() + "_column", index.getColumns().get(0).getName());
		}
	}
}
