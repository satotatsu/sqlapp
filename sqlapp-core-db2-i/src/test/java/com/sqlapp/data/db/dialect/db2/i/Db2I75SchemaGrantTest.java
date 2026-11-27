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
import com.sqlapp.data.db.dialect.db2.common.metadata.AbstractDb2HostCatalogReader;
import com.sqlapp.data.db.dialect.db2.metadata.Db2SchemaPrivilegeReader;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

class Db2I75SchemaGrantTest {
	private static final boolean ZOS = false;

	@Test
	void oneQueryRetainsSchemaGrantsAcrossXmlAndCatalogCreate() throws Exception {
		var calls = new AtomicInteger();
		var bindings = new ArrayList<Object>();
		var reader = reader();
		reader.setSchemaName("APP");
		var grants = reader.getAllFull(connection(calls, bindings, "normal"));
		assertEquals(1, calls.get());
		assertEquals(List.of("APP"), bindings);
		var catalog = new Catalog();
		catalog.getSchemaPrivileges().addAll(grants);
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		assertEquals(catalog, restored);
		assertEquals(catalog, catalog.clone());
		assertEquals(2, restored.getSchemaPrivileges().size());
		var first = restored.getSchemaPrivileges().get(0);
		assertEquals(ZOS ? "OWNER" : null, first.getGrantorName());
		assertTrue(first.isGrantable());
		assertTrue(
				sql(first, SqlType.GRANT).contains("GRANT CREATEIN ON SCHEMA \"APP\" TO \"reader\" WITH GRANT OPTION"));
		assertEquals(2, new Db2I75().createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).size());
	}

	@Test
	void fullCatalogLoadingIncludesSchemaGrantsOnce() {
		var calls = new AtomicInteger();
		var catalogs = new Db2I75().getCatalogReader().getAllFull(connection(calls, new ArrayList<>(), "normal"));
		assertEquals(1, catalogs.size());
		assertEquals(2, catalogs.get(0).getSchemaPrivileges().size());
		assertEquals(5, calls.get(), "Schemas, object/routine/column grants and one schema-grant query");
	}

	@Test
	void explicitContextSupportsMultipleSchemasWithoutPerSchemaQueries() {
		var calls = new AtomicInteger();
		var bindings = new ArrayList<Object>();
		var reader = reader();
		reader.setSchemaName("IGNORED");
		var context = new ParametersContext();
		context.put("schemaName", List.of("APP", "OTHER"));
		reader.getAllFull(connection(calls, bindings, "normal"), context);
		assertEquals(1, calls.get());
		assertEquals(List.of("APP", "OTHER"), bindings);
	}

	@Test
	void quotingAndNativeRevokeGrammarArePreserved() throws Exception {
		var privilege = read("quoted");
		SchemaPrivilege restored = SchemaUtils.readXml(new StringReader(privilege.asXml()));
		assertTrue(sql(restored, SqlType.GRANT).contains("ON SCHEMA \"A\"\"pp\""));
		restored.setState(PrivilegeState.Revoke);
		String text = sql(restored, SqlType.REVOKE);
		assertTrue(text.contains("REVOKE CREATEIN ON SCHEMA \"A\"\"pp\" FROM \"reader\""));
		assertFalse(text.contains("RESTRICT"));
		assertFalse(text.contains("WITH GRANT OPTION"));
		assertThrows(IllegalArgumentException.class, () -> sql(restored, SqlType.GRANT));
	}

	@Test
	void productSpecificActionsCannotBecomeLuwSchemaAuthorities() {
		var privilege = read("normal");
		privilege.setPrivilege(ZOS ? "DROPIN" : "USAGE");
		assertTrue(sql(privilege, SqlType.GRANT).contains(privilege.getPrivilege()));
		privilege.setPrivilege(ZOS ? "USAGE" : "ALTERIN");
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
		privilege.setPrivilege("SCHEMAADM");
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
	}

	@Test
	void publicRolesAndInternalGranteesAreDistinct() {
		assertTrue(sql(read("public"), SqlType.GRANT).contains("TO PUBLIC"));
		var role = read("role");
		if (ZOS)
			assertTrue(sql(role, SqlType.GRANT).contains("TO ROLE \"reader\""));
		else
			assertThrows(IllegalArgumentException.class, () -> sql(role, SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(read("internal"), SqlType.GRANT));
	}

	@Test
	void wildcardAndUnresolvedAuthorizationRemainInventoryOnly() throws Exception {
		var all = read("all");
		assertEquals("ALL", all.getSpecifics().get("DB2_HOST_SCHEMA_SCOPE"));
		SchemaPrivilege restored = SchemaUtils.readXml(new StringReader(all.asXml()));
		assertThrows(IllegalArgumentException.class, () -> sql(restored, SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(read("authlist"), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(read("unknown-grant"), SqlType.GRANT));
	}

	@Test
	void missingSchemaFailsInsteadOfUsingTheCurrentSchema() {
		assertThrows(RuntimeException.class, () -> read("missing"));
		var privilege = read("normal");
		privilege.setSchemaName(null);
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
	}

	private static Db2SchemaPrivilegeReader reader() {
		return ((AbstractDb2HostCatalogReader) new Db2I75().getCatalogReader()).getSchemaPrivilegeReader();
	}

	private static SchemaPrivilege read(String mode) {
		return reader().getAllFull(connection(new AtomicInteger(), new ArrayList<>(), mode)).get(0);
	}

	private static String sql(SchemaPrivilege privilege, SqlType type) {
		return new Db2I75().createSqlFactoryRegistry().createSql(privilege, type).get(0).getSqlText();
	}

	private static Connection connection(AtomicInteger calls, List<Object> bindings, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> {
			String query = (String) a[0];
			yield proxy(PreparedStatement.class, (sp, sm, sa) -> {
				if (sm.getName().equals("setString") || sm.getName().equals("setObject"))
					bindings.add(sa[1]);
				if (!sm.getName().equals("executeQuery"))
					return fallback(sm.getReturnType());
				calls.incrementAndGet();
				if (!query.contains("host:schemaGrants"))
					return rows(List.of());
				assertTrue(query.contains("SYSSCHEMAAUTH"));
				var first = row("SCHEMA_NAME",
						mode.equals("missing") ? null
								: mode.equals("all") ? "*" : mode.equals("quoted") ? "A\"pp" : "APP",
						"GRANTOR_NAME", ZOS ? "OWNER" : null, "GRANTEE_NAME",
						mode.equals("public") ? "PUBLIC" : "reader", "PRIVILEGE_NAME", "CREATEIN", "GRANTABLE_FLAG",
						mode.equals("unknown-grant") ? "?" : "Y", "GRANTEE_TYPE",
						mode.equals("role") ? "R" : mode.equals("internal") ? "I" : "U", "GRANTOR_TYPE", null,
						"AUTHORIZATION_LIST", mode.equals("authlist") ? "AUTL" : null, "AUTHHOWGOT", ZOS ? "S" : null);
				var second = new java.util.LinkedHashMap<>(first);
				second.put("PRIVILEGE_NAME", ZOS ? "ALTERIN" : "USAGE");
				second.put("GRANTABLE_FLAG", "N");
				return rows(List.of(first, second));
			});
		}
		default -> fallback(m.getReturnType());
		});
	}
}
