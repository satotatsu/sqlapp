/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SapHanaSystemPrivilegeMetadataTest {
	private static void supply(ResultSetNextHandler handler) {
		try {
			for (int i = 0; i < 100; i++) {
				final int position = i;
				handler.handleResultSetNext(new ExResultSet(null) {
					@Override
					public String getString(String key) {
						return switch (key.toUpperCase(Locale.ROOT)) {
						case "GRANTEE" -> "Grantee " + position;
						case "GRANTOR" -> "OWNER";
						case "PRIVILEGE" -> "CATALOG READ";
						case "IS_GRANTABLE" -> position % 2 == 0 ? "TRUE" : "FALSE";
						default -> null;
						};
					}

					@Override
					public Object getObject(String key) {
						return key.equalsIgnoreCase("IS_VALID") ? (position % 2 == 0 ? "TRUE" : "FALSE") : null;
					}
				});
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void userSystemPrivilegesAreFetchedInOneQuery() {
		var queries = new AtomicInteger();
		var reader = new SapHanaUserPrivilegeReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				assertTrue(node.toString().contains("SYSTEMPRIVILEGE"));
				assertTrue(node.toString().contains("GRANTEE_TYPE = 'USER'"));
				supply(handler);
				return null;
			}
		};
		var grants = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(100, grants.size());
		assertTrue(grants.getFirst().isAdmin());
		assertFalse(grants.get(1).isAdmin());
		assertEquals("FALSE", grants.get(1).getSpecifics().get("IS_VALID"));
		assertEquals("Grantee 99", grants.getLast().getGranteeName());
		assertEquals("OWNER", grants.getLast().getGrantorName());
		assertEquals("CATALOG READ", grants.getLast().getPrivilege());
	}

	@Test
	void roleSystemPrivilegesAreFetchedInOneQuery() {
		var queries = new AtomicInteger();
		var reader = new SapHanaRolePrivilegeReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				assertTrue(node.toString().contains("SYSTEMPRIVILEGE"));
				assertTrue(node.toString().contains("GRANTEE_TYPE = 'ROLE'"));
				supply(handler);
				return null;
			}
		};
		var grants = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(100, grants.size());
		assertTrue(grants.getFirst().isAdmin());
		assertFalse(grants.get(1).isAdmin());
		assertEquals("FALSE", grants.get(1).getSpecifics().get("IS_VALID"));
		assertEquals("Grantee 99", grants.getLast().getGranteeName());
		assertEquals("OWNER", grants.getLast().getGrantorName());
		assertEquals("CATALOG READ", grants.getLast().getPrivilege());
	}
}
