/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2DatabasePrivilegeSqlTest {
	private UserPrivilege privilege(String action, String type, String name) {
		var privilege = new UserPrivilege().setPrivilege(action).setGranteeName(name);
		privilege.getSpecifics().put("GRANTEE_TYPE", type);
		return privilege;
	}

	private String sql(UserPrivilege privilege, SqlType type, Dialect dialect) {
		SqlFactory<UserPrivilege> factory = dialect.createSqlFactoryRegistry().getSqlFactory(privilege, type);
		return factory.createSql(privilege).getFirst().getSqlText();
	}

	private List<String> alter(Catalog original, Catalog target) {
		SqlFactory<Catalog> factory = DialectHolder.Db2_1215Dialect.createSqlFactoryRegistry().getSqlFactory(target,
				SqlType.ALTER);
		return factory.createDiffSql(original.diff(target)).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void dbadmExplicitlySeparatesDataAndAccessControlAcrossModernDefaults() {
		var dbadm = privilege("DBADM", "U", "Operator");
		for (Dialect dialect : List.of(DialectHolder.Db2_970Dialect, DialectHolder.Db2_1155Dialect,
				DialectHolder.Db2_1215Dialect))
			assertEquals("GRANT DBADM WITHOUT DATAACCESS WITHOUT ACCESSCTRL ON DATABASE TO USER \"Operator\"",
					sql(dbadm, SqlType.GRANT, dialect));
		assertEquals("GRANT DBADM ON DATABASE TO USER \"Operator\"",
				sql(dbadm, SqlType.GRANT, DialectHolder.Db2_950Dialect));
		assertEquals("REVOKE DBADM ON DATABASE FROM USER \"Operator\"",
				sql(dbadm, SqlType.REVOKE, DialectHolder.Db2_1215Dialect));
		dbadm.getSpecifics().put("DBADM_MODEL", "LEGACY");
		assertThrows(IllegalArgumentException.class, () -> sql(dbadm, SqlType.GRANT, DialectHolder.Db2_970Dialect));
		dbadm.getSpecifics().put("DBADM_MODEL", "SEPARATE");
		assertThrows(IllegalArgumentException.class, () -> sql(dbadm, SqlType.GRANT, DialectHolder.Db2_950Dialect));
	}

	@Test
	void versionedAuthoritiesAndOldSecadmPrincipalRestrictionAreEnforced() {
		var secadm = privilege("SECADM", "U", "SECURITY_USER");
		assertThrows(IllegalArgumentException.class, () -> sql(secadm, SqlType.GRANT, DialectHolder.defaultDialect));
		assertTrue(sql(secadm, SqlType.GRANT, DialectHolder.Db2_910Dialect).startsWith("GRANT SECADM"));
		secadm.getSpecifics().put("GRANTEE_TYPE", "G");
		assertThrows(IllegalArgumentException.class, () -> sql(secadm, SqlType.GRANT, DialectHolder.Db2_950Dialect));
		assertTrue(sql(secadm, SqlType.GRANT, DialectHolder.Db2_970Dialect).contains("TO GROUP"));
		var access = privilege("ACCESSCTRL", "R", "Operations");
		assertThrows(IllegalArgumentException.class, () -> sql(access, SqlType.GRANT, DialectHolder.Db2_950Dialect));
		assertTrue(sql(access, SqlType.GRANT, DialectHolder.Db2_970Dialect).contains("TO ROLE"));
		var secure = privilege("CREATE_SECURE_OBJECT", "U", "DEV");
		assertThrows(IllegalArgumentException.class, () -> sql(secure, SqlType.GRANT, DialectHolder.Db2_970Dialect));
		assertTrue(sql(secure, SqlType.GRANT, DialectHolder.Db2_1010Dialect).startsWith("GRANT CREATE_SECURE_OBJECT"));
	}

	@Test
	void unsupportedOptionsAndPublicAuthoritiesFailClearly() {
		for (String action : List.of("DBADM", "SECADM", "ACCESSCTRL", "DATAACCESS", "CREATE_SECURE_OBJECT")) {
			var privilege = privilege(action, "G", "PUBLIC");
			assertThrows(IllegalArgumentException.class,
					() -> sql(privilege, SqlType.GRANT, DialectHolder.Db2_1215Dialect));
		}
		var privilege = privilege("CONNECT", "G", "PUBLIC");
		assertEquals("GRANT CONNECT ON DATABASE TO PUBLIC",
				sql(privilege, SqlType.GRANT, DialectHolder.Db2_1215Dialect));
		privilege.setGrantable(true);
		assertThrows(IllegalArgumentException.class,
				() -> sql(privilege, SqlType.GRANT, DialectHolder.Db2_1215Dialect));
		privilege.setGrantable(false).setAdmin(true);
		assertThrows(IllegalArgumentException.class,
				() -> sql(privilege, SqlType.GRANT, DialectHolder.Db2_1215Dialect));
		privilege.setAdmin(false).setPrivilege("SYSADM");
		assertThrows(IllegalArgumentException.class,
				() -> sql(privilege, SqlType.GRANT, DialectHolder.Db2_1215Dialect));
	}

	@Test
	void xmlAndCatalogCreatePreserveMultipleGrantorsWithoutDuplicateNativeGrants() throws Exception {
		Catalog original = new Catalog();
		var dbadm = privilege("DBADM", "R", "Operations").setGrantorName("A");
		dbadm.getSpecifics().put("DBADM_MODEL", "SEPARATE");
		original.getRoles().add(new Role("Operations"));
		original.getUserPrivileges().add(dbadm);
		original.getUserPrivileges().add(dbadm.clone().setGrantorName("B"));
		original.getUserPrivileges().add(privilege("DATAACCESS", "R", "Operations"));
		Catalog restored = SchemaUtils.readXml(new StringReader(original.asXml()));
		assertEquals(original.asXml(), restored.asXml());
		assertEquals(original, restored);
		assertEquals(3, restored.getUserPrivileges().size());
		assertEquals(original, original.clone());
		SqlFactory<Catalog> factory = DialectHolder.Db2_1215Dialect.createSqlFactoryRegistry().getSqlFactory(restored,
				SqlType.CREATE);
		var operations = factory.createSql(restored).stream().map(SqlOperation::getSqlText).toList();
		assertEquals(1, operations.stream().filter(s -> s.startsWith("GRANT DBADM")).count());
		assertEquals(1, operations.stream().filter(s -> s.startsWith("GRANT DATAACCESS")).count());
		assertTrue(operations.getFirst().startsWith("CREATE ROLE"));
		Catalog differentGrantor = restored.clone();
		differentGrantor.getUserPrivileges().getFirst().setGrantorName("C");
		assertTrue(alter(restored, differentGrantor).isEmpty());
	}

	@Test
	void catalogDiffDistinguishesSameNamedUserAndGroup() {
		Catalog before = new Catalog(), after = new Catalog();
		before.getUserPrivileges().add(privilege("CONNECT", "U", "SAME"));
		after.getUserPrivileges().add(privilege("CONNECT", "G", "SAME"));
		assertEquals(List.of("REVOKE CONNECT ON DATABASE FROM USER SAME", "GRANT CONNECT ON DATABASE TO GROUP SAME"),
				alter(before, after));
		assertEquals(List.of("REVOKE CONNECT ON DATABASE FROM GROUP SAME", "GRANT CONNECT ON DATABASE TO USER SAME"),
				alter(after, before));
		assertEquals("U", before.getUserPrivileges().getFirst().getSpecifics().get("GRANTEE_TYPE"));
	}

	@Test
	void revocationOrderHandlesDbadmAndRoutineDependencies() {
		Catalog before = new Catalog(), after = new Catalog();
		for (String action : List.of("CONNECT", "CREATE_EXTERNAL_ROUTINE", "SECADM", "CREATE_NOT_FENCED_ROUTINE",
				"DBADM"))
			before.getUserPrivileges().add(privilege(action, "U", "SAME"));
		var sql = alter(before, after);
		assertTrue(sql.getFirst().startsWith("REVOKE DBADM"));
		assertTrue(sql.get(1).startsWith("REVOKE CREATE_NOT_FENCED_ROUTINE"));
		assertTrue(sql.getLast().startsWith("REVOKE SECADM"));
		after.getUserPrivileges().add(privilege("DBADM", "U", "SAME"));
		assertThrows(IllegalArgumentException.class, () -> alter(before, after));
	}

	@Test
	void directDiffUsesTheSameTypedPlannerAndDoesNotAlterSourceState() {
		var original = privilege("CONNECT", "U", "SAME").setState(PrivilegeState.Grant);
		var target = original.clone();
		target.getSpecifics().put("GRANTEE_TYPE", "G");
		var registry = DialectHolder.Db2_1215Dialect.createSqlFactoryRegistry();
		assertEquals(2, registry.getSqlFactory(original.diff(target)).createDiffSql(original.diff(target)).size());
		assertEquals(PrivilegeState.Grant, original.getState());
		Catalog invalid = new Catalog();
		invalid.getUserPrivileges().add(privilege("CONNECT", "U", "SAME").setState(PrivilegeState.Deny));
		assertThrows(IllegalArgumentException.class, () -> alter(invalid, new Catalog()));
	}
}
