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

class VirticaColumnReaderTest {
	@Test
	void temporalPrecisionUsesDatetimeFieldRatherThanNumericScale() throws Exception {
		var reader = new Reader();
		for (Integer precision : new Integer[] { null, 4 }) {
			var row = new ExResultSet(null) {
				boolean missing;

				@Override
				public String getString(String name) {
					return switch (name) {
					case "COLUMN_NAME" -> "occurred";
					case "DATA_TYPE" -> "timestamptz";
					case "IS_NULLABLE" -> "true";
					case "IS_IDENTITY" -> "false";
					default -> null;
					};
				}

				@Override
				public long getLong(String name) {
					missing = true;
					return 0;
				}

				@Override
				public int getInt(String name) {
					Integer value = switch (name) {
					case "NUMERIC_SCALE" -> Integer.valueOf(0);
					case "DATETIME_PRECISION" -> precision;
					default -> null;
					};
					missing = value == null;
					return missing ? 0 : value;
				}

				@Override
				public boolean wasNull() {
					return missing;
				}
			};
			var column = reader.createColumn(row);
			assertEquals(precision, column.getScale());
			assertEquals(precision == null ? null : precision.longValue(), column.getLength());
		}
	}

	private static class Reader extends VirticaColumnReader {
		int queries;
		Long cache = 20L;

		Reader() {
			super(DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0));
		}

		ExResultSet row(int id) {
			return new ExResultSet(null) {
				boolean missing;

				@Override
				public String getString(String name) {
					return switch (name) {
					case "COLUMN_NAME" -> "id" + id;
					case "DATA_TYPE" -> "int";
					case "IS_NULLABLE" -> "false";
					case "IS_IDENTITY", "ALLOW_CYCLE" -> "true";
					case "TABLE_NAME" -> "items";
					case "TABLE_SCHEMA" -> "app";
					case "SEQUENCE_NAME" -> "items_seq";
					default -> null;
					};
				}

				@Override
				public long getLong(String name) {
					Long value = switch (name) {
					case "NUMERIC_PRECISION" -> 19L;
					case "SESSION_CACHE_COUNT" -> cache;
					case "INCREMENT_BY", "MINIMUM" -> 1L;
					case "MAXIMUM" -> Long.MAX_VALUE;
					case "CURRENT_VALUE" -> 4000L;
					default -> null;
					};
					missing = value == null;
					return missing ? 0 : value;
				}

				@Override
				public int getInt(String name) {
					missing = true;
					return 0;
				}

				@Override
				public boolean wasNull() {
					return missing;
				}
			};
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (int i = 0; i < 3; i++)
					h.handleResultSetNext(row(i));
			} catch (java.sql.SQLException e) {
				throw new IllegalStateException(e);
			}
			return null;
		}
	}

	@Test
	void identityAllocationAndCacheSurviveBulkReadAndTableXml() throws Exception {
		Reader reader = new Reader();
		var columns = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertEquals(3, columns.size());
		Table table = new Table("items");
		for (Column column : columns)
			table.getColumns().add(column);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		for (Column column : restored.getColumns()) {
			assertTrue(column.isIdentity());
			assertEquals(20, column.getIdentityCacheSize());
			assertNull(column.getIdentityLastValue());
			assertNull(column.getIdentityStartValue());
			assertEquals("4000", column.getSpecifics().get("CURRENT_VALUE"));
		}
		String ddl = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry()
				.createSql(restored, com.sqlapp.data.db.sql.SqlType.CREATE).get(0).getSqlText();
		assertFalse(ddl.contains("4000"), ddl);
	}

	@Test
	void rejectsOverflowAndNegativeCacheAndPreservesSqlNull() throws Exception {
		Reader reader = new Reader();
		reader.cache = (long) Integer.MAX_VALUE + 1;
		assertThrows(ArithmeticException.class, () -> reader.createColumn(reader.row(0)));
		reader.cache = -1L;
		assertThrows(IllegalStateException.class, () -> reader.createColumn(reader.row(0)));
		reader.cache = null;
		assertNull(reader.createColumn(reader.row(0)).getIdentityCacheSize());
	}
}
