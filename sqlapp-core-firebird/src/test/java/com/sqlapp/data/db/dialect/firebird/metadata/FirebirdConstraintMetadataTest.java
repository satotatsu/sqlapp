/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class FirebirdConstraintMetadataTest {
	@Test void retainsConstantChecksAndAppliesTheConstraintNameFilterInOneQuery() {
		int[] queries = { 0 };
		var reader = new FirebirdCheckConstraintReader(DialectHolder.defaultDialect25) {
			@Override protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
				queries[0]++;
				for (int i = 0; i < 50; i++) {
					String name = "constant_check_" + i;
					var row = new ExResultSet(null) {
						@Override public String getString(String key) {
							return switch (key.toLowerCase(java.util.Locale.ROOT)) {
							case "constraint_name" -> name;
							case "table_name" -> "orders";
							case "rdb$trigger_source" -> "CHECK (1=1)";
							default -> null;
							};
						}
					};
					try { handler.handleResultSetNext(row); } catch (SQLException e) { throw new AssertionError(e); }
				}
				return null;
			}
		};
		var constraints = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(50, constraints.size());
		assertEquals(1, queries[0]);
		assertTrue(constraints.stream().allMatch(c -> "1=1".equals(c.getExpression())));
		var context = new ParametersContext();
		context.put("constraintName", "constant_check_0");
		context.put("readerOptions", new com.sqlapp.data.db.metadata.ReaderOptions());
		var sql = new com.sqlapp.jdbc.sql.SqlParameterCollection();
		reader.getSqlSqlNode(null).eval(context, sql);
		assertTrue(sql.getSql().contains("LEFT OUTER JOIN RDB$DEPENDENCIES"));
		assertTrue(sql.getSql().contains("CC.RDB$CONSTRAINT_NAME IN"));
		assertFalse(sql.getSql().contains("CC.RDB$CONSTRAINT_NAME NOT LIKE"));
	}
}
