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

class Db2I75SequenceGrantTest {
	private static final boolean ZOS = false;

	@Test
	void readsTableAndSequenceGrantsInOneFilteredQueryWithoutIdentityCollisions() throws Exception {
		var calls = new AtomicInteger();
		var bindings = new ArrayList<Object>();
		var reader = new Db2I75().getCatalogReader().getObjectPrivilegeReader();
		reader.setSchemaName("App");
		reader.setObjectName("S");
		var grants = reader.getAllFull(connection(calls, bindings, "normal"));
		assertEquals(1, calls.get());
		assertEquals(List.of("App", "S", "App", "S", "App", "S"), bindings);
		assertEquals(3, grants.size());
		var catalog = new Catalog();
		catalog.getObjectPrivileges().addAll(grants);
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		assertEquals(catalog, restored);
		assertEquals(catalog, catalog.clone());
		var table = restored.getObjectPrivileges().get(0);
		var sequence = restored.getObjectPrivileges().get(2);
		assertEquals(table.getObjectName(), sequence.getObjectName());
		assertNotSame(restored.getObjectPrivileges().find(table), restored.getObjectPrivileges().find(sequence));
		assertEquals("SEQUENCE:S", sequence.getSpecificName());
		assertTrue(sql(table, SqlType.GRANT).contains("ON TABLE \"App\".\"S\""));
		assertTrue(sql(sequence, SqlType.GRANT).contains("ON SEQUENCE \"App\".\"S\""));
		var operations = new Db2I75().createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
		assertEquals(3, operations.size(), "Catalog CREATE must include its object grants");
		assertEquals(2, operations.stream().filter(op -> op.getSqlText().contains("ON SEQUENCE")).count());
	}

	@Test
	void grantsKeepQuotedNamesAndKnownGrantOptions() throws Exception {
		var privilege = read("quoted");
		ObjectPrivilege restored = SchemaUtils.readXml(new StringReader(privilege.asXml()));
		String text = sql(restored, SqlType.GRANT);
		assertTrue(text.contains("GRANT USAGE ON SEQUENCE \"App\".\"S\"\"eq\" TO \"reader\""), text);
		assertTrue(text.endsWith("WITH GRANT OPTION"), text);
		privilege.setGrantable(false);
		assertFalse(sql(privilege, SqlType.GRANT).contains("WITH GRANT OPTION"));
	}

	@Test
	void revokeTargetsSequenceWithRestrictAndChecksState() {
		var privilege = read("normal");
		privilege.setState(PrivilegeState.Revoke);
		String text = sql(privilege, SqlType.REVOKE);
		assertTrue(text.contains("REVOKE USAGE ON SEQUENCE \"App\".\"S\" FROM \"reader\""), text);
		assertTrue(text.endsWith("RESTRICT"));
		assertFalse(text.contains("WITH GRANT OPTION"));
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
	}

	@Test
	void publicAndRolesUseTheExistingNativeGranteeRules() {
		assertTrue(sql(read("public"), SqlType.GRANT).contains("TO PUBLIC"));
		var role = read("role");
		if (ZOS)
			assertTrue(sql(role, SqlType.GRANT).contains("TO ROLE \"reader\""));
		else
			assertThrows(IllegalArgumentException.class, () -> sql(role, SqlType.GRANT));
	}

	@Test
	void unresolvedAuthorizationAndPackageRowsAreRetainedWithoutGuessedSql() throws Exception {
		for (String mode : List.of("authlist", "unknown-grant", "package")) {
			var privilege = read(mode);
			ObjectPrivilege restored = SchemaUtils.readXml(new StringReader(privilege.asXml()));
			assertThrows(IllegalArgumentException.class, () -> sql(restored, SqlType.GRANT));
			if (mode.equals("package")) {
				assertEquals("COLL", restored.getSpecifics().get("DB2_HOST_PACKAGE_COLLECTION"));
				assertEquals("0123456789ABCDEF", restored.getSpecifics().get("DB2_HOST_PACKAGE_TOKEN"));
			}
		}
	}

