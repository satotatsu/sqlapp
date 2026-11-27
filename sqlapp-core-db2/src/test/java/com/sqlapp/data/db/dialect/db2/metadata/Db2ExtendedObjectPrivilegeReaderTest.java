/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2ExtendedObjectPrivilegeReaderTest {
	private Map<String, String> values(String kind, String action, String state) {
		return Map.of("schema_name", "APP", "object_name", "SAME", "GRANTOR", "OWNER", "GRANTEE", "PUBLIC",
				"GRANTEETYPE", "G", "OBJECT_TYPE", kind, "PRIVILEGE", action, "AUTH_STATE", state);
	}

	@Test
	void twentyObjectsOfBothKindsUseOneFilteredQuery() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2ExtendedObjectPrivilegeReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				assertNotNull(node);
				assertEquals("APP", context.get("schemaName"));
				assertNull(context.get("objectName"));
				queries.incrementAndGet();
				for (int i = 0; i < 20; i++) {
					try {
						var sequence = new java.util.HashMap<>(values("SEQUENCE", "USAGE", "G"));
						var index = new java.util.HashMap<>(values("INDEX", "CONTROL", "Y"));
						sequence.put("object_name", "SAME" + i);
						index.put("object_name", "SAME" + i);
						handler.handleResultSetNext(Db2MetadataParityTest.row(sequence));
						handler.handleResultSetNext(Db2MetadataParityTest.row(index));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		reader.setSchemaName("APP");
		var privileges = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(40, privileges.size());
		assertEquals(1, queries.get());
		assertEquals(Db2ObjectPrivileges.Kind.SEQUENCE, Db2ObjectPrivileges.fromModel(privileges.getFirst()).kind());
		assertEquals(Db2ObjectPrivileges.GranteeType.PUBLIC,
				Db2ObjectPrivileges.fromModel(privileges.getFirst()).granteeType());
		assertEquals(Db2ObjectPrivileges.Kind.INDEX, Db2ObjectPrivileges.fromModel(privileges.getLast()).kind());
	}

	@Test
	void serverGrantsUseTheSameSingleBulkQueryWithoutInventingASchema() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2ExtendedObjectPrivilegeReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				assertTrue(node.toString().contains("FROM SYSCAT.PASSTHRUAUTH"));
				for (int i = 0; i < 20; i++) {
					var row = new java.util.HashMap<>(values("SERVER", "PASSTHRU", "Y"));
					row.remove("schema_name");
					row.put("object_name", "S" + i);
					try {
						handler.handleResultSetNext(Db2MetadataParityTest.row(row));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		var grants = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(20, grants.size());
		for (var privilege : grants) {
			var grant = Db2ObjectPrivileges.fromModel(privilege);
			assertEquals(Db2ObjectPrivileges.Kind.SERVER, grant.kind());
			assertNull(grant.schemaName());
			assertFalse(grant.grantable());
		}
	}

	@Test
	void catalogLoadUsesExtensionAndLeavesTablePrivilegesAlone() throws Exception {
		var sequence = new Db2ExtendedObjectPrivilegeReader(DialectHolder.defaultDialect)
				.createPrivilege(Db2MetadataParityTest.row(values("SEQUENCE", "ALTER", "Y")));
		var reader = new Db2ExtendedObjectPrivilegeReader(DialectHolder.defaultDialect) {
			@Override
			public List<ObjectPrivilege> getAllFull(Connection connection) {
				return List.of(sequence);
			}
		};
		Catalog catalog = new Catalog();
		catalog.getObjectPrivileges().add(new ObjectPrivilege().setSchemaName("APP").setObjectName("SAME")
				.setPrivilege("ALTER").setGranteeName("PUBLIC"));
		reader.loadFull(null, catalog);
		assertEquals(1, catalog.getObjectPrivileges().size());
		assertEquals(1, Db2ObjectPrivileges.get(catalog).grants().size());
		assertNotNull(((Db2CatalogReader) DialectHolder.defaultDialect.getCatalogReader())
				.getExtendedObjectPrivilegeReader());
	}

	@Test
	void malformedStatesAndIndexGrantOptionAreRejected() {
		var reader = new Db2ExtendedObjectPrivilegeReader(DialectHolder.defaultDialect);
		assertThrows(SQLException.class,
				() -> reader.createPrivilege(Db2MetadataParityTest.row(values("SEQUENCE", "USAGE", "?"))));
		assertThrows(IllegalArgumentException.class,
				() -> reader.createPrivilege(Db2MetadataParityTest.row(values("INDEX", "CONTROL", "G"))));
		assertThrows(IllegalArgumentException.class,
				() -> reader.createPrivilege(Db2MetadataParityTest.row(values("TABLE", "CONTROL", "Y"))));
	}
}
