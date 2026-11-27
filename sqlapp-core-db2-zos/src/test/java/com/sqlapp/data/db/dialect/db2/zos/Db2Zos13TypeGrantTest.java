/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.db2.zos.Db2Zos13Test.*;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2Zos13TypeGrantTest {
	private static final boolean ZOS = true;

	@Test
	void oneFilteredQueryKeepsSameNamedTableSequenceAndTypeGrantsDistinct() throws Exception {
		var calls = new AtomicInteger();
		var bindings = new ArrayList<Object>();
		var reader = new Db2Zos13().getCatalogReader().getObjectPrivilegeReader();
		reader.setSchemaName("App");
		reader.setObjectName("T");
		var grants = reader.getAllFull(connection(calls, bindings, "normal"));
		assertEquals(1, calls.get());
		assertEquals(List.of("App", "T", "App", "T", "App", "T"), bindings);
		var catalog = new Catalog();
		catalog.getObjectPrivileges().addAll(grants);
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		assertEquals(catalog, restored);
		assertEquals(catalog, catalog.clone());
		var sequence = restored.getObjectPrivileges().get(1);
		var type = restored.getObjectPrivileges().get(2);
		assertEquals("T", type.getObjectName());
		assertEquals("TYPE:T", type.getSpecificName());
		assertNotSame(restored.getObjectPrivileges().find(sequence), restored.getObjectPrivileges().find(type));
		assertEquals("TYPE", restored.getObjectPrivileges().find(type).getSpecifics().get("DB2_HOST_OBJECT_KIND"));
		var operations = new Db2Zos13().createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
		assertEquals(3, operations.size());
		assertEquals(1, operations.stream().filter(op -> op.getSqlText().contains(" ON TYPE ")).count());
		assertTrue(
				sql(type, SqlType.GRANT).contains("GRANT USAGE ON TYPE \"App\".\"T\" TO \"reader\" WITH GRANT OPTION"));
	}

	@Test
	void alterPrivilegeIsOnlyEnabledForIbmI() {
		var type = read("normal");
		type.setPrivilege("ALTER");
		if (ZOS)
			assertThrows(IllegalArgumentException.class, () -> sql(type, SqlType.GRANT));
		else
			assertTrue(sql(type, SqlType.GRANT).contains("GRANT ALTER ON TYPE"));
	}

	@Test
	void quotingAndGrantOptionSurviveXml() throws Exception {
		var type = read("quoted");
		ObjectPrivilege restored = SchemaUtils.readXml(new StringReader(type.asXml()));
		assertTrue(sql(restored, SqlType.GRANT).contains("ON TYPE \"A\"\"pp\".\"T\"\"ype\""));
		restored.setGrantable(false);
		assertFalse(sql(restored, SqlType.GRANT).contains("WITH GRANT OPTION"));
		var namedType = read("same-as-kind");
		ObjectPrivilege copy = SchemaUtils.readXml(new StringReader(namedType.asXml()));
		assertEquals("TYPE:TYPE", copy.getSpecificName());
	}

	@Test
	void revokeUsesRestrictAndConflictingStateFails() {
		var type = read("normal");
		type.setState(PrivilegeState.Revoke);
		String text = sql(type, SqlType.REVOKE);
		assertTrue(text.contains("REVOKE USAGE ON TYPE \"App\".\"T\" FROM \"reader\""));
		assertTrue(text.endsWith("RESTRICT"));
		assertFalse(text.contains("WITH GRANT OPTION"));
		assertThrows(IllegalArgumentException.class, () -> sql(type, SqlType.GRANT));
	}

	@Test
	void publicRolesAndInternalGranteesRemainDistinct() {
		assertTrue(sql(read("public"), SqlType.GRANT).contains("TO PUBLIC"));
		var role = read("role");
		if (ZOS)
			assertTrue(sql(role, SqlType.GRANT).contains("TO ROLE \"reader\""));
		else
			assertThrows(IllegalArgumentException.class, () -> sql(role, SqlType.GRANT));
		var internal = read("internal");
		assertThrows(IllegalArgumentException.class, () -> sql(internal, SqlType.GRANT));
	}

	@Test
	void unresolvedRightsAndScopeCannotBecomeTableOrExecuteGrants() {
		assertThrows(IllegalArgumentException.class, () -> sql(read("authlist"), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(read("unknown-grant"), SqlType.GRANT));
		var type = read("normal");
		type.setPrivilege("EXECUTE");
		assertThrows(IllegalArgumentException.class, () -> sql(type, SqlType.GRANT));
		type.setPrivilege("ALTER");
		type.getSpecifics().remove("DB2_HOST_OBJECT_KIND");
		assertThrows(IllegalArgumentException.class, () -> sql(type, SqlType.GRANT));
	}

	@Test
	void missingIdentityAndUnknownObjectKindFailClearly() {
		assertThrows(RuntimeException.class, () -> read("missing-schema"));
		assertThrows(RuntimeException.class, () -> read("missing-name"));
		assertThrows(RuntimeException.class, () -> read("unknown-kind"));
	}

	private static ObjectPrivilege read(String mode) {
		return new Db2Zos13().getCatalogReader().getObjectPrivilegeReader()
				.getAllFull(connection(new AtomicInteger(), new ArrayList<>(), mode)).get(2);
	}

	private static String sql(ObjectPrivilege type, SqlType operation) {
		return new Db2Zos13().createSqlFactoryRegistry().createSql(type, operation).get(0).getSqlText();
	}

	private static Connection connection(AtomicInteger calls, List<Object> bindings, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> {
			String query = (String) a[0];
			assertTrue(query.contains(ZOS ? "SYSIBM.SYSRESAUTH" : "QSYS2.SYSUDTAUTH"), query);
			if (ZOS) {
				assertTrue(query.contains("a.OBTYPE='D'"), query);
				assertTrue(query.contains("WHEN 'L' THEN 'R' WHEN 'R' THEN 'I'"), query);
			}
			yield proxy(PreparedStatement.class, (sp, sm, sa) -> {
				if (sm.getName().equals("setString") || sm.getName().equals("setObject"))
					bindings.add(sa[1]);
				if (!sm.getName().equals("executeQuery"))
					return fallback(sm.getReturnType());
				calls.incrementAndGet();
				var table = row("SCHEMA_NAME", "App", "OBJECT_NAME", "T", "OBJECT_KIND", "TABLE", "GRANTOR_NAME",
						ZOS ? "OWNER" : null, "GRANTEE_NAME", "reader", "PRIVILEGE_NAME", "ALTER", "GRANTABLE_FLAG",
						"N", "GRANTEE_TYPE", "U", "GRANTOR_TYPE", null, "AUTHORIZATION_LIST", null);
				var sequence = new java.util.LinkedHashMap<>(table);
				sequence.put("OBJECT_KIND", "SEQUENCE");
				sequence.put("PRIVILEGE_NAME", "USAGE");
				var type = new java.util.LinkedHashMap<>(sequence);
				type.put("OBJECT_KIND", mode.equals("unknown-kind") ? "?" : "TYPE");
				type.put("SCHEMA_NAME", mode.equals("missing-schema") ? null : mode.equals("quoted") ? "A\"pp" : "App");
				type.put("OBJECT_NAME", mode.equals("missing-name") ? null
						: mode.equals("quoted") ? "T\"ype" : mode.equals("same-as-kind") ? "TYPE" : "T");
				type.put("GRANTEE_TYPE", mode.equals("role") ? "R" : mode.equals("internal") ? "I" : "U");
				type.put("GRANTEE_NAME", mode.equals("public") ? "PUBLIC" : "reader");
				type.put("GRANTABLE_FLAG", mode.equals("unknown-grant") ? "?" : "Y");
				type.put("AUTHORIZATION_LIST", mode.equals("authlist") ? "AUTL" : null);
				return rows(List.of(table, sequence, type));
			});
		}
		default -> fallback(m.getReturnType());
		});
	}
}
