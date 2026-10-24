/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.User;

class SapHanaUserSqlTest {
	@Test
	void recreatesSchemaCreationPrivilegesWithoutTreatingThemAsTableGrants() {
		var grant = new com.sqlapp.data.schemas.ObjectPrivilege().setSchemaName("APP").setGranteeName("R")
				.setGrantable(true);
		grant.getSpecifics().put("OBJECT_TYPE", "SCHEMA");
		for (String action : java.util.List.of("CREATE ANY", "CREATE TEMPORARY TABLE",
				"CREATE OBJECT STRUCTURED PRIVILEGE", "CREATE VIRTUAL PACKAGE")) {
			grant.setPrivilege(action);
			assertTrue(registry.createSql(grant, SqlType.GRANT).getFirst().getSqlText()
					.contains("GRANT " + action + " ON SCHEMA APP TO R WITH GRANT OPTION"));
			assertTrue(registry.createSql(grant, SqlType.REVOKE).getFirst().getSqlText()
					.contains("REVOKE " + action + " ON SCHEMA APP FROM R"));
		}
		grant.setObjectName("T").setPrivilege("CREATE ANY");
		grant.getSpecifics().remove("OBJECT_TYPE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(grant, SqlType.GRANT));
	}

	private final com.sqlapp.data.db.sql.SqlFactoryRegistry registry = DialectHolder.defaultDialect
			.createSqlFactoryRegistry();

	@Test
	void createsRestrictedUsersWithExplicitCredentialAndSettings() {
		var user = new User("Odd User").setPassword("Abc\"9x!");
		user.getSpecifics().put("IS_RESTRICTED", "TRUE");
		user.getSpecifics().put("PASSWORD_CHANGE_NEEDED", "FALSE");
		user.getSpecifics().put("PASSWORD_LIFETIME_CHECK_ENABLED", "FALSE");
		user.getSpecifics().put("USER_DEACTIVATED", "TRUE");
		var sql = registry.createSql(user, SqlType.CREATE);
		assertEquals(3, sql.size());
		assertTrue(sql.getFirst().getSqlText().contains(
				"CREATE RESTRICTED USER \"Odd User\" PASSWORD \"Abc\"\"9x!\" NO FORCE_FIRST_PASSWORD_CHANGE"));
		assertTrue(sql.get(1).getSqlText().contains("DISABLE PASSWORD LIFETIME"));
		assertTrue(sql.getLast().getSqlText().contains("DEACTIVATE USER NOW"));
	}

	@Test
	void rejectsMissingCredentialsAndUnsupportedAuthentication() {
		var user = new User("U");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(user, SqlType.CREATE));
		user.setPassword("Abc9x!\n");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(user, SqlType.CREATE));
		user.setPassword("Abc9x!").setAdmin(true);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(user, SqlType.CREATE));
		user.setAdmin(false);
		user.getSpecifics().put("IS_PASSWORD_ENABLED", "FALSE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(user, SqlType.CREATE));
	}

	@Test
	void userDiffsAvoidUnrequestedPasswordResetAndPreserveForcePolicy() {
		var original = new User("U");
		original.getSpecifics().put("IS_RESTRICTED", "FALSE");
		original.getSpecifics().put("USER_DEACTIVATED", "FALSE");
		original.getSpecifics().put("PASSWORD_CHANGE_NEEDED", "FALSE");
		assertTrue(registry.createSql(original.diff(original.clone()), SqlType.ALTER).isEmpty());
		var target = original.clone();
		target.getSpecifics().put("USER_DEACTIVATED", "TRUE");
		var operations = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertTrue(operations.getFirst().getSqlText().contains("DEACTIVATE USER NOW"));
		target.setPassword("Fresh9x!");
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).getFirst().getSqlText()
				.contains("NO FORCE_FIRST_PASSWORD_CHANGE"));
		target.setName("Other");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void dropIsRestrictiveAndLifetimeChangesCannotHidePasswordPolicySideEffects() {
		assertEquals("DROP USER U", registry.createSql(new User("U"), SqlType.DROP).getFirst().getSqlText().trim());
		var original = new User("U");
		original.getSpecifics().put("PASSWORD_CHANGE_NEEDED", "FALSE");
		original.getSpecifics().put("PASSWORD_LIFETIME_CHECK_ENABLED", "FALSE");
		var target = original.clone();
		target.getSpecifics().put("PASSWORD_LIFETIME_CHECK_ENABLED", "TRUE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		target.setPassword("Fresh9x!");
		assertEquals(2, registry.createSql(original.diff(target), SqlType.ALTER).size());
	}

	@Test
	void createsRolesAndRendersUserAndRoleSystemPrivilegesWithAdminOption() {
		var role = new com.sqlapp.data.schemas.Role("Odd Role");
		assertTrue(
				registry.createSql(role, SqlType.CREATE).getFirst().getSqlText().contains("CREATE ROLE \"Odd Role\""));
		assertTrue(registry.createSql(role, SqlType.DROP).getFirst().getSqlText().contains("DROP ROLE \"Odd Role\""));
		var grant = new com.sqlapp.data.schemas.UserPrivilege().setGranteeName("Odd User").setPrivilege("CATALOG READ")
				.setAdmin(true);
		assertTrue(registry.createSql(grant, SqlType.GRANT).getFirst().getSqlText()
				.contains("GRANT CATALOG READ TO \"Odd User\" WITH ADMIN OPTION"));
		assertTrue(registry.createSql(grant, SqlType.REVOKE).getFirst().getSqlText()
				.contains("REVOKE CATALOG READ FROM \"Odd User\""));
		var roleGrant = new com.sqlapp.data.schemas.RolePrivilege().setGranteeName("Odd Role").setPrivilege("REPO.READ")
				.setAdmin(true);
		assertTrue(registry.createSql(roleGrant, SqlType.GRANT).getFirst().getSqlText().contains("WITH ADMIN OPTION"));
		grant.setPrivilege("USER ADMIN; DROP USER U");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(grant, SqlType.GRANT));
		role.getSpecifics().put("GLOBAL_IDENTITY", "EXTERNAL");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(role, SqlType.CREATE));
	}

	@Test
	void blankUserFlagsAreInvalidRatherThanUnspecified() {
		for (String key : java.util.List.of("IS_RESTRICTED", "IS_PASSWORD_ENABLED", "PASSWORD_CHANGE_NEEDED",
				"PASSWORD_LIFETIME_CHECK_ENABLED", "USER_DEACTIVATED")) {
			for (String value : java.util.List.of("", "   ", "\t")) {
				var user = new User("U");
				user.setPassword("Fresh9x!");
				user.getSpecifics().put(key, value);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(user, SqlType.CREATE), key);
			}
		}
	}

	@Test
	void invalidGrantsAreNotSilentlyReactivatedDuringReplay() {
		var object = new com.sqlapp.data.schemas.ObjectPrivilege().setObjectName("T").setGranteeName("U")
				.setPrivilege("SELECT");
		object.getSpecifics().put("IS_VALID", "FALSE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(object, SqlType.GRANT));
		assertDoesNotThrow(() -> registry.createSql(object, SqlType.REVOKE));
		var routine = new com.sqlapp.data.schemas.RoutinePrivilege().setObjectName("F").setGranteeName("U")
				.setPrivilege("EXECUTE");
		routine.getSpecifics().put("IS_VALID", "FALSE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(routine, SqlType.GRANT));
		assertDoesNotThrow(() -> registry.createSql(routine, SqlType.REVOKE));
		var system = new com.sqlapp.data.schemas.UserPrivilege().setGranteeName("U").setPrivilege("CATALOG READ");
		system.getSpecifics().put("IS_VALID", "FALSE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(system, SqlType.GRANT));
		assertDoesNotThrow(() -> registry.createSql(system, SqlType.REVOKE));
	}
}
