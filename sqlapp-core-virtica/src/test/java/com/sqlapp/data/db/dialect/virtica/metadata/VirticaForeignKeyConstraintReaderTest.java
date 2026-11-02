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

class VirticaForeignKeyConstraintReaderTest {
	private static class Reader extends VirticaForeignKeyConstraintReader {
		int queries;

		Reader() {
			super(DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0));
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (int i = 0; i < 4; i++) {
					final int row = i;
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name.toUpperCase(java.util.Locale.ROOT)) {
							case "TABLE_SCHEMA" -> "app";
							case "TABLE_NAME" -> "child" + row / 2;
							case "CONSTRAINT_NAME" -> "C_FOREIGN";
							case "COLUMN_NAME" -> "fk" + row % 2;
							case "REFERENCE_TABLE_SCHEMA" -> "ref schema";
							case "REFERENCE_TABLE_NAME" -> "parent" + row / 2;
							case "REFERENCE_COLUMN_NAME" -> "pk" + row % 2;
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
	void sameNamedCompositeForeignKeysRemainSeparateWithOneQuery() throws Exception {
		Reader reader = new Reader();
		var keys = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertEquals(2, keys.size());
		for (int i = 0; i < keys.size(); i++) {
			var key = keys.get(i);
			assertEquals("child" + i, key.getTableName());
			assertEquals(2, key.getColumns().size());
			assertEquals("fk0", key.getColumns().get(0).getName());
			assertEquals("fk1", key.getColumns().get(1).getName());
			assertEquals("parent" + i, key.getRelatedColumns().get(0).getTableName());
			assertEquals("ref schema", key.getRelatedColumns().get(0).getSchemaName());
			assertEquals("pk0", key.getRelatedColumns().get(0).getName());
			assertEquals("pk1", key.getRelatedColumns().get(1).getName());
			Table table = new Table(key.getTableName());
			table.setSchemaName("app");
			table.getConstraints().add(key);
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			var restoredKey = restored.getConstraints().getForeignKeyConstraints().get(0);
			assertEquals("parent" + i, restoredKey.getRelatedColumns().get(0).getTableName());
			String sql = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry()
					.createSql(restored, com.sqlapp.data.db.sql.SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("\"ref schema\".\"parent" + i + "\""), sql);
		}
	}
}
