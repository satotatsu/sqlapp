/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.User;

class InformixUserFactoriesTest {
	private User user(String kind) {
		var user = new User("analyst");
		user.getSpecifics().put("INFORMIX_USER_TYPE", kind);
		return user;
	}

	private User role(User user, String role) {
		if (role == null)
			user.getSpecifics().remove("INFORMIX_DEFAULT_ROLE");
		else
			user.getSpecifics().put("INFORMIX_DEFAULT_ROLE", role);
		return user;
	}

	private List<String> sql(User user, SqlType type) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(user, type).stream()
				.map(o -> o.getSqlText()).toList();
	}

	private List<String> alter(User before, User after) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(before.diff(after), SqlType.ALTER)
				.stream().map(o -> o.getSqlText()).toList();
	}

	@Test
	void defaultsToConnectWithoutInferringAdministrativeAccess() {
		assertEquals(List.of("GRANT CONNECT TO \"analyst\""), sql(new User("analyst"), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> sql(new User("analyst").setAdmin(true), SqlType.CREATE));
		assertEquals(List.of("GRANT DBA TO \"analyst\""), sql(user("D"), SqlType.GRANT));
	}

	@Test
	void dropsDatabaseAuthorizationInHierarchyOrder() {
		assertEquals(List.of("REVOKE DEFAULT ROLE FROM \"analyst\"", "REVOKE DBA FROM \"analyst\"",
				"REVOKE CONNECT FROM \"analyst\""), sql(role(user("D"), "reports"), SqlType.DROP));
		assertEquals(List.of("REVOKE RESOURCE FROM \"analyst\"", "REVOKE CONNECT FROM \"analyst\""),
				sql(user("R"), SqlType.REVOKE));
	}

	@Test
	void preservesImplicitAccessWithoutGrantingExplicitConnect() {
		assertEquals(List.of("GRANT DEFAULT ROLE \"reports\" TO \"analyst\""),
				sql(role(user("U"), "reports"), SqlType.CREATE));
		assertEquals(List.of("REVOKE DEFAULT ROLE FROM \"analyst\""), sql(role(user("U"), "reports"), SqlType.DROP));
		assertThrows(IllegalArgumentException.class, () -> sql(user("U"), SqlType.CREATE));
	}

	@Test
	void upgradesAndDowngradesWithoutDroppingConnectOrMembership() {
		assertEquals(List.of("REVOKE DBA FROM \"analyst\"", "GRANT RESOURCE TO \"analyst\""),
				alter(user("D"), user("R")));
		assertEquals(List.of("REVOKE RESOURCE FROM \"analyst\"", "GRANT CONNECT TO \"analyst\""),
				alter(user("R"), user("C")));
		assertEquals(List.of("GRANT DBA TO \"analyst\""), alter(user("R"), user("D")));
		assertTrue(alter(user("C"), user("C")).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> alter(user("C"), role(user("U"), "reports")));
	}

	@Test
	void changesDefaultsWithoutRevokingExistingRoleMembership() {
		assertEquals(List.of("GRANT DEFAULT ROLE \"new\" TO \"analyst\""),
				alter(role(user("C"), "old"), role(user("C"), "new")));
		assertEquals(List.of("REVOKE DEFAULT ROLE FROM \"analyst\""), alter(role(user("C"), "old"), user("C")));
	}

	@Test
	void handlesPublicAndEscapedIdentifiers() {
		assertEquals(List.of("GRANT CONNECT TO PUBLIC"), sql(new User("public"), SqlType.GRANT));
		assertEquals(List.of("GRANT CONNECT TO \"odd\"\"name\""), sql(new User("odd\"name"), SqlType.GRANT));
	}

	@Test
	void rejectsUnsupportedAccountConfigurationBeforeReturningSql() {
		assertThrows(IllegalArgumentException.class, () -> sql(new User(), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> sql(user("G"), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> sql(user("C").setPassword("secret"), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> sql(user("C").setLoginUserName("login"), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> alter(user("C"), user("C").setName("renamed")));
		assertThrows(IllegalArgumentException.class, () -> sql(user("C"), SqlType.ALTER));
	}

	@Test
	void preservesAuthorizationThroughXml() throws Exception {
		var original = role(user("D"), "reports");
		User restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
		assertEquals(sql(original, SqlType.CREATE), sql(restored, SqlType.CREATE));
	}

	@Test
	void resolvesNativeFactoriesThroughDifferenceStateAcrossVersions() {
		for (int version : new int[] { 11, 12, 14, 15 }) {
			var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("Informix", version,
					version == 11 ? 70 : 10, null);
			var registry = dialect.createSqlFactoryRegistry();
			var before = user("D");
			var after = user("C");
			assertEquals(alter(before, after),
					registry.createSql(before.diff(after)).stream().map(o -> o.getSqlText()).toList());
		}
	}
}
