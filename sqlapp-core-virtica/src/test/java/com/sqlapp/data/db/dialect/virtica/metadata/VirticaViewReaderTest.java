/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.metadata;

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

class VirticaViewReaderTest {
	private static class Reader extends VirticaViewReader {
		int queries;

		Reader() {
			super(DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0));
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (int i = 0; i < 3; i++) {
					final int id = i;
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name.toUpperCase(java.util.Locale.ROOT)) {
							case "TABLE_NAME" -> "view" + id;
							case "TABLE_SCHEMA" -> "app";
							case "TABLE_ID" -> Integer.toString(id);
							case "VIEW_DEFINITION" -> "SELECT 1";
							default -> null;
							};
						}

						@Override
						public java.sql.Timestamp getTimestamp(String name) {
							return null;
						}

						@Override
						public Object getObject(String name) {
							return name.equals("IS_LOCAL_TEMP_VIEW") ? Boolean.TRUE : Boolean.FALSE;
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
	void bulkQueryRetainsQueryAndNativeFlagsForXmlDdl() throws Exception {
		var reader = new Reader();
		var views = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertEquals(3, views.size());
		for (Table table : views) {
			View restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals(table.getStatement(), restored.getStatement());
			assertEquals(Boolean.FALSE, restored.getSpecifics().get("INHERIT_PRIVILEGES", Boolean.class));
			String sql = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("LOCAL TEMPORARY VIEW"), sql);
			assertTrue(sql.contains("SELECT 1"), sql);
		}
	}
}
