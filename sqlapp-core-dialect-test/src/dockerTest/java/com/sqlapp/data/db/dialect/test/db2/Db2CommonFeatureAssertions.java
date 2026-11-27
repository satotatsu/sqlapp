/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Closing ordinary FK/view recreation and generated prepared DML checks. */
final class Db2CommonFeatureAssertions {
	private static final String NAME = "SQLAPP_COMMON_FEATURES";

	private Db2CommonFeatureAssertions() {
	}

	static void verify(Connection connection) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + NAME);
			Throwable failure = null;
			try {
				statement.execute("CREATE TABLE " + NAME + ".P (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(30))");
				statement.execute("CREATE TABLE " + NAME
						+ ".C (ID INTEGER NOT NULL PRIMARY KEY, PID INTEGER, CONSTRAINT FK_C_P FOREIGN KEY(PID) REFERENCES "
						+ NAME + ".P(ID))");
				statement.execute("CREATE VIEW " + NAME + ".V AS SELECT C.ID,P.TXT FROM " + NAME + ".C C JOIN " + NAME
						+ ".P P ON C.PID=P.ID");
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName(NAME);
				Schema original = reader.getAllFull(connection).stream().filter(s -> NAME.equals(s.getName()))
						.findFirst().orElseThrow();
				Schema restored = SchemaUtils.readXml(new StringReader(original.asXml()));
				statement.execute("DROP VIEW " + NAME + ".V");
				statement.execute("DROP TABLE " + NAME + ".C");
				statement.execute("DROP TABLE " + NAME + ".P");
				statement.execute("DROP SCHEMA " + NAME + " RESTRICT");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setCreateIfNotExists(false);
				SqlFactory<Schema> factory = registry.getSqlFactory(restored, SqlType.CREATE);
				for (var operation : factory.createSql(restored)) {
					try {
						statement.execute(operation.getSqlText());
					} catch (SQLException e) {
						throw new SQLException(operation.getSqlText(), e);
					}
				}
				Schema reread = reader.getAllFull(connection).stream().filter(s -> NAME.equals(s.getName())).findFirst()
						.orElseThrow();
				assertEquals(1, reread.getTables().get("C").getConstraints().stream()
						.filter(ForeignKeyConstraint.class::isInstance).count());
				assertEquals(1, reread.getViews().size());
				Table parent = reread.getTables().get("P");
				Row row = parent.newRow();
				row.put("ID", 1);
				row.put("TXT", "日本語's");
				assertEquals(1, execute(connection, registry, parent, row, SqlType.INSERT));
				statement.execute("INSERT INTO " + NAME + ".C VALUES(10,1)");
				SQLException foreignKey = assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + NAME + ".C VALUES(11,99)"));
				assertEquals("23503", foreignKey.getSQLState());
				row.put("TXT", "changed");
				assertEquals(1, execute(connection, registry, parent, row, SqlType.UPDATE));
				try (var rows = statement.executeQuery("SELECT ID,TXT FROM " + NAME + ".V")) {
					assertTrue(rows.next());
					assertEquals(10, rows.getInt(1));
					assertEquals("changed", rows.getString(2));
					assertFalse(rows.next());
				}
				statement.execute("DELETE FROM " + NAME + ".C");
				assertEquals(1, execute(connection, registry, parent, row, SqlType.DELETE));
				try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + NAME + ".P")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
			} catch (Exception | AssertionError e) {
				failure = e;
				throw e;
			} finally {
				for (String sql : List.of("DROP VIEW " + NAME + ".V", "DROP TABLE " + NAME + ".C",
						"DROP TABLE " + NAME + ".P", "DROP SCHEMA " + NAME + " RESTRICT")) {
					try {
						statement.execute(sql);
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

	private static int execute(Connection connection, SqlFactoryRegistry registry, Table table, Row row, SqlType type)
			throws SQLException {
		var nodes = registry.createSqlNodes(table, type);
		assertEquals(1, nodes.size());
		var parameters = nodes.getFirst().eval(row);
		try (var statement = parameters.createStatement(connection)) {
			parameters.setBind(statement);
			return statement.executeUpdate();
		}
	}
}
