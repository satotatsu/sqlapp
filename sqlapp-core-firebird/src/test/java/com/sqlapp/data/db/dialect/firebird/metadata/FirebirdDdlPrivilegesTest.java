/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdDdlPrivileges;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.UserPrivilege;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class FirebirdDdlPrivilegesTest {
	@Test
	void mapsObjectClassesSeparatelyFromIndividualObjectCodes() {
		String[] types = { "DATABASE", "TABLE", "VIEW", "PROCEDURE", "FUNCTION", "PACKAGE", "SEQUENCE", "DOMAIN",
				"EXCEPTION", "ROLE", "CHARACTER SET", "COLLATION", "FILTER" };
		for (int i = 0; i < types.length; i++) {
			for (var dialect : new com.sqlapp.data.db.dialect.Dialect[] { DialectHolder.defaultDialect30,
					DialectHolder.defaultDialect40 }) {
				int code = i + (dialect == DialectHolder.defaultDialect30 ? 20 : 21);
				assertEquals("CREATE " + types[i], FirebirdDdlPrivileges.fromCatalog("C", code, dialect));
				assertEquals((i == 0 ? "ALTER " : "ALTER ANY ") + types[i],
						FirebirdDdlPrivileges.fromCatalog("L", code, dialect));
				assertEquals((i == 0 ? "DROP " : "DROP ANY ") + types[i],
						FirebirdDdlPrivileges.fromCatalog("O", code, dialect));
			}
		}
		assertThrows(IllegalArgumentException.class,
				() -> FirebirdDdlPrivileges.fromCatalog("L", 0, DialectHolder.defaultDialect30));
		assertThrows(IllegalArgumentException.class,
				() -> FirebirdDdlPrivileges.fromCatalog("X", 22, DialectHolder.defaultDialect40));
	}

	@Test
	void readsAndRecreatesManyDdlGrantsInOneQuery() {
		int[] queries = { 0 };
		var reader = new Firebird30UserPrivilegeReader(DialectHolder.defaultDialect30) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				for (int i = 0; i < 50; i++) {
					var values = Map.<String, Object>of("grantee", "reader_" + i, "grantee_type", 13, "grantor",
							"owner", "privilege_type", "L", "object_type", 23, "grant_option", 1);
					try {
						handler.handleResultSetNext(row(values));
					} catch (SQLException e) {
						throw new AssertionError(e);
					}
				}
				return null;
			}
		};
		var privileges = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(50, privileges.size());
		assertEquals(1, queries[0]);
		String sql = DialectHolder.defaultDialect30.createSqlFactoryRegistry()
				.createSql(privileges.get(0), SqlType.GRANT).get(0).getSqlText();
		assertEquals("GRANT ALTER ANY PROCEDURE TO ROLE \"reader_0\" WITH GRANT OPTION GRANTED BY \"owner\"", sql);
	}

	@Test
	void readsDatabaseCreatorsOnlyWhenEnabledAndInOneBulkQuery() {
		int[] queries = { 0 };
		var reader = new Firebird30UserPrivilegeReader(DialectHolder.defaultDialect30) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				if (queries[0] == 2) {
					for (int i = 0; i < 50; i++) {
						try {
							handler.handleResultSetNext(
									row(Map.of("grantee", "creator_" + i, "grantee_type", i % 2 == 0 ? 8 : 13)));
						} catch (SQLException e) {
							throw new AssertionError(e);
						}
					}
				}
				return null;
			}
		};
		assertFalse(reader.isReadDatabaseCreators());
		assertTrue(reader.doGetAll(null, new ParametersContext(), null).isEmpty());
		assertEquals(1, queries[0]);
		queries[0] = 0;
		reader.setReadDatabaseCreators(true);
		var privileges = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(2, queries[0]);
		assertEquals(50, privileges.size());
		for (int i = 0; i < privileges.size(); i++) {
			var privilege = privileges.get(i);
			assertEquals("CREATE DATABASE", privilege.getPrivilege());
			assertFalse(privilege.isGrantable());
			assertNull(privilege.getGrantorName());
			String sql = DialectHolder.defaultDialect30.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT)
					.get(0).getSqlText();
			assertEquals("GRANT CREATE DATABASE TO " + (i % 2 == 0 ? "USER" : "ROLE") + " \"creator_" + i + "\"", sql);
		}
	}

	@Test
	void validatesDdlGrantOptionsRecipientsAndVersion() {
		var registry = DialectHolder.defaultDialect30.createSqlFactoryRegistry();
		UserPrivilege privilege = new UserPrivilege().setGranteeName("reader").setPrivilege("create table")
				.setGrantable(true);
		assertEquals("GRANT CREATE TABLE TO USER \"reader\" WITH GRANT OPTION",
				registry.createSql(privilege, SqlType.GRANT).get(0).getSqlText());
		assertEquals("REVOKE CREATE TABLE FROM USER \"reader\"",
				registry.createSql(privilege, SqlType.REVOKE).get(0).getSqlText());
		UserPrivilege rolePrivilege = new UserPrivilege().setPrivilege("CREATE TABLE")
				.setGrantee(new com.sqlapp.data.schemas.Role("readers"));
		assertEquals("GRANT CREATE TABLE TO ROLE \"readers\"",
				registry.createSql(rolePrivilege, SqlType.GRANT).get(0).getSqlText());
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT));
		privilege.setPrivilege("CREATE DATABASE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege, SqlType.GRANT));
		privilege.setPrivilege("CREATE TABLE; DROP DATABASE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege, SqlType.GRANT));
		privilege.setPrivilege("CREATE TABLE").setState(PrivilegeState.Deny);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege, SqlType.GRANT));
		privilege.setState((PrivilegeState) null).setAdmin(true);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege, SqlType.GRANT));
		privilege.setAdmin(false).getSpecifics().put(FirebirdPrivilege.GRANTEE_TYPE, "PROCEDURE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege, SqlType.GRANT));
		assertNull(DialectHolder.defaultDialect25.getCatalogReader().getUserPrivilegeReader());
		assertEquals(Firebird40UserPrivilegeReader.class,
				DialectHolder.defaultDialect50.getCatalogReader().getUserPrivilegeReader().getClass());
	}

	private static ExResultSet row(Map<String, Object> values) {
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
