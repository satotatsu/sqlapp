/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;

class FirebirdPrivilegeTest {
	@Test
	void restoresGrantsToSystemPrivilegesUsingNamesInsteadOfNumericCatalogRecipients() throws Exception {
		var values = privileges();
		values.put("grantee_type", 20);
		values.put("grantee", "1");
		var privilege = new FirebirdObjectPrivilegeReader(DialectHolder.defaultDialect40).createPrivilege(row(values));
		assertEquals("USER_MANAGEMENT", privilege.getGranteeName());
		assertTrue(sql(DialectHolder.defaultDialect40, privilege, SqlType.GRANT)
				.contains("TO SYSTEM PRIVILEGE USER_MANAGEMENT"));
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect30, privilege, SqlType.GRANT));
		privilege.setGranteeName("USER_MANAGEMENT,READ_RAW_PAGES");
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect40, privilege, SqlType.GRANT));
	}

	@Test
	void revokesOptionsWithoutRemovingTheUnderlyingPrivilegeOrMembership() {
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		ObjectPrivilege privilege = new ObjectPrivilege().setObjectName("orders").setGranteeName("reader")
				.setPrivilege("SELECT");
		privilege.getSpecifics().put(FirebirdPrivilege.REVOKE_OPTION, "GRANT");
		assertTrue(registry.createSql(privilege, SqlType.REVOKE).get(0).getSqlText()
				.startsWith("REVOKE GRANT OPTION FOR SELECT ON TABLE"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege, SqlType.GRANT));
		RoleMember member = new RoleMember().setMemberRoleName("reporting").setGranteeName("reader");
		member.getSpecifics().put(FirebirdPrivilege.REVOKE_OPTION, "ADMIN");
		assertTrue(
				registry.createSql(member, SqlType.REVOKE).get(0).getSqlText().startsWith("REVOKE ADMIN OPTION FOR"));
		member.getSpecifics().put(FirebirdPrivilege.REVOKE_OPTION, "DEFAULT");
		assertTrue(registry.createSql(member, SqlType.REVOKE).get(0).getSqlText().startsWith("REVOKE DEFAULT"));
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect30.createSqlFactoryRegistry().createSql(member, SqlType.REVOKE));
		member.getSpecifics().put(FirebirdPrivilege.REVOKE_OPTION, "GRANT");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(member, SqlType.REVOKE));
	}

	@Test
	void readsManyRoleMembershipsWithOneQuery() {
		int[] queries = { 0 };
		var reader = new FirebirdRoleMemberReader(DialectHolder.defaultDialect40) {
			@Override
			protected com.sqlapp.jdbc.sql.JdbcQueryHandler execute(java.sql.Connection connection,
					com.sqlapp.jdbc.sql.node.SqlNode node, com.sqlapp.data.parameter.ParametersContext context,
					com.sqlapp.jdbc.sql.ResultSetNextHandler handler) {
				queries[0]++;
				for (int i = 0; i < 50; i++) {
					try {
						handler.handleResultSetNext(row(Map.of("role_name", "reporting", "grantee", "user" + i,
								"grantee_type", 8, "grant_option", 1)));
					} catch (java.sql.SQLException e) {
						throw new AssertionError(e);
					}
				}
				return null;
			}
		};
		var members = reader.doGetAll(null, new com.sqlapp.data.parameter.ParametersContext(), null);
		assertEquals(50, members.size());
		assertEquals(1, queries[0]);
		assertTrue(members.stream().allMatch(RoleMember::isAdmin));
		assertTrue(members.stream().noneMatch(RoleMember::isDefault));
	}

	@Test
	void usesVersionSafeRoleCatalogsAndKeepsPrivilegeQueriesSeparate() {
		var context = new com.sqlapp.data.parameter.ParametersContext();
		context.put("readerOptions", new com.sqlapp.data.db.metadata.ReaderOptions());
		var legacy = new FirebirdRoleReader(DialectHolder.defaultDialect).getSqlSqlNode(null);
		String legacySql = render(legacy, context);
		assertFalse(legacySql.contains("RDB$DESCRIPTION"), legacySql);
		assertFalse(legacySql.contains("RDB$SYSTEM_FLAG"), legacySql);
		var modern = new Firebird20RoleReader(DialectHolder.defaultDialect20).getSqlSqlNode(null);
		assertTrue(render(modern, context).contains("RDB$DESCRIPTION"));
		context.put("objectName", java.util.List.of("orders"));
		var objectSql = render(new FirebirdObjectPrivilegeReader(DialectHolder.defaultDialect30).getSqlSqlNode(null),
				context);
		assertTrue(objectSql.contains("RDB$PRIVILEGE IN ('S', 'I', 'U', 'D', 'R', 'A', 'G')"), objectSql);
		assertFalse(objectSql.contains("'M'"), objectSql);
		var routineSql = render(new FirebirdRoutinePrivilegeReader(DialectHolder.defaultDialect30).getSqlSqlNode(null),
				context);
		assertTrue(routineSql.contains("RDB$PRIVILEGE = 'X'"), routineSql);
		assertTrue(routineSql.contains("RDB$OBJECT_TYPE IN (5, 15, 18)"), routineSql);
		var memberSql = render(new FirebirdRoleMemberReader(DialectHolder.defaultDialect40).getSqlSqlNode(null),
				context);
		assertTrue(memberSql.contains("RDB$PRIVILEGE = 'M'"), memberSql);
	}

	private String render(com.sqlapp.jdbc.sql.node.SqlNode node, com.sqlapp.data.parameter.ParametersContext context) {
		var parameters = new com.sqlapp.jdbc.sql.SqlParameterCollection();
		node.eval(context, parameters);
		return parameters.getSql();
	}

	private String sql(Dialect dialect, DbObject<?> object, SqlType type) {
		return dialect.createSqlFactoryRegistry().createSql(object, type).get(0).getSqlText();
	}

	@Test
	void restoresTableGrantIdentityAndGrantorAndQuotesIdentifiers() throws Exception {
		var values = privileges();
		values.put("object_name", "order\"detail");
		values.put("grantee_type", 13);
		var privilege = new FirebirdObjectPrivilegeReader(DialectHolder.defaultDialect25).createPrivilege(row(values));
		assertEquals("TABLE", privilege.getSpecifics().get(FirebirdPrivilege.OBJECT_TYPE));
		assertEquals("ROLE", privilege.getSpecifics().get(FirebirdPrivilege.GRANTEE_TYPE));
		String text = sql(DialectHolder.defaultDialect25, privilege, SqlType.GRANT);
		assertEquals(
				"GRANT SELECT ON TABLE \"order\"\"detail\" TO ROLE \"reader\" WITH GRANT OPTION GRANTED BY \"owner\"",
				text);
		text = sql(DialectHolder.defaultDialect25, privilege, SqlType.REVOKE);
		assertTrue(text.startsWith("REVOKE SELECT ON TABLE"), text);
		assertTrue(text.contains(" FROM ROLE \"reader\""), text);
		assertFalse(text.contains("WITH GRANT OPTION"), text);
	}

	@Test
	void restoresRoutineKindsWithoutGuessingFromTheirNames() throws Exception {
		for (int code : new int[] { 5, 15, 18 }) {
			var values = privileges();
			values.put("object_type", code);
			values.put("privilege_type", "X");
			var privilege = new FirebirdRoutinePrivilegeReader(DialectHolder.defaultDialect30)
					.createObject(row(values));
			String text = sql(DialectHolder.defaultDialect30, privilege, SqlType.GRANT);
			assertTrue(text.contains("EXECUTE ON " + FirebirdPrivilege.objectType(code)), text);
			if (code != 5)
				assertThrows(IllegalArgumentException.class,
						() -> sql(DialectHolder.defaultDialect25, privilege, SqlType.GRANT));
		}
	}

	@Test
	void restoresSequenceUsageAndPublicRecipient() throws Exception {
		var values = privileges();
		values.put("object_type", 14);
		values.put("privilege_type", "G");
		values.put("grantee", "PUBLIC");
		values.put("is_grantable", 0);
		var privilege = new FirebirdObjectPrivilegeReader(DialectHolder.defaultDialect30).createPrivilege(row(values));
		String text = sql(DialectHolder.defaultDialect30, privilege, SqlType.GRANT);
		assertTrue(text.contains("USAGE ON SEQUENCE \"orders\" TO PUBLIC"), text);
		assertFalse(text.contains("USER \"PUBLIC\""), text);
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect25, privilege, SqlType.GRANT));
	}

	@Test
	void restoresColumnGrantWithoutExpandingItToTheWholeTable() throws Exception {
		var values = privileges();
		values.put("privilege_type", "U");
		values.put("column_name", "limited");
		values.put("grantee_type", 5);
		var privilege = new FirebirdColumnPrivilegeReader(DialectHolder.defaultDialect25)
				.createColumnPrivilege(row(values));
		String text = sql(DialectHolder.defaultDialect25, privilege, SqlType.GRANT);
		assertTrue(text.matches(".*UPDATE \\(\\s*\"limited\"\\) ON TABLE \"orders\" TO PROCEDURE \"reader\".*"), text);
		ColumnPrivilege missingColumn = new ColumnPrivilege();
		missingColumn.setObjectName("orders").setGranteeName("reader").setPrivilege("UPDATE");
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect25, missingColumn, SqlType.GRANT));
	}

	@Test
	void restoresAdminAndDefaultMembershipIncludingGrantor() throws Exception {
		var values = new HashMap<String, Object>();
		values.put("role_name", "reporting");
		values.put("grantee", "analyst");
		values.put("grantee_type", 13);
		values.put("grantor", "owner");
		values.put("grant_option", 2);
		values.put("default_marker", "D");
		var member = new FirebirdRoleMemberReader(DialectHolder.defaultDialect40).createObject(row(values));
		assertTrue(member.isAdmin());
		assertTrue(member.isDefault());
		assertEquals("GRANT DEFAULT \"reporting\" TO ROLE \"analyst\" WITH ADMIN OPTION GRANTED BY \"owner\"",
				sql(DialectHolder.defaultDialect40, member, SqlType.GRANT));
		assertEquals("REVOKE \"reporting\" FROM ROLE \"analyst\" GRANTED BY \"owner\"",
				sql(DialectHolder.defaultDialect40, member, SqlType.REVOKE));
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect30, member, SqlType.GRANT));
	}

	@Test
	void createsAndDropsRolesAndPreservesRoleComments() {
		Role role = new Role("reporting");
		role.setRemarks("reader's role");
		var operations = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(role, SqlType.CREATE);
		assertEquals(2, operations.size());
		assertEquals("CREATE ROLE \"reporting\"", operations.get(0).getSqlText());
		assertEquals("COMMENT ON ROLE \"reporting\" IS 'reader''s role'", operations.get(1).getSqlText());
		assertEquals("DROP ROLE \"reporting\"", sql(DialectHolder.defaultDialect25, role, SqlType.DROP));
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect, role, SqlType.CREATE));
	}

	@Test
	void rejectsUnsupportedPrivilegesAndConflictingStateInsteadOfGeneratingSql() {
		ObjectPrivilege privilege = new ObjectPrivilege();
		privilege.setObjectName("orders").setGranteeName("reader").setPrivilege("SELECT; DROP TABLE orders");
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect30, privilege, SqlType.GRANT));
		privilege.setPrivilege("SELECT").setState(PrivilegeState.Deny);
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect30, privilege, SqlType.GRANT));
		privilege.setState((PrivilegeState) null).setGrantorName("owner");
		assertThrows(IllegalArgumentException.class,
				() -> sql(DialectHolder.defaultDialect20, privilege, SqlType.GRANT));
	}

	@Test
	void wiresReadersAtTheCorrectVersionBoundaries() {
		assertEquals(FirebirdRoleReader.class,
				DialectHolder.defaultDialect.getCatalogReader().getRoleReader().getClass());
		for (var dialect : new Dialect[] { DialectHolder.defaultDialect20, DialectHolder.defaultDialect25,
				DialectHolder.defaultDialect30, DialectHolder.defaultDialect40, DialectHolder.defaultDialect50 }) {
			assertEquals(dialect.compareTo(DialectHolder.defaultDialect40) >= 0 ? Firebird40RoleReader.class
					: Firebird20RoleReader.class, dialect.getCatalogReader().getRoleReader().getClass());
			assertEquals(FirebirdRoleMemberReader.class, dialect.getCatalogReader().getRoleMemberReader().getClass());
			assertEquals(FirebirdRoutinePrivilegeReader.class,
					dialect.getCatalogReader().getRoutinePrivilegeReader().getClass());
		}
	}

	private Map<String, Object> privileges() {
		return new HashMap<>(Map.of("object_name", "orders", "object_type", 0, "grantee", "reader", "grantee_type", 8,
				"grantor", "owner", "privilege_type", "S", "is_grantable", 1));
	}

	private ExResultSet row(Map<String, Object> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return values.get(key) == null ? null : values.get(key).toString();
			}

			@Override
			public int getInt(String key) {
				return values.get(key) == null ? 0 : ((Number) values.get(key)).intValue();
			}
		};
	}
}
