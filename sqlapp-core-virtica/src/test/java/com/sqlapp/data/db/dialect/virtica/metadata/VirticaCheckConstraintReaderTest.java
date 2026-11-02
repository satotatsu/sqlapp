/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class VirticaCheckConstraintReaderTest {
	private static class Reader extends VirticaCheckConstraintReader {
		int queries;

		Reader(int major, int minor) {
			super(DialectResolver.getInstance().getDialect("Vertica", major, minor, 0));
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (int i = 0; i < 2; i++) {
					final int id = i;
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name.toUpperCase(java.util.Locale.ROOT)) {
							case "TABLE_SCHEMA" -> "app";
							case "TABLE_NAME" -> "items" + id;
							case "CONSTRAINT_NAME" -> "positive";
							case "PREDICATE" -> "amount > " + id;
							default -> null;
							};
						}

						@Override
						public boolean getBoolean(String name) {
							return id == 0;
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
	void bulkChecksRetainExecutablePredicateAndEnforcementThroughXml() throws Exception {
		for (int major : new int[] { 7, 8, 11, 25 }) {
			Reader reader = new Reader(major, major == 7 ? 2 : 0);
			var checks = reader.doGetAll(null, new ParametersContext(), null);
			assertEquals(1, reader.queries);
			assertEquals(2, checks.size());
			for (int i = 0; i < 2; i++) {
				var check = checks.get(i);
				Table table = new Table(check.getTableName());
				table.setSchemaName(check.getSchemaName());
				table.getConstraints().add(check);
				Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
				var restoredCheck = restored.getConstraints().getCheckConstraints().get(0);
				assertEquals("amount > " + i, restoredCheck.getExpression());
				assertEquals(i == 0, restoredCheck.isEnable());
				String sql = DialectResolver.getInstance().getDialect("Vertica", major, major == 7 ? 2 : 0, 0)
						.createSqlFactoryRegistry().createSql(restored, com.sqlapp.data.db.sql.SqlType.CREATE).get(0)
						.getSqlText();
				assertTrue(sql.contains("amount > " + i), sql);
				assertTrue(sql.contains(i == 0 ? "ENABLED" : "DISABLED"), sql);
			}
		}
	}
}
