/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2DatabaseAuthority;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2UserPrivilegeReaderTest {
	private ProductVersionInfo version(int major, int minor) {
		return new ProductVersionInfo().setMajorVersion(major).setMinorVersion(minor).setRevision(0);
	}

	private Map<String, String> values(ProductVersionInfo version) {
		var values = new HashMap<>(Map.of("GRANTOR", "OWNER", "GRANTEE", "Operator", "GRANTEETYPE", "U"));
		for (var authority : Db2DatabaseAuthority.values())
			if (authority.supports(version))
				values.put(authority.columnName(), "N");
		return values;
	}

	@Test
	void legacyAndModernQueriesReadOnlySupportedColumns() throws Exception {
		var reader = new Db2UserPrivilegeReader(DialectHolder.Db2_1215Dialect);
		for (var version : java.util.List.of(version(9, 0), version(9, 1), version(9, 5), version(9, 7), version(10, 1),
				version(12, 1))) {
			var values = values(version);
			values.put("DBADMAUTH", "Y");
			values.put("CONNECTAUTH", "Y");
			var privileges = reader.createPrivileges(Db2MetadataParityTest.row(values), version);
			assertEquals(2, privileges.size());
			var dbadm = privileges.stream().filter(p -> p.getPrivilege().equals("DBADM")).findFirst().orElseThrow();
			assertEquals(version.gte(9, 7, 0) ? "SEPARATE" : "LEGACY", dbadm.getSpecifics().get("DBADM_MODEL"));
			assertFalse(dbadm.isGrantable());
			assertFalse(dbadm.isAdmin());
			String query = reader.getSqlNode(version).toString();
			assertEquals(version.gte(9, 1, 0), query.contains("a.SECURITYADMAUTH"));
			assertEquals(version.gte(9, 7, 0), query.contains("a.ACCESSCTRLAUTH"));
			assertEquals(version.gte(10, 1, 0), query.contains("a.CREATESECUREAUTH"));
		}
	}

	@Test
	void twentyPrincipalsUseOneSelectAndRetainTypedAuthorityIdentity() {
		AtomicInteger queries = new AtomicInteger();
		var version = version(12, 1);
		var reader = new Db2UserPrivilegeReader(DialectHolder.Db2_1215Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				assertNotNull(node);
				assertNull(context.get("userName"));
				for (int i = 0; i < 20; i++) {
					var values = values(version);
					values.put("GRANTEE", "Operator" + i);
					values.put("GRANTEETYPE", i % 2 == 0 ? "U" : "G");
					values.put("DATAACCESSAUTH", "Y");
					values.put("CREATESECUREAUTH", "Y");
					try {
						handler.handleResultSetNext(Db2MetadataParityTest.row(values));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		var privileges = reader.doGetAll(null, new ParametersContext(), version);
		assertEquals(40, privileges.size());
		assertEquals(1, queries.get());
		assertEquals("U", privileges.getFirst().getSpecifics().get("GRANTEE_TYPE"));
		assertEquals("G", privileges.getLast().getSpecifics().get("GRANTEE_TYPE"));
		assertInstanceOf(Db2UserPrivilegeReader.class,
				DialectHolder.Db2_1215Dialect.getCatalogReader().getUserPrivilegeReader());
	}

	@Test
	void invalidFlagsAndPrincipalTypesAreRejected() {
		var reader = new Db2UserPrivilegeReader(DialectHolder.Db2_1215Dialect);
		var version = version(12, 1);
		var values = values(version);
		for (String state : java.util.List.of("G", "?", "")) {
			values.put("CONNECTAUTH", state);
			assertThrows(SQLException.class, () -> reader.createPrivileges(Db2MetadataParityTest.row(values), version));
		}
		values.put("CONNECTAUTH", "Y");
		values.put("GRANTEETYPE", "?");
		assertThrows(SQLException.class, () -> reader.createPrivileges(Db2MetadataParityTest.row(values), version));
		values.remove("CONNECTAUTH");
		assertThrows(SQLException.class, () -> reader.createPrivileges(Db2MetadataParityTest.row(values), version));
	}
}
