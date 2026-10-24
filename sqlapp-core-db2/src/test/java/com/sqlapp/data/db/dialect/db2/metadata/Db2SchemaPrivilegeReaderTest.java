/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2SchemaPrivilegeReaderTest {
	private static ProductVersionInfo version(int revision) {
		return new ProductVersionInfo().setMajorVersion(11).setMinorVersion(5).setRevision(revision);
	}

	private static Map<String, String> values(boolean modern) {
		Map<String, String> values = new HashMap<>(Map.of("SCHEMANAME", "App.Schema", "GRANTOR", "OWNER", "GRANTEE",
				"TEAM", "GRANTEETYPE", "R", "ALTERINAUTH", "N", "CREATEINAUTH", "G", "DROPINAUTH", "Y"));
		if (modern) {
			for (String action : java.util.List.of("SELECTIN", "INSERTIN", "UPDATEIN", "DELETEIN", "EXECUTEIN",
					"SCHEMAADM", "ACCESSCTRL", "DATAACCESS", "LOAD"))
				values.put(action + "AUTH", "N");
		}
		return values;
	}

	@Test
	void legacyMetadataDoesNotReadModernColumns() throws Exception {
		var reader = new Db2SchemaPrivilegeReader(DialectHolder.Db2_1150Dialect) {
			SqlNode expected(String name) {
				return getSqlNodeCache().getString(name);
			}
		};
		var privileges = reader.createPrivileges(Db2MetadataParityTest.row(values(false)), version(4));
		assertEquals(2, privileges.size());
		assertEquals("CREATEIN", privileges.getFirst().getPrivilege());
		assertTrue(privileges.getFirst().isGrantable());
		assertFalse(privileges.getLast().isGrantable());
		assertEquals("App.Schema", privileges.getFirst().getSchemaName());
		assertEquals("R", privileges.getFirst().getSpecifics().get("GRANTEE_TYPE"));
		assertSame(reader.expected("schemaPrivileges.sql"), reader.getSqlNode(version(4)));
		assertSame(reader.expected("schemaPrivileges1155.sql"), reader.getSqlNode(version(5)));
	}

	@Test
	void modernMetadataPreservesAuthorityAndGrantOptionsAndRejectsInvalidFlags() throws Exception {
		var reader = new Db2SchemaPrivilegeReader(DialectHolder.Db2_1155Dialect);
		var values = values(true);
		values.put("SELECTINAUTH", "G");
		values.put("SCHEMAADMAUTH", "Y");
		var privileges = reader.createPrivileges(Db2MetadataParityTest.row(values), version(5));
		assertEquals(4, privileges.size());
		assertTrue(privileges.stream().anyMatch(p -> p.getPrivilege().equals("SELECTIN") && p.isGrantable()));
		assertTrue(privileges.stream().anyMatch(p -> p.getPrivilege().equals("SCHEMAADM") && !p.isGrantable()));
		values.put("SCHEMAADMAUTH", "G");
		assertThrows(SQLException.class, () -> reader.createPrivileges(Db2MetadataParityTest.row(values), version(5)));
		values.put("SCHEMAADMAUTH", "?");
		assertThrows(SQLException.class, () -> reader.createPrivileges(Db2MetadataParityTest.row(values), version(5)));
		values.remove("SCHEMAADMAUTH");
		assertThrows(SQLException.class, () -> reader.createPrivileges(Db2MetadataParityTest.row(values), version(5)));
	}

	@Test
	void twentySchemasUseOneFilteredQueryAndCatalogExposesReader() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2SchemaPrivilegeReader(DialectHolder.Db2_1155Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				assertNotNull(node);
				assertEquals("App.Schema", context.get("schemaName"));
				queries.incrementAndGet();
				for (int i = 0; i < 20; i++) {
					var values = values(true);
					values.put("SCHEMANAME", "S" + i);
					try {
						handler.handleResultSetNext(Db2MetadataParityTest.row(values));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		reader.setSchemaName("App.Schema");
		assertEquals(40, reader.doGetAll(null, new ParametersContext(), version(5)).size());
		assertEquals(1, queries.get());
		assertNotNull(((Db2CatalogReader) DialectHolder.Db2_1155Dialect.getCatalogReader()).getSchemaPrivilegeReader());
	}
}
