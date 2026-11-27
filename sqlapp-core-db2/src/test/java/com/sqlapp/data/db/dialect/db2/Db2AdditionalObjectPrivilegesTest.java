/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges.*;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2AdditionalObjectPrivilegesTest {
	private Grant grant(Kind kind, String action, boolean grantable) {
		return new Grant(kind, List.of(Kind.TABLESPACE, Kind.WORKLOAD).contains(kind) ? null : "A.B", "Same.Name",
				action, "OWNER", GranteeType.ROLE, "R\"ole", grantable);
	}

	private List<Grant> grants() {
		return List.of(grant(Kind.TABLESPACE, "USE", true), grant(Kind.PACKAGE, "CONTROL", false),
				grant(Kind.PACKAGE, "BIND", true), grant(Kind.PACKAGE, "EXECUTE", true),
				grant(Kind.MODULE, "EXECUTE", true), grant(Kind.VARIABLE, "READ", true),
				grant(Kind.VARIABLE, "WRITE", true), grant(Kind.WORKLOAD, "USAGE", false),
				grant(Kind.XSROBJECT, "USAGE", false));
	}

	@Test
	void sixScopesRetainIdentityThroughXmlCloneAndEffectiveDiff() throws Exception {
		Catalog catalog = new Catalog();
		Db2ObjectPrivileges.set(catalog, grants());
		Catalog restored = SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
		assertEquals(grants(), Db2ObjectPrivileges.get(restored).grants());
		assertEquals(grants(), Db2ObjectPrivileges.get(catalog.clone()).grants());
		assertEquals(9, Db2ObjectPrivileges.createSql(catalog).size());
		assertEquals(9, Db2ObjectPrivileges.diff(new Catalog(), catalog).afterSql().size());
		assertEquals(9, Db2ObjectPrivileges.diff(catalog, new Catalog()).beforeSql().size());
		assertTrue(Db2ObjectPrivileges.diff(catalog, restored).afterSql().isEmpty());
		assertTrue(Db2ObjectPrivileges.grantSql(grants().getFirst())
				.startsWith("GRANT USE OF TABLESPACE \"Same.Name\" TO ROLE \"R\"\"ole\""));
		for (var grant : grants()) {
			assertEquals(grant.kind() == Kind.VARIABLE, Db2ObjectPrivileges.revokeSql(grant).endsWith(" RESTRICT"));
			assertEquals(grant.grantable(), Db2ObjectPrivileges.grantSql(grant).endsWith("WITH GRANT OPTION"));
		}
	}

	@Test
	void actionsGrantabilityAndGlobalSchemaBoundariesAreEnforced() {
		for (Kind kind : List.of(Kind.TABLESPACE, Kind.PACKAGE, Kind.MODULE, Kind.VARIABLE, Kind.WORKLOAD,
				Kind.XSROBJECT)) {
			assertThrows(IllegalArgumentException.class, () -> grant(kind, "SELECT", false));
		}
		assertThrows(IllegalArgumentException.class, () -> grant(Kind.PACKAGE, "CONTROL", true));
		assertThrows(IllegalArgumentException.class, () -> grant(Kind.XSROBJECT, "USAGE", true));
		assertThrows(IllegalArgumentException.class, () -> grant(Kind.WORKLOAD, "USAGE", true));
		assertThrows(IllegalArgumentException.class,
				() -> new Grant(Kind.TABLESPACE, "APP", "S", "USE", null, GranteeType.USER, "U", false));
		assertThrows(IllegalArgumentException.class,
				() -> new Grant(Kind.VARIABLE, null, "S", "READ", null, GranteeType.USER, "U", false));
	}

	@Test
	void catalogAndDirectFactoriesShareNativeSyntaxAndRejectHistoricalDialects() {
		var registry = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry();
		Catalog catalog = new Catalog();
		Db2ObjectPrivileges.set(catalog, grants());
		SqlFactory<Catalog> create = registry.getSqlFactory(catalog, SqlType.CREATE);
		assertEquals(Db2ObjectPrivileges.createSql(catalog),
				create.createSql(catalog).stream().map(o -> o.getSqlText()).toList());
		SqlFactory<Catalog> alter = registry.getSqlFactory(catalog, SqlType.ALTER);
		assertEquals(9, alter.createDiffSql(new Catalog().diff(catalog)).size());
		assertEquals(9, alter.createDiffSql(catalog.diff(new Catalog())).size());
		for (var grant : grants()) {
			ObjectPrivilege privilege = new ObjectPrivilege().setSchemaName(grant.schemaName())
					.setObjectName(grant.objectName()).setPrivilege(grant.privilege())
					.setGranteeName(grant.granteeName()).setGrantable(grant.grantable()).setHierachy(false);
			privilege.getSpecifics().putAll(Map.of("OBJECT_TYPE", grant.kind().name(), "GRANTEE_TYPE", "R"));
			SqlFactory<ObjectPrivilege> factory = registry.getSqlFactory(privilege, SqlType.GRANT);
			assertEquals(Db2ObjectPrivileges.grantSql(grant), factory.createSql(privilege).getFirst().getSqlText());
			factory = registry.getSqlFactory(privilege, SqlType.REVOKE);
			assertEquals(Db2ObjectPrivileges.revokeSql(grant), factory.createSql(privilege).getFirst().getSqlText());
			var old = DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry();
			SqlFactory<ObjectPrivilege> unsupported = old.getSqlFactory(privilege, SqlType.GRANT);
			assertThrows(IllegalArgumentException.class, () -> unsupported.createSql(privilege));
		}
		SqlFactory<Catalog> unsupported = DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry()
				.getSqlFactory(catalog, SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> unsupported.createSql(catalog));
	}
}
