/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2SchemaPrivilegeSqlTest {
	private SchemaPrivilege privilege(String action) {
		var privilege = new SchemaPrivilege().setSchemaName("App.Schema").setPrivilege(action)
				.setGranteeName("Reader Role");
		privilege.getSpecifics().put("GRANTEE_TYPE", "R");
		return privilege;
	}

	private String sql(SchemaPrivilege privilege, SqlType type, Dialect dialect) {
		SqlFactory<SchemaPrivilege> factory = dialect.createSqlFactoryRegistry().getSqlFactory(privilege, type);
		return factory.createSql(privilege).getFirst().getSqlText();
	}

	@Test
	void legacyGrantAndRevokeRetainSchemaIdentityAndTypedGrantee() {
		var privilege = privilege("CREATEIN").setGrantable(true);
		assertEquals("GRANT CREATEIN ON SCHEMA \"App.Schema\" TO ROLE \"Reader Role\" WITH GRANT OPTION",
				sql(privilege, SqlType.GRANT, DialectHolder.defaultDialect));
		assertEquals("REVOKE CREATEIN ON SCHEMA \"App.Schema\" FROM ROLE \"Reader Role\"",
				sql(privilege, SqlType.REVOKE, DialectHolder.defaultDialect));
		privilege.setGranteeName("PUBLIC");
		assertTrue(sql(privilege, SqlType.CREATE, DialectHolder.defaultDialect).contains(" TO PUBLIC"));
	}

	@Test
	void modernPrivilegesRequire1155AndRejectInvalidAuthorityOptions() {
		for (String action : List.of("SELECTIN", "INSERTIN", "UPDATEIN", "DELETEIN", "EXECUTEIN", "SCHEMAADM",
				"ACCESSCTRL", "DATAACCESS", "LOAD")) {
			var privilege = privilege(action);
			assertThrows(IllegalArgumentException.class,
					() -> sql(privilege, SqlType.GRANT, DialectHolder.Db2_1150Dialect));
			assertTrue(sql(privilege, SqlType.GRANT, DialectHolder.Db2_1155Dialect).startsWith("GRANT " + action));
			assertTrue(sql(privilege, SqlType.REVOKE, DialectHolder.Db2_1215Dialect).startsWith("REVOKE " + action));
		}
		var authority = privilege("SCHEMAADM").setGrantable(true);
		assertThrows(IllegalArgumentException.class,
				() -> sql(authority, SqlType.GRANT, DialectHolder.Db2_1155Dialect));
		authority.setGrantable(false).setGranteeName("PUBLIC");
		assertThrows(IllegalArgumentException.class,
				() -> sql(authority, SqlType.GRANT, DialectHolder.Db2_1155Dialect));
		var invalid = privilege("SELECT");
		assertThrows(IllegalArgumentException.class, () -> sql(invalid, SqlType.GRANT, DialectHolder.Db2_1155Dialect));
		invalid.setPrivilege("CREATEIN").setSchemaName(" ");
		assertThrows(IllegalArgumentException.class, () -> sql(invalid, SqlType.GRANT, DialectHolder.Db2_1155Dialect));
		invalid.setSchemaName("APP").setState(PrivilegeState.Revoke);
		assertThrows(IllegalArgumentException.class, () -> sql(invalid, SqlType.GRANT, DialectHolder.Db2_1155Dialect));
	}

	@Test
	void catalogCreateAndAlterComposeSchemaGrantsAfterXmlAndClone() throws Exception {
		var dialect = DialectHolder.Db2_1155Dialect;
		Catalog catalog = new Catalog().setDialect(dialect);
		catalog.getSchemas().add(new Schema("App.Schema"));
		catalog.getRoles().add(new Role("Reader Role"));
		catalog.getSchemaPrivileges()
				.add(privilege("SELECTIN").setGrantorName("OWNER").setGrantable(true).setState(PrivilegeState.Grant));
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		restored.setDialect(dialect);
		assertEquals(catalog, restored);
		assertEquals(catalog, catalog.clone());
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setCreateIfNotExists(false);
		SqlFactory<Catalog> create = registry.getSqlFactory(restored, SqlType.CREATE);
		var operations = create.createSql(restored).stream().map(o -> o.getSqlText()).toList();
		String grant = sql(privilege("SELECTIN").setGrantable(true), SqlType.GRANT, dialect);
		assertTrue(operations.contains(grant), operations.toString());
		assertTrue(operations.indexOf(grant) > 0);
		Catalog empty = restored.clone();
		empty.getSchemaPrivileges().clear();
		SqlFactory<Catalog> alter = registry.getSqlFactory(restored, SqlType.ALTER);
		assertEquals(List.of(grant),
				alter.createDiffSql(empty.diff(restored)).stream().map(o -> o.getSqlText()).toList());
		assertTrue(alter.createDiffSql(restored.diff(empty)).getFirst().getSqlText().startsWith("REVOKE SELECTIN"));
		assertEquals(PrivilegeState.Grant, restored.getSchemaPrivileges().getFirst().getState());
	}

	@Test
	void grantOptionUpgradeIsGeneratedAndReductionRequiresExplicitPlan() {
		var registry = DialectHolder.Db2_1155Dialect.createSqlFactoryRegistry();
		var original = privilege("CREATEIN");
		var target = original.clone().setGrantable(true);
		var factory = registry.getSqlFactory(target.diff(original));
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(target.diff(original)));
		assertTrue(registry.getSqlFactory(original.diff(target)).createDiffSql(original.diff(target)).getFirst()
				.getSqlText().endsWith("WITH GRANT OPTION"));
		var changedType = original.clone();
		changedType.getSpecifics().put("GRANTEE_TYPE", "U");
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(original.diff(changedType)).createDiffSql(original.diff(changedType)));
	}
}