	@Test
	void invalidKindsScopesAndActionsCannotFallBackToTablePrivileges() {
		assertThrows(RuntimeException.class, () -> read("unknown-kind"));
		assertThrows(RuntimeException.class, () -> read("missing-schema"));
		var privilege = read("normal");
		privilege.setPrivilege("SELECT");
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
		privilege.setPrivilege("ALTER");
		privilege.getSpecifics().remove("DB2_HOST_OBJECT_KIND");
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
		privilege.getSpecifics().put("DB2_HOST_OBJECT_KIND", "SEQUENCE");
		privilege.setHierachy(true);
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
	}

	@Test
	void sequenceNamedSequenceRetainsItsScopeAcrossXml() throws Exception {
		var privilege = read("same-as-kind");
		ObjectPrivilege restored = SchemaUtils.readXml(new StringReader(privilege.asXml()));
		assertEquals("SEQUENCE:SEQUENCE", restored.getSpecificName());
		assertTrue(sql(restored, SqlType.GRANT).contains("ON SEQUENCE \"App\".\"SEQUENCE\""));
	}

	private static ObjectPrivilege read(String mode) {
		return new Db2I75().getCatalogReader().getObjectPrivilegeReader()
				.getAllFull(connection(new AtomicInteger(), new ArrayList<>(), mode)).get(1);
	}

	private static String sql(ObjectPrivilege privilege, SqlType type) {
		return new Db2I75().createSqlFactoryRegistry().createSql(privilege, type).get(0).getSqlText();
	}

	private static Connection connection(AtomicInteger calls, List<Object> bindings, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> {
			String query = (String) a[0];
			assertTrue(query.contains("host:objectGrants") && query.contains("SYSSEQUENCEAUTH"), query);
			yield proxy(PreparedStatement.class, (sp, sm, sa) -> {
				if (sm.getName().equals("setString") || sm.getName().equals("setObject"))
					bindings.add(sa[1]);
				if (!sm.getName().equals("executeQuery"))
					return fallback(sm.getReturnType());
				calls.incrementAndGet();
				String name = mode.equals("quoted") ? "S\"eq" : mode.equals("same-as-kind") ? "SEQUENCE" : "S";
				var table = row("SCHEMA_NAME", "App", "OBJECT_NAME", name, "OBJECT_KIND", "TABLE", "GRANTOR_NAME",
						ZOS ? "OWNER" : null, "GRANTEE_NAME", "reader", "PRIVILEGE_NAME", "ALTER", "GRANTABLE_FLAG",
						"N", "GRANTEE_TYPE", "U", "GRANTOR_TYPE", null, "AUTHORIZATION_LIST", null,
						"PACKAGE_COLLECTION", null, "PACKAGE_TOKEN", null, "AUTHHOWGOT", null);
				var usage = new java.util.LinkedHashMap<>(table);
				usage.put("OBJECT_KIND", mode.equals("unknown-kind") ? "?" : "SEQUENCE");
				usage.put("PRIVILEGE_NAME", "USAGE");
				usage.put("SCHEMA_NAME", mode.equals("missing-schema") ? null : "App");
				usage.put("GRANTABLE_FLAG", mode.equals("unknown-grant") ? "?" : "Y");
				usage.put("GRANTEE_NAME", mode.equals("public") ? "PUBLIC" : "reader");
				usage.put("GRANTEE_TYPE", mode.equals("role") ? "R" : mode.equals("package") ? "P" : "U");
				usage.put("AUTHORIZATION_LIST", mode.equals("authlist") ? "AUTL" : null);
				if (mode.equals("package")) {
					usage.put("PACKAGE_COLLECTION", "COLL");
					usage.put("PACKAGE_TOKEN", "0123456789ABCDEF");
				}
				var alter = new java.util.LinkedHashMap<>(usage);
				alter.put("PRIVILEGE_NAME", "ALTER");
				alter.put("GRANTABLE_FLAG", "N");
				return rows(List.of(table, usage, alter));
			});
		}
		default -> fallback(m.getReturnType());
		});
	}
}
