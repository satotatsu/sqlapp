/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class MariadbCheckConstraintReaderTest {
	private static class Reader extends MariadbCheckConstraintReader {
		int queries;
		String sql;

		Reader(int major, int minor, int revision) {
			super(DialectResolver.getInstance().getDialect("MariaDB", major, minor, revision));
		}

		java.util.List<CheckConstraint> read() {
			return doGetAll(null, new ParametersContext(), null);
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			sql = n.eval(p).getSql();
			try {
				for (String table : new String[] { "first", "second" }) {
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name.toUpperCase(java.util.Locale.ROOT)) {
							case "CONSTRAINT_CATALOG" -> "def";
							case "CONSTRAINT_SCHEMA" -> "app";
							case "TABLE_NAME" -> table;
							case "CONSTRAINT_NAME" -> "valid_value";
							case "CHECK_CLAUSE" -> table.equals("first") ? "`value` > 0" : "`value` < 10";
							case "ENFORCED" -> null;
							default -> throw new AssertionError("Unexpected metadata column: " + name);
							};
						}
					});
				}
			} catch (java.sql.SQLException e) {
				throw new IllegalStateException(e);
			}
			return null;
		}
	}

	@Test
	void qualifiesSameNamedConstraintsByTableAndRecreatesEachExpression() throws Exception {
		for (int[] version : new int[][] { { 10, 2, 7 }, { 10, 5, 0 }, { 11, 5, 0 }, { 12, 1, 0 } }) {
			var reader = new Reader(version[0], version[1], version[2]);
			var constraints = reader.read();
			assertEquals(1, reader.queries);
			assertEquals(2, constraints.size());
			assertEquals("first", constraints.get(0).getTableName());
			assertEquals("second", constraints.get(1).getTableName());
			assertTrue(reader.sql.contains("tc.TABLE_NAME = cc.TABLE_NAME"), reader.sql);
			assertFalse(reader.sql.contains("tc.ENFORCED"), reader.sql);
			for (var constraint : constraints) {
				Table owner = new Table(constraint.getTableName()).setSchemaName(constraint.getSchemaName());
				owner.getConstraints().add(constraint);
				Table table = SchemaUtils.readXml(new java.io.StringReader(owner.asXml()));
				CheckConstraint restored = (CheckConstraint) table.getConstraints().get(0);
				assertEquals(constraint.getTableName(), restored.getTableName());
				assertEquals(constraint.getExpression(), restored.getExpression());
				var registry = DialectResolver.getInstance().getDialect("MariaDB", version[0], version[1], version[2])
						.createSqlFactoryRegistry();
				String ddl = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
				assertTrue(ddl.contains(constraint.getExpression()), ddl);
			}
		}
	}
}
