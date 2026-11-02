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
							return switch (name) {
							case "CONSTRAINT_CATALOG" -> "def";
							case "CONSTRAINT_SCHEMA" -> "app";
							case "TABLE_NAME" -> table;
							case "CONSTRAINT_NAME" -> "valid_value";
							case "CHECK_CLAUSE" -> table.equals("first") ? "`value` > 0" : "`value` < 10";
							default -> null;
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
		for (int[] version : new int[][] { {10, 2, 7}, {10, 5, 0}, {11, 5, 0}, {12, 1, 0} }) {
			var reader = new Reader(version[0], version[1], version[2]);
			var constraints = reader.read();
			assertEquals(1, reader.queries);
			assertEquals(2, constraints.size());
			assertTrue(reader.sql.contains("tc.TABLE_NAME = cc.TABLE_NAME"), reader.sql);
			assertFalse(reader.sql.contains("tc.ENFORCED"), reader.sql);
			for (var constraint : constraints) {
				CheckConstraint restored = SchemaUtils.readXml(new java.io.StringReader(constraint.asXml()));
				assertEquals(constraint.getTableName(), restored.getTableName());
				assertEquals(constraint.getExpression(), restored.getExpression());
				Table table = new Table(restored.getTableName()).setSchemaName(restored.getSchemaName());
				table.getConstraints().add(restored);
				var registry = DialectResolver.getInstance().getDialect("MariaDB", version[0], version[1], version[2])
						.createSqlFactoryRegistry();
				String ddl = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
				assertTrue(ddl.contains(constraint.getExpression()), ddl);
			}
		}
	}
}
