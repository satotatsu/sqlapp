/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * Populated disposable tables only; maintenance is explicitly performed by the
 * test.
 */
final class Db2OrdinaryAlterAssertions {
	private static final String NAME = "SQLAPP_ORDINARY_ALTER";

	private Db2OrdinaryAlterAssertions() {
	}

	static void verify(Connection connection) throws Exception {
		Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		Deque<String> cleanup = new ArrayDeque<>();
		Throwable failure = null;
		try (Statement statement = connection.createStatement()) {
			try {
				statement.execute("CREATE SCHEMA " + NAME);
				cleanup.push("DROP SCHEMA " + NAME + " RESTRICT");
				statement.execute("CREATE TABLE " + NAME + ".T (ID INTEGER, V INTEGER, TXT VARCHAR(10))");
				cleanup.push("DROP TABLE " + NAME + ".T");
				statement.execute("INSERT INTO " + NAME + ".T VALUES (1,7,'keep')");
				Schema before = read(connection, dialect), after = before.clone();
				Table table = after.getTables().get("T");
				table.setRemarks("table's comment");
				table.getColumns().get("V").setDefaultValue("9").setRemarks("column's comment");
				table.getColumns().get("TXT").setLength(20L);
				apply(statement, dialect, before, after, true);
				Schema actual = read(connection, dialect);
				assertEquals("9", actual.getTables().get("T").getColumns().get("V").getDefaultValue());
				assertEquals("table's comment", actual.getTables().get("T").getRemarks());
				assertEquals("column's comment", actual.getTables().get("T").getColumns().get("V").getRemarks());
				assertEquals(20L, actual.getTables().get("T").getColumns().get("TXT").getLength());
				statement.execute("INSERT INTO " + NAME + ".T (ID,TXT) VALUES (2,'default')");
				assertData(statement);
				for (int step = 0; step < 3; step++) {
					before = read(connection, dialect);
					after = before.clone();
					Column column = after.getTables().get("T").getColumns().get("V");
					if (step == 0)
						column.setNotNull(true);
					else if (step == 1)
						column.setDataType(DataType.BIGINT);
					else {
						column.setNotNull(false).setDefaultValue(null).setRemarks(null);
						after.getTables().get("T").setRemarks(null);
					}
					apply(statement, dialect, before, after, false);
					actual = read(connection, dialect);
					Column reread = actual.getTables().get("T").getColumns().get("V");
					assertEquals(step != 2, reread.isNotNull());
					if (step > 0)
						assertEquals(DataType.BIGINT, reread.getDataType());
					if (step == 2) {
						assertNull(reread.getDefaultValue());
						assertTrue(reread.getRemarks() == null || reread.getRemarks().isEmpty());
						assertTrue(actual.getTables().get("T").getRemarks() == null
								|| actual.getTables().get("T").getRemarks().isEmpty());
					}
					assertData(statement);
				}
				statement.execute("INSERT INTO " + NAME + ".T (ID,TXT) VALUES (3,'nullable')");
				try (var rows = statement.executeQuery("SELECT V FROM " + NAME + ".T WHERE ID=3")) {
					assertTrue(rows.next());
					assertNull(rows.getObject(1));
				}
			} catch (Exception | AssertionError e) {
				failure = e;
				throw e;
			} finally {
				while (!cleanup.isEmpty()) {
					try {
						statement.execute(cleanup.pop());
					} catch (SQLException e) {
						if (failure != null)
							failure.addSuppressed(e);
						else
							throw e;
					}
				}
			}
		}
	}

	private static Schema read(Connection connection, Dialect dialect) throws SQLException {
		var reader = dialect.getCatalogReader().getSchemaReader();
		reader.setSchemaName(NAME);
		return reader.getAllFull(connection).stream().filter(s -> NAME.equals(s.getName())).findFirst().orElseThrow();
	}

	private static void apply(Statement statement, Dialect dialect, Schema before, Schema after, boolean catalog)
			throws SQLException {
		List<SqlOperation> operations;
		if (catalog) {
			Catalog left = new Catalog(), right = new Catalog();
			left.getSchemas().add(before.clone());
			right.getSchemas().add(after.clone());
			SqlFactory<Catalog> factory = dialect.createSqlFactoryRegistry().getSqlFactory(right, SqlType.ALTER);
			operations = factory.createDiffSql(left.diff(right));
		} else {
			SqlFactory<Schema> factory = dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
			operations = factory.createDiffSql(before.diff(after));
		}
		assertFalse(operations.isEmpty());
		for (var operation : operations) {
			if (operation.getSqlType() == SqlType.COMMENT)
				continue;
			assertFalse(operation.getSqlText().contains("ADMIN_CMD"));
			statement.execute(operation.getSqlText());
		}
		if (operations.stream().anyMatch(o -> o.getSqlType() == SqlType.COMMENT && o.getSqlText().contains("REORG")))
			statement.execute("CALL SYSPROC.ADMIN_CMD('REORG TABLE " + NAME + ".T')");
	}

	private static void assertData(Statement statement) throws SQLException {
		try (var rows = statement.executeQuery("SELECT ID,V,TXT FROM " + NAME + ".T ORDER BY ID")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
			assertEquals(7, rows.getInt(2));
			assertEquals("keep", rows.getString(3));
			assertTrue(rows.next());
			assertEquals(2, rows.getInt(1));
			assertEquals(9, rows.getInt(2));
			assertEquals("default", rows.getString(3));
			assertFalse(rows.next());
		}
	}
}
