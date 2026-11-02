/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlIndexReader;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class MariadbIndexReaderTest {
	private static class Reader extends MySqlIndexReader {
		int queries;
		String ignored;
		Reader(int minor, String ignored) {
			super(DialectResolver.getInstance().getDialect("MariaDB", 10, minor, 0));
			this.ignored = ignored;
		}
		List<Index> read() { return doGetAll(null, new ParametersContext(), null); }
		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (String table : List.of("orders", "archive")) {
					for (int position = 1; position <= 2; position++) {
						final int ordinal = position;
						h.handleResultSetNext(new ExResultSet(null) {
							@Override public String getString(String name) {
								return switch (name.toUpperCase(java.util.Locale.ROOT)) {
								case "TABLE_CATALOG" -> "def";
								case "TABLE_SCHEMA" -> "app";
								case "TABLE_NAME" -> table;
								case "INDEX_NAME" -> "idx_payload";
								case "COLUMN_NAME" -> ordinal == 1 ? "payload" : "id";
								case "INDEX_TYPE" -> "BTREE";
								case "IGNORED" -> ignored;
								case "ASC_OR_DESC" -> ordinal == 1 ? "D" : "A";
								default -> null;
								};
							}
							@Override public int getInt(String name) { return 1; }
							@Override public long getLong(String name) { return 12; }
							@Override public boolean wasNull() { return ordinal != 1; }
						});
					}
				}
			} catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
			return null;
		}
	}

	@Test
	void ignoredCompositeIndexesRoundTripInOneQuery() throws Exception {
		var reader = new Reader(8, "YES");
		var indexes = reader.read();
		assertEquals(1, reader.queries);
		assertEquals(2, indexes.size());
		assertNotEquals(indexes.get(0).getTableName(), indexes.get(1).getTableName());
		for (Index index : indexes) {
			assertFalse(index.isEnable());
			assertEquals("payload", index.getColumns().get(0).getName());
			assertEquals(Order.Desc, index.getColumns().get(0).getOrder());
			assertEquals(12L, index.getColumns().get(0).getLength());
			assertEquals("id", index.getColumns().get(1).getName());
			Table table = new Table(index.getTableName()).setSchemaName(index.getSchemaName());
			table.getColumns().add("payload", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR).setLength(100));
			table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
			table.getIndexes().add(index);
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			Index restoredIndex = restored.getIndexes().get(0);
			assertFalse(restoredIndex.isEnable());
			String ddl = DialectResolver.getInstance().getDialect("MariaDB", 10, 8, 0).createSqlFactoryRegistry()
					.createSql(restoredIndex, SqlType.CREATE).get(0).getSqlText();
			assertTrue(ddl.contains("IGNORED"), ddl);
			assertTrue(ddl.contains("DESC"), ddl);
			assertTrue(ddl.contains("(12)"), ddl);
		}
	}
	@Test
	void absentLegacyIgnoredColumnAndExplicitNoKeepIndexEnabled() {
		for (Reader reader : List.of(new Reader(5, null), new Reader(6, "NO"))) {
			assertTrue(reader.read().stream().allMatch(Index::isEnable));
			assertEquals(1, reader.queries);
		}
	}
}
