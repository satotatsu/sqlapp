/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSystemPrivileges;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class FirebirdSystemPrivilegesTest {
	private static final String KEY = FirebirdSystemPrivileges.ROLE_PRIVILEGES;

	@Test
	void readsManyRolesAndTheirBitmapsInOneQuery() {
		int[] queries = { 0 };
		var reader = new Firebird40RoleReader(DialectHolder.defaultDialect40) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				for (int i = 0; i < 50; i++) {
					String name = "role_" + i;
					var row = new ExResultSet(null) {
						@Override
						public String getString(String key) {
							return Map.of("role_name", name, "owner_name", "SYSDBA").get(key);
						}

						@Override
						public byte[] getBytes(String key) {
							return new byte[] { 2, 1, 1, 4, 0, 0, 0, 0 };
						}
					};
					try {
						handler.handleResultSetNext(row);
					} catch (SQLException e) {
						throw new AssertionError(e);
					}
				}
				return null;
			}
		};
		var roles = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(50, roles.size());
		assertEquals(1, queries[0]);
		assertEquals("USER_MANAGEMENT,ACCESS_SHUTDOWN_DATABASE,SELECT_ANY_OBJECT_IN_DATABASE,REPLICATE_INTO_DATABASE",
				roles.get(0).getSpecifics().get(KEY));
	}

	@Test
	void rejectsUnknownBitsAndUnsupportedVersions() {
		byte[] bitmap = new byte[8];
		bitmap[3] = 8;
		assertEquals("PROFILE_ANY_ATTACHMENT", FirebirdSystemPrivileges.decode(bitmap, DialectHolder.defaultDialect50));
		assertThrows(IllegalArgumentException.class,
				() -> FirebirdSystemPrivileges.decode(bitmap, DialectHolder.defaultDialect40));
		bitmap[7] = (byte) 128;
		assertThrows(IllegalArgumentException.class,
				() -> FirebirdSystemPrivileges.decode(bitmap, DialectHolder.defaultDialect50));
		assertThrows(IllegalArgumentException.class,
				() -> FirebirdSystemPrivileges.normalize("USER_MANAGEMENT", DialectHolder.defaultDialect30));
		for (String invalid : new String[] { "", "USER_MANAGEMENT,", "USER_MANAGEMENT,USER_MANAGEMENT",
				"USER_MANAGEMENT; DROP ROLE x" })
			assertThrows(IllegalArgumentException.class,
					() -> FirebirdSystemPrivileges.normalize(invalid, DialectHolder.defaultDialect50));
	}

	@Test
	void createsAndAltersRolesWithoutLosingOtherGrants() {
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		Role original = new Role("reader");
		original.getSpecifics().put(KEY, "select_any_object_in_database");
		assertTrue(registry.createSql(original, SqlType.CREATE).get(0).getSqlText()
				.endsWith(" SET SYSTEM PRIVILEGES TO SELECT_ANY_OBJECT_IN_DATABASE"));
		Role target = original.clone();
		target.getSpecifics().put(KEY, "MONITOR_ANY_ATTACHMENT");
		String alter = registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText();
		assertTrue(alter.startsWith("ALTER ROLE"), alter);
		assertTrue(alter.endsWith(" SET SYSTEM PRIVILEGES TO MONITOR_ANY_ATTACHMENT"), alter);
		target.getSpecifics().remove(KEY);
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText()
				.endsWith(" DROP SYSTEM PRIVILEGES"));
		target.getSpecifics().put("firebird.owner", "another_owner");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void keepsLegacyRoleQueriesFreeOfTheNewColumn() {
		ParametersContext context = new ParametersContext();
		context.put("readerOptions", new com.sqlapp.data.db.metadata.ReaderOptions());
		var sql = new com.sqlapp.jdbc.sql.SqlParameterCollection();
		new Firebird20RoleReader(DialectHolder.defaultDialect30).getSqlSqlNode(null).eval(context, sql);
		assertFalse(sql.getSql().contains("RDB$SYSTEM_PRIVILEGES"));
	}
}
