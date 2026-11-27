/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.db2.i.Db2I75Test.*;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2I75RoutineGrantTest {
	private static final boolean ZOS = false;

	@Test
	void readsOverloadedFunctionGrantsInOneFilteredQueryAndRetainsXmlIdentity() throws Exception {
		var calls = new AtomicInteger();
		var bindings = new ArrayList<Object>();
		var reader = new Db2I75().getCatalogReader().getRoutinePrivilegeReader();
		var context = new com.sqlapp.data.parameter.ParametersContext();
		context.put("schemaName", "App");
		context.put("objectName", "F");
		var grants = reader.getAllFull(connection(calls, bindings, "normal"), context);
		assertEquals(1, calls.get());
		assertEquals(List.of("App", "F"), bindings);
		assertEquals(List.of("F1", "F2"), grants.stream().map(RoutinePrivilege::getSpecificName).toList());
		var catalog = new Catalog();
		catalog.getRoutinePrivileges().addAll(grants);
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		assertEquals(catalog, restored);
		assertEquals(2, catalog.clone().getRoutinePrivileges().size());
		var privilege = restored.getRoutinePrivileges().get(0);
		assertFalse(privilege.isHierachy());
		assertTrue(privilege.isGrantable());
		String text = sql(privilege, SqlType.GRANT);
		assertTrue(text.contains("GRANT EXECUTE ON SPECIFIC FUNCTION \"App\".\"F1\" TO \"reader\""), text);
		assertTrue(text.endsWith("WITH GRANT OPTION"), text);
	}

	@Test
	void procedureGrammarFollowsTheProductAndEqualSpecificNamesSurviveXml() throws Exception {
		var privilege = read("procedure");
		assertEquals("P1", privilege.getSpecificName());
		String text = sql(privilege, SqlType.GRANT);
		assertTrue(text.contains(ZOS ? "ON PROCEDURE \"App\".\"P\"" : "ON SPECIFIC PROCEDURE \"App\".\"P1\""), text);
		privilege.setSpecificName(privilege.getObjectName());
		RoutinePrivilege restored = SchemaUtils.readXml(new StringReader(privilege.asXml()));
		assertEquals("P", restored.getSpecificName());
		assertEquals(privilege, restored);
	}

	@Test
	void revokeUsesRestrictAndDoesNotCarryGrantOptions() {
		var privilege = read("normal");
		privilege.setState(PrivilegeState.Revoke);
		String text = sql(privilege, SqlType.REVOKE);
		assertTrue(text.contains("REVOKE EXECUTE ON SPECIFIC FUNCTION \"App\".\"F1\" FROM \"reader\""), text);
		assertTrue(text.endsWith("RESTRICT"), text);
		assertFalse(text.contains("WITH GRANT OPTION"), text);
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
	}

	@Test
	void publicAndRoleGranteesUseNativeGrammar() {
		assertTrue(sql(read("public"), SqlType.GRANT).contains("TO PUBLIC"));
		var role = read("role");
		if (ZOS)
			assertTrue(sql(role, SqlType.GRANT).contains("TO ROLE \"reader\""));
		else
			assertThrows(IllegalArgumentException.class, () -> sql(role, SqlType.GRANT));
	}

	@Test
	void schemaScopesUnresolvedRoutinesAndPackageGranteesAreInventoryOnly() throws Exception {
		var scope = read("scope");
		assertTrue(scope.isHierachy());
		assertEquals("SCHEMA", scope.getSpecifics().get("DB2_HOST_ROUTINE_SCOPE"));
		RoutinePrivilege restored = SchemaUtils.readXml(new StringReader(scope.asXml()));
		assertThrows(IllegalArgumentException.class, () -> sql(restored, SqlType.GRANT));
		var missing = read("unresolved");
		assertEquals("Y", missing.getSpecifics().get("DB2_HOST_ROUTINE_UNRESOLVED"));
		assertThrows(IllegalArgumentException.class, () -> sql(missing, SqlType.GRANT));
		var pkg = read("package");
		assertEquals("COLL", pkg.getSpecifics().get("DB2_HOST_PACKAGE_COLLECTION"));
		assertThrows(IllegalArgumentException.class, () -> sql(pkg, SqlType.GRANT));
	}

	@Test
	void authorizationListsUnknownGrantabilityAndActionsRequireResolution() {
		assertThrows(IllegalArgumentException.class, () -> sql(read("authlist"), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(read("unknown-grant"), SqlType.GRANT));
		var privilege = read("normal");
		privilege.setPrivilege("ALTER");
		if (ZOS)
			assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
		else
			assertTrue(sql(privilege, SqlType.GRANT).contains("GRANT ALTER ON SPECIFIC FUNCTION"));
		privilege.setPrivilege("CONTROL");
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
	}

	@Test
	void missingOrUnknownRoutineIdentityCannotProduceGuessedSql() {
		assertThrows(RuntimeException.class, () -> read("missing-specific"));
		var unknown = read("unknown-kind");
		assertThrows(IllegalArgumentException.class, () -> sql(unknown, SqlType.GRANT));
		var unqualified = read("normal");
		unqualified.setSchemaName(null);
		assertThrows(IllegalArgumentException.class, () -> sql(unqualified, SqlType.GRANT));
	}

	private static RoutinePrivilege read(String mode) {
		return new Db2I75().getCatalogReader().getRoutinePrivilegeReader()
				.getAllFull(connection(new AtomicInteger(), new ArrayList<>(), mode)).get(0);
	}

	private static String sql(RoutinePrivilege privilege, SqlType type) {
		return new Db2I75().createSqlFactoryRegistry().createSql(privilege, type).get(0).getSqlText();
	}

	private static Connection connection(AtomicInteger calls, List<Object> bindings, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> {
			assertTrue(((String) a[0]).contains("host:routineGrants"));
			yield proxy(PreparedStatement.class, (sp, sm, sa) -> {
				if (sm.getName().equals("setString") || sm.getName().equals("setObject"))
					bindings.add(sa[1]);
				if (!sm.getName().equals("executeQuery"))
					return fallback(sm.getReturnType());
				calls.incrementAndGet();
				var first = row("SCHEMA_NAME", "App", "OBJECT_NAME", mode.equals("procedure") ? "P" : "F",
						"SPECIFIC_NAME",
						mode.equals("missing-specific") ? null
								: mode.equals("scope") ? "*" : mode.equals("procedure") ? "P1" : "F1",
						"ROUTINE_KIND", mode.equals("unknown-kind") ? "?" : mode.equals("procedure") ? "P" : "F",
						"GRANTOR_NAME", ZOS ? "OWNER" : null, "GRANTEE_NAME",
						mode.equals("public") ? "PUBLIC" : "reader", "PRIVILEGE_NAME", "EXECUTE", "GRANTABLE_FLAG",
						mode.equals("unknown-grant") ? "?" : "Y", "GRANTEE_TYPE",
						mode.equals("package") ? "P" : mode.equals("role") ? "R" : "U", "GRANTOR_TYPE", null,
						"AUTHORIZATION_LIST", mode.equals("authlist") ? "AUTL" : null, "RESOLVED_FLAG",
						mode.equals("unresolved") ? "N" : "Y", "PACKAGE_COLLECTION",
						mode.equals("package") ? "COLL" : null, "AUTHHOWGOT", ZOS ? "S" : null);
				var second = new java.util.LinkedHashMap<>(first);
				second.put("SPECIFIC_NAME", "F2");
				return rows(mode.equals("normal") ? List.of(first, second) : List.of(first));
			});
		}
		default -> fallback(m.getReturnType());
		});
	}
}
