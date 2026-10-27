/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Verifies comment changes do not recreate overloads or disturb grants. */
final class Db2RoutineAlterAssertions {
	private static final String NAME = "SQLAPP_ROUTINE_ALTER";
	private Db2RoutineAlterAssertions() { }

	static void verifyReplacement(Connection connection) throws Exception {
		verifyReplacement(connection, false, false);
	}

	static void verifyStatementReplacement(Connection connection) throws Exception {
		verifyReplacement(connection, true, false);
	}

	static void verifyUnqualifiedReplacement(Connection connection) throws Exception {
		verifyReplacement(connection, false, true);
		verifyReplacement(connection, true, true);
	}

	private static void verifyReplacement(Connection connection, boolean statements, boolean unqualified) throws Exception {
		String schema = NAME + (unqualified ? "_UNQUAL" : "") + (statements ? "_STMT" : "_BODY");
		var dialect = DialectResolver.getInstance().getDialect(connection);
		Deque<String> cleanup = new ArrayDeque<>();
		Throwable failure = null;
		try (var statement = connection.createStatement()) {
			try {
				statement.execute("CREATE SCHEMA " + schema); cleanup.push("DROP SCHEMA " + schema + " RESTRICT");
				if (unqualified) {
					statement.execute("SET CURRENT SCHEMA = '" + schema + "'");
					statement.execute("SET CURRENT PATH = SYSIBM, SYSFUN, SYSPROC, " + schema);
					statement.execute("CREATE FUNCTION HELPER(N INTEGER) RETURNS INTEGER SPECIFIC H_S LANGUAGE SQL RETURN N+1");
					cleanup.push("DROP SPECIFIC FUNCTION " + schema + ".H_S");
				}
				String routinePrefix = unqualified ? "" : schema + ".";
				String expression = unqualified ? "HELPER(N)" : "N+1";
				statement.execute("CREATE FUNCTION " + routinePrefix + "F(N INTEGER) RETURNS INTEGER SPECIFIC F_INT LANGUAGE SQL "
						+ (statements ? "BEGIN RETURN " + expression + "; END" : "RETURN " + expression));
				cleanup.push("DROP SPECIFIC FUNCTION " + schema + ".F_INT");
				statement.execute("CREATE FUNCTION " + schema + ".F(N VARCHAR(10)) RETURNS INTEGER SPECIFIC F_TEXT LANGUAGE SQL RETURN LENGTH(N)");
				cleanup.push("DROP SPECIFIC FUNCTION " + schema + ".F_TEXT");
				statement.execute("CREATE PROCEDURE " + routinePrefix + "P(OUT N INTEGER) SPECIFIC P_OUT LANGUAGE SQL COMMIT ON RETURN NO BEGIN SET N=7; END");
				cleanup.push("DROP SPECIFIC PROCEDURE " + schema + ".P_OUT");
				statement.execute("CREATE VIEW " + schema + ".V AS SELECT " + schema + ".F(4) AS N FROM SYSIBM.SYSDUMMY1");
				cleanup.push("DROP VIEW " + schema + ".V");
				statement.execute("GRANT EXECUTE ON SPECIFIC FUNCTION " + schema + ".F_INT TO PUBLIC");
				statement.execute("GRANT EXECUTE ON SPECIFIC PROCEDURE " + schema + ".P_OUT TO PUBLIC");
				statement.execute("COMMENT ON SPECIFIC FUNCTION " + schema + ".F_INT IS 'preserved'");
				String authSql = "SELECT SPECIFICNAME,GRANTOR,GRANTEE,GRANTEETYPE,EXECUTEAUTH FROM SYSCAT.ROUTINEAUTH WHERE SCHEMA='" + schema + "' ORDER BY SPECIFICNAME,GRANTOR,GRANTEE";
				var grants = snapshot(statement, authSql);
				var reader = dialect.getCatalogReader().getSchemaReader(); reader.setSchemaName(schema);
				reader.getReaderOptions().setReadDefinition(!statements); reader.getReaderOptions().setReadStatement(statements);
				Schema before = reader.getAllFull(connection).stream().filter(s -> schema.equals(s.getName())).findFirst().orElseThrow();
				if (statements) before = SchemaUtils.readXml(new java.io.StringReader(before.asXml()));
				Schema after = before.clone();
				Function function = after.getFunctions().stream().filter(f -> "F_INT".equals(f.getSpecificName())).findFirst().orElseThrow();
				String functionDdl = String.join("\n", statements ? function.getStatement() : function.getDefinition());
				assertTrue(functionDdl.contains(expression), functionDdl);
				if (statements) {
					assertTrue(function.getDefinition().isEmpty());
					assertNotNull(function.getSpecifics().get("DB2_ROUTINE_DDL_BASE64"));
					function.setStatement(functionDdl.replace(expression, unqualified ? "HELPER(N)+1" : "N+2"));
				} else function.setDefinition(functionDdl.replace(expression, unqualified ? "HELPER(N)+1" : "N+2"));
				Procedure procedure = after.getProcedures().getFirst();
				String procedureDdl = String.join("\n", statements ? procedure.getStatement() : procedure.getDefinition());
				assertTrue(procedureDdl.contains("N=7"), procedureDdl);
				if (statements) procedure.setStatement(procedureDdl.replace("N=7", "N=9"));
				else procedure.setDefinition(procedureDdl.replace("N=7", "N=9"));
				procedure.setRemarks("updated");
				Catalog left = new Catalog(), right = new Catalog();
				left.getSchemas().add(before); right.getSchemas().add(after);
				SqlFactory<Catalog> factory = dialect.createSqlFactoryRegistry().getSqlFactory(right, SqlType.ALTER);
				var operations = factory.createDiffSql(left.diff(right));
				assertEquals(2, operations.stream().filter(o -> o.getSqlText().startsWith("CREATE OR REPLACE")).count());
				if (unqualified) {
					assertTrue(operations.stream().anyMatch(o -> o.getSqlText().startsWith("CREATE OR REPLACE FUNCTION \"" + schema + "\".\"F\"(")));
					assertTrue(operations.stream().anyMatch(o -> o.getSqlText().startsWith("CREATE OR REPLACE PROCEDURE \"" + schema + "\".\"P\"(")));
					// Prove replay does not depend on the caller's current namespace/path.
					statement.execute("SET CURRENT SCHEMA = 'SYSIBM'");
					statement.execute("SET CURRENT PATH = SYSIBM, SYSFUN, SYSPROC");
				}
				for (var operation : operations) {
					assertFalse(operation.getSqlText().startsWith("DROP")); statement.execute(operation.getSqlText());
				}
				assertEquals(grants, snapshot(statement, authSql));
				try (var rows = statement.executeQuery("SELECT N FROM " + schema + ".V")) {
					assertTrue(rows.next()); assertEquals(6, rows.getInt(1));
				}
				try (var rows = statement.executeQuery("VALUES " + schema + ".F(CAST('abc' AS VARCHAR(10)))")) {
					assertTrue(rows.next()); assertEquals(3, rows.getInt(1));
				}
				try (var call = connection.prepareCall("CALL " + schema + ".P(?)")) {
					call.registerOutParameter(1, Types.INTEGER); call.execute(); assertEquals(9, call.getInt(1));
				}
				Schema actual = reader.getAllFull(connection).stream().filter(s -> schema.equals(s.getName())).findFirst().orElseThrow();
				assertEquals(unqualified ? Set.of("F_INT", "F_TEXT", "H_S") : Set.of("F_INT", "F_TEXT"),
						new HashSet<>(actual.getFunctions().stream().map(Function::getSpecificName).toList()));
				assertEquals("preserved", actual.getFunctions().stream().filter(f -> "F_INT".equals(f.getSpecificName())).findFirst().orElseThrow().getRemarks());
				assertEquals("P_OUT", actual.getProcedures().getFirst().getSpecificName());
				assertEquals("updated", actual.getProcedures().getFirst().getRemarks());
			} catch (Exception | AssertionError e) { failure = e; throw e; }
			finally {
				while (!cleanup.isEmpty()) {
					try { statement.execute(cleanup.pop()); }
					catch (SQLException e) { if (failure != null) failure.addSuppressed(e); else throw e; }
				}
			}
		}
	}

