/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.db2.zos.Db2Zos13Test.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.ParameterDirection;

class Db2Zos13ProgrammableTest {
	private static final boolean ZOS = true;

	@Test
	void revokeUsesRoleGrammarAndRejectsConflictingState() {
		var dialect = new Db2Zos13();
		var privilege = dialect.getCatalogReader().getObjectPrivilegeReader()
				.getAll(connection(new AtomicInteger(), "grants")).get(0);
		privilege.setState(PrivilegeState.Revoke);
		String text = sql(dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.REVOKE));
		assertTrue(text.contains("REVOKE SELECT ON TABLE \"APP\".\"T\" FROM ROLE \"reader\""), text);
		assertFalse(text.contains("WITH GRANT OPTION"), text);
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT));
	}

	@Test
	void readsOverloadsInOneQueryAndReplaysOnlyCompleteDefinitions() throws Exception {
		var calls = new AtomicInteger();
		var dialect = new Db2Zos13();
		var reader = dialect.getCatalogReader().getSchemaReader().getProcedureReader();
		reader.setSchemaName("APP");
		var routines = reader.getAllFull(connection(calls, "procedure"));
		assertEquals(1, calls.get());
		assertEquals(2, routines.size());
		assertEquals(List.of("P1", "P2"), routines.stream().map(Procedure::getSpecificName).toList());
		Procedure routine = SchemaUtils.readXml(new java.io.StringReader(routines.get(0).asXml()));
		if (!ZOS) {
			assertEquals(ParameterDirection.Inout, routine.getArguments().get(0).getDirection());
			assertEquals(DataType.INT, routine.getArguments().get(0).getDataType());
			assertTrue(routine.getSpecifics().containsKey("DB2_HOST_REQUIRES_DEFINITION"));
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(routine, SqlType.CREATE));
			routine.setDefinition(procedureDdl());
		}
		var sql = sql(dialect.createSqlFactoryRegistry().createSql(routine, SqlType.CREATE));
		assertTrue(sql.contains(procedureDdl()), sql);
		assertTrue(sql.contains("SET X = X + 1; SET X = X + 2;"), sql);
	}

	@Test
	void readsFunctionBodyAndReturningMetadataWithoutGuessingCreateOptions() throws Exception {
		var calls = new AtomicInteger();
		var dialect = new Db2Zos13();
		var functions = dialect.getCatalogReader().getSchemaReader().getFunctionReader()
				.getAllFull(connection(calls, "function"));
		assertEquals(1, calls.get());
		Function function = SchemaUtils.readXml(new java.io.StringReader(functions.get(0).asXml()));
		if (!ZOS) {
			assertEquals(DataType.INT, function.getReturning().getDataType());
			assertEquals(1, function.getArguments().size());
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(function, SqlType.CREATE));
			function.setDefinition(functionDdl());
		}
		assertTrue(sql(dialect.createSqlFactoryRegistry().createSql(function, SqlType.CREATE))
				.contains("RETURNS INTEGER"));
	}

	@Test
	void readsTriggerReferencesAndBodyAndPreservesDisabledState() throws Exception {
		var dialect = new Db2Zos13();
		var calls = new AtomicInteger();
		var triggers = dialect.getCatalogReader().getSchemaReader().getTriggerReader()
				.getAllFull(connection(calls, "trigger"));
		assertEquals(1, calls.get());
		Trigger trigger = SchemaUtils.readXml(new java.io.StringReader(triggers.get(0).asXml()));
		assertEquals("BASE", trigger.getTableSchemaName());
		assertEquals(Set.of("UPDATE"), trigger.getEventManipulation());
		if (!ZOS) {
			assertEquals("N", trigger.getActionReferenceNewRow());
			assertEquals("O", trigger.getActionReferenceOldRow());
			assertEquals("NT", trigger.getSpecifics().get("DB2_HOST_NEW_TABLE"));
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE));
			trigger.setDefinition(triggerDdl());
		}
		assertTrue(sql(dialect.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE))
				.contains("SET N.A = 1; SET N.B = 2;"));
		trigger.setEnable(false);
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE));
	}

	@Test
	void tableGrantsRetainIdentityAndGrantOptionsAcrossXml() throws Exception {
		var dialect = new Db2Zos13();
		var calls = new AtomicInteger();
		var grants = dialect.getCatalogReader().getObjectPrivilegeReader().getAllFull(connection(calls, "grants"));
		assertEquals(1, calls.get());
		ObjectPrivilege privilege = SchemaUtils.readXml(new java.io.StringReader(grants.get(0).asXml()));
		assertTrue(privilege.isGrantable());
		assertEquals(ZOS ? "OWNER" : null, privilege.getGrantorName());
		String sql = sql(dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT));
		assertTrue(sql.contains("GRANT SELECT ON TABLE \"APP\".\"T\" TO " + (ZOS ? "ROLE \"reader\"" : "\"reader\"")),
				sql);
		assertTrue(sql.contains("WITH GRANT OPTION"), sql);
		assertFalse(sql.contains("TO USER"), sql);
	}

	@Test
	void columnGrantabilityAndAuthorizationListsRequireResolution() throws Exception {
		var dialect = new Db2Zos13();
		var calls = new AtomicInteger();
		var privileges = dialect.getCatalogReader().getColumnPrivilegeReader().getAllFull(connection(calls, "columns"));
		assertEquals(1, calls.get());
		ColumnPrivilege privilege = SchemaUtils.readXml(new java.io.StringReader(privileges.get(0).asXml()));
		assertEquals("A", privilege.getColumnName());
		if (ZOS) {
			assertEquals("Y", privilege.getSpecifics().get("DB2_HOST_GRANTABILITY_UNKNOWN"));
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT));
			privilege.getSpecifics().remove("DB2_HOST_GRANTABILITY_UNKNOWN");
			privilege.setGrantable(true); // Simulates explicitly supplied authoritative information.
		}
		String grantSql = sql(dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT));
		assertTrue(grantSql.matches("(?s).*UPDATE\\s*\\(\\s*\"A\"\\s*\\)\\s+ON\\s+TABLE.*"), grantSql);
		var fromList = dialect.getCatalogReader().getObjectPrivilegeReader()
				.getAll(connection(new AtomicInteger(), "authlist")).get(0);
		assertEquals("AUTHLIST", fromList.getSpecifics().get("DB2_HOST_AUTHORIZATION_LIST"));
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSqlFactoryRegistry().createSql(fromList, SqlType.GRANT));
	}

	@Test
	void packageGranteesAndIncompleteCatalogTextCannotProduceSql() {
		var dialect = new Db2Zos13();
		var privilege = dialect.getCatalogReader().getObjectPrivilegeReader()
				.getAll(connection(new AtomicInteger(), "package")).get(0);
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT));
		assertThrows(RuntimeException.class, () -> dialect.getCatalogReader().getSchemaReader().getProcedureReader()
				.getAll(connection(new AtomicInteger(), "missing")));
		assertThrows(RuntimeException.class, () -> dialect.getCatalogReader().getSchemaReader().getProcedureReader()
				.getAll(connection(new AtomicInteger(), "alter")));
	}

	@Test
	void publicGranteeIsNotQuotedAsAUserAndBadActionsFail() {
		var dialect = new Db2Zos13();
		var privilege = dialect.getCatalogReader().getObjectPrivilegeReader()
				.getAll(connection(new AtomicInteger(), "public")).get(0);
		assertTrue(sql(dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT)).contains("TO PUBLIC"));
		privilege.setPrivilege("CONTROL");
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT));
	}

	private static String sql(List<com.sqlapp.data.db.sql.SqlOperation> operations) {
		return operations.stream().map(op -> op.getSqlText()).reduce("", (a, b) -> a + "\n" + b);
	}

	private static String procedureDdl() {
		return "CREATE PROCEDURE APP.P(INOUT X INTEGER) SPECIFIC APP.P1 LANGUAGE SQL BEGIN SET X = X + 1; SET X = X + 2; END";
	}

	private static String functionDdl() {
		return "CREATE FUNCTION APP.F(X INTEGER) RETURNS INTEGER SPECIFIC APP.F1 LANGUAGE SQL RETURN X + 1";
	}

	private static String triggerDdl() {
		return "CREATE TRIGGER APP.TR NO CASCADE BEFORE UPDATE ON BASE.T REFERENCING NEW AS N FOR EACH ROW BEGIN ATOMIC SET N.A = 1; SET N.B = 2; END";
	}

	private static Connection connection(AtomicInteger calls, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> statement((String) a[0], calls, mode);
		default -> fallback(m.getReturnType());
		});
	}

	private static PreparedStatement statement(String sql, AtomicInteger calls, String mode) {
		return proxy(PreparedStatement.class, (p, m, a) -> {
			if (!m.getName().equals("executeQuery"))
				return fallback(m.getReturnType());
			calls.incrementAndGet();
			if (sql.contains("host:routines")) {
				boolean function = mode.equals("function");
				var first = routine(function, mode);
				if (ZOS) {
					var second = new LinkedHashMap<>(first);
					second.put("SPECIFIC_NAME", function ? "F2" : "P2");
					return rows(function ? List.of(first) : List.of(first, second));
				}
				var argument = new LinkedHashMap<>(first);
				argument.put("PARAM_POSITION", 1);
				argument.put("PARAM_ROW_TYPE", "P");
				argument.put("PARAM_NAME", "X");
				argument.put("PARAM_MODE", function ? "IN" : "INOUT");
				argument.put("PARAM_TYPE", "INTEGER");
				argument.put("PARAM_LENGTH", 10L);
				argument.put("PARAM_SCALE", 0);
				if (function) {
					var returning = new LinkedHashMap<>(argument);
					returning.put("PARAM_POSITION", 2);
					returning.put("PARAM_ROW_TYPE", "R");
					returning.put("PARAM_NAME", null);
					return rows(List.of(argument, returning));
				}
				var second = new LinkedHashMap<>(argument);
				second.put("SPECIFIC_NAME", "P2");
				return rows(List.of(argument, second));
			}
			if (sql.contains("host:triggers"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "TRIGGER_NAME", "TR", "TABLE_SCHEMA", "BASE",
						"TABLE_NAME", "T", "ACTION_TIMING", "BEFORE", "ACTION_ORIENTATION", "ROW", "OLD_ROW",
						ZOS ? null : "O", "NEW_ROW", ZOS ? null : "N", "OLD_TABLE", null, "NEW_TABLE",
						ZOS ? null : "NT", "WHEN_TEXT", ZOS ? null : "N.A > 0", "EVENT_NAMES", "UPDATE", "ENABLED_FLAG",
						"Y", "CREATED_AT", null, "REMARKS", null, "TRIGGER_TEXT",
						ZOS ? triggerDdl() : "BEGIN ATOMIC SET N.A = 1; SET N.B = 2; END", "TEXT_KIND", ZOS ? "D" : "S",
						"TRIGGER_MODE", ZOS ? null : "DB2ROW", "ACTION_ORDER", ZOS ? null : "1", "NATIVE_VERSION", null,
						"SQL_PATH", null)));
			if (sql.contains("host:objectGrants") || sql.contains("host:columnGrants")) {
				var grant = row("SCHEMA_NAME", "APP", "OBJECT_NAME", "T", "COLUMN_NAME", "A", "GRANTOR_NAME",
						ZOS ? "OWNER" : null, "GRANTEE_NAME", mode.equals("public") ? "PUBLIC" : "reader",
						"PRIVILEGE_NAME", sql.contains("host:columnGrants") ? "UPDATE" : "SELECT", "GRANTABLE_FLAG",
						sql.contains("host:columnGrants") && ZOS ? "?" : "Y", "GRANTEE_TYPE",
						mode.equals("package") ? "P"
								: mode.equals("public") ? "U" : ZOS && !sql.contains("host:columnGrants") ? "R" : "U",
						"GRANTOR_TYPE", ZOS ? "U" : null, "AUTHORIZATION_LIST",
						mode.equals("authlist") ? "AUTHLIST" : null);
				return rows(List.of(grant));
			}
			throw new AssertionError(sql);
		});
	}

	private static Map<String, Object> routine(boolean function, String mode) {
		String ddl = function ? functionDdl() : procedureDdl();
		String body = function ? "RETURN X + 1" : "BEGIN SET X = X + 1; SET X = X + 2; END";
		return row("SCHEMA_NAME", "APP", "ROUTINE_NAME", function ? "F" : "P", "SPECIFIC_NAME", function ? "F1" : "P1",
				"LANGUAGE_NAME", "SQL", "ROUTINE_TEXT",
				mode.equals("missing") ? null
						: mode.equals("alter") ? "ALTER PROCEDURE APP.P ALTER PARAMETER X INTEGER" : ZOS ? ddl : body,
				"TEXT_KIND", mode.equals("alter") ? "D" : ZOS ? "D" : "S", "REMARKS", null, "EXTERNAL_NAME", null,
				"PARAMETER_STYLE", null, "SQL_DATA_ACCESS", "MODIFIES SQL DATA", "DETERMINISTIC", "NO",
				"NATIVE_VERSION", null, "SQL_PATH", null, "FUNCTION_FORM", function ? "S" : null, "PARAM_POSITION",
				null, "PARAM_NAME", null, "PARAM_ROW_TYPE", null, "PARAM_MODE", null, "PARAM_TYPE", null,
				"PARAM_LENGTH", null, "PARAM_SCALE", null, "PARAM_TYPE_SCHEMA", null, "PARAM_TYPE_NAME", null);
	}
}