	static void verify(Connection connection) throws Exception {
		Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		Deque<String> cleanup = new ArrayDeque<>();
		Throwable failure = null;
		try (Statement statement = connection.createStatement()) {
			try {
				statement.execute("CREATE SCHEMA " + NAME);
				cleanup.push("DROP SCHEMA " + NAME + " RESTRICT");
				statement.execute("CREATE FUNCTION " + NAME + ".F(N INTEGER) RETURNS INTEGER SPECIFIC F_INT LANGUAGE SQL RETURN N+1");
				cleanup.push("DROP SPECIFIC FUNCTION " + NAME + ".F_INT");
				statement.execute("CREATE FUNCTION " + NAME + ".F(N VARCHAR(10)) RETURNS INTEGER SPECIFIC F_TEXT LANGUAGE SQL RETURN LENGTH(N)");
				cleanup.push("DROP SPECIFIC FUNCTION " + NAME + ".F_TEXT");
				statement.execute("CREATE PROCEDURE " + NAME + ".P(OUT N INTEGER) SPECIFIC P_OUT LANGUAGE SQL BEGIN SET N=7; END");
				cleanup.push("DROP SPECIFIC PROCEDURE " + NAME + ".P_OUT");
				statement.execute("GRANT EXECUTE ON SPECIFIC FUNCTION " + NAME + ".F_INT TO PUBLIC");
				statement.execute("GRANT EXECUTE ON SPECIFIC PROCEDURE " + NAME + ".P_OUT TO PUBLIC");
				var identities = snapshot(statement, "SELECT SPECIFICNAME,ROUTINEID,CREATE_TIME FROM SYSCAT.ROUTINES WHERE ROUTINESCHEMA='" + NAME + "' ORDER BY SPECIFICNAME");
				var grants = snapshot(statement, "SELECT SPECIFICNAME,GRANTOR,GRANTEE,GRANTEETYPE,EXECUTEAUTH FROM SYSCAT.ROUTINEAUTH WHERE SCHEMA='" + NAME + "' ORDER BY SPECIFICNAME,GRANTOR,GRANTEE");
				assertFalse(grants.isEmpty());
				Schema before = read(connection, dialect);
				Function function = before.getFunctions().stream().filter(f -> "F_INT".equals(f.getSpecificName())).findFirst().orElseThrow();
				Function changed = function.clone().setRemarks("function's comment");
				SqlFactory<Function> direct = dialect.createSqlFactoryRegistry().getSqlFactory(changed, SqlType.ALTER);
				execute(statement, direct.createDiffSql(function.diff(changed)));
				before = read(connection, dialect);
				Schema after = before.clone();
				after.getProcedures().getFirst().setRemarks("procedure's comment");
				SqlFactory<Schema> schemaFactory = dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
				execute(statement, schemaFactory.createDiffSql(before.diff(after)));
				Schema actual = read(connection, dialect);
				assertEquals("function's comment", actual.getFunctions().stream().filter(f -> "F_INT".equals(f.getSpecificName())).findFirst().orElseThrow().getRemarks());
				assertTrue(actual.getFunctions().stream().filter(f -> "F_TEXT".equals(f.getSpecificName())).allMatch(f -> f.getRemarks() == null || f.getRemarks().isEmpty()));
				assertEquals("procedure's comment", actual.getProcedures().getFirst().getRemarks());
				Catalog left = new Catalog(), right = new Catalog();
				left.getSchemas().add(actual.clone()); right.getSchemas().add(actual.clone());
				right.getSchemas().getFirst().getFunctions().forEach(f -> f.setRemarks(null));
				right.getSchemas().getFirst().getProcedures().forEach(p -> p.setRemarks(null));
				SqlFactory<Catalog> catalogFactory = dialect.createSqlFactoryRegistry().getSqlFactory(right, SqlType.ALTER);
				execute(statement, catalogFactory.createDiffSql(left.diff(right)));
				actual = read(connection, dialect);
				actual.getFunctions().forEach(f -> assertTrue(f.getRemarks() == null || f.getRemarks().isEmpty()));
				actual.getProcedures().forEach(p -> assertTrue(p.getRemarks() == null || p.getRemarks().isEmpty()));
				Schema unsupported = actual.clone();
				unsupported.getFunctions().getFirst().setStatement("RETURN 99");
				Schema original = actual;
				assertThrows(IllegalArgumentException.class, () -> schemaFactory.createDiffSql(original.diff(unsupported)));
				assertEquals(identities, snapshot(statement, "SELECT SPECIFICNAME,ROUTINEID,CREATE_TIME FROM SYSCAT.ROUTINES WHERE ROUTINESCHEMA='" + NAME + "' ORDER BY SPECIFICNAME"));
				assertEquals(grants, snapshot(statement, "SELECT SPECIFICNAME,GRANTOR,GRANTEE,GRANTEETYPE,EXECUTEAUTH FROM SYSCAT.ROUTINEAUTH WHERE SCHEMA='" + NAME + "' ORDER BY SPECIFICNAME,GRANTOR,GRANTEE"));
				try (var rows = statement.executeQuery("VALUES " + NAME + ".F(4), " + NAME + ".F(CAST('abc' AS VARCHAR(10)))")) {
					assertTrue(rows.next()); assertEquals(5, rows.getInt(1));
					assertTrue(rows.next()); assertEquals(3, rows.getInt(1));
				}
				try (var call = connection.prepareCall("CALL " + NAME + ".P(?)")) {
					call.registerOutParameter(1, Types.INTEGER); call.execute(); assertEquals(7, call.getInt(1));
				}
			} catch (Exception | AssertionError e) { failure = e; throw e; }
			finally {
				while (!cleanup.isEmpty()) {
					try { statement.execute(cleanup.pop()); }
					catch (SQLException e) { if (failure != null) failure.addSuppressed(e); else throw e; }
				}
			}
		}
	}

	private static Schema read(Connection connection, Dialect dialect) throws SQLException {
		var reader = dialect.getCatalogReader().getSchemaReader(); reader.setSchemaName(NAME);
		return reader.getAllFull(connection).stream().filter(s -> NAME.equals(s.getName())).findFirst().orElseThrow();
	}
	private static void execute(Statement statement, List<SqlOperation> operations) throws SQLException {
		assertFalse(operations.isEmpty());
		for (var operation : operations) {
			assertEquals(SqlType.SET_COMMENT, operation.getSqlType());
			assertTrue(operation.getSqlText().startsWith("COMMENT ON SPECIFIC "));
			statement.execute(operation.getSqlText());
		}
	}
	private static List<String> snapshot(Statement statement, String sql) throws SQLException {
		List<String> result = new ArrayList<>();
		try (var rows = statement.executeQuery(sql)) {
			while (rows.next()) {
				List<String> values = new ArrayList<>();
				for (int i=1; i<=rows.getMetaData().getColumnCount(); i++) values.add(rows.getString(i));
				result.add(values.toString());
			}
		}
		return result;
	}
}
