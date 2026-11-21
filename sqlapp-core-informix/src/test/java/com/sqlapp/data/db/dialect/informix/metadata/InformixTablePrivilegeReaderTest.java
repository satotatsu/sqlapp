/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class InformixTablePrivilegeReaderTest {
	private Map<String, Object> row() {
		return new HashMap<>(Map.of("catalog_name", "db", "schema_name", "owner", "table_name", "items", "grantor",
				"owner", "grantee", "reader"));
	}

	@Test
	void installsSetBasedReaders() {
		var catalog = DialectHolder.defaultDialect.getCatalogReader();
		assertInstanceOf(InformixObjectPrivilegeReader.class, catalog.getObjectPrivilegeReader());
		assertInstanceOf(InformixColumnPrivilegeReader.class, catalog.getColumnPrivilegeReader());
	}

	@Test
	void decodesPositionsGrantOptionsAndColumnMarkersStrictly() {
		var privileges = InformixSecurityMetadata.privileges("SU*idxarN", false);
		assertEquals(8, privileges.size());
		assertEquals("UNDER", privileges.get(7).name());
		assertTrue(privileges.get(0).grantable());
		assertFalse(privileges.get(2).grantable());
		assertEquals(7, InformixSecurityMetadata.privileges("su-idxar", false).size());
		assertTrue(InformixSecurityMetadata.privileges("--*------", false).isEmpty());
		assertEquals(3, InformixSecurityMetadata.privileges("SuR", true).size());
		for (String invalid : new String[] { "", "u--------", "su?idxarn", "su-idxarz", "su-idxarn-" })
			assertThrows(IllegalStateException.class, () -> InformixSecurityMetadata.privileges(invalid, false));
	}

	@Test
	void readsManyTablesOnceAndRecreatesEachGrant() {
		var row = row();
		row.put("table_auth", "S--i-----");
		int[] queries = { 0 };
		var reader = new InformixObjectPrivilegeReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode node, ParametersContext p,
					ResultSetNextHandler handler) {
				queries[0]++;
				assertTrue(node.eval(p).getSql().contains("systabauth"));
				assertEquals("owner", p.get("schemaName"));
				assertTrue(node.eval(p).getSql().contains("t.owner IN"));
				try {
					for (int i = 0; i < 40; i++) {
						row.put("table_name", "table" + i);
						handler.handleResultSetNext(InformixMetadataReaderTest.row(row));
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		reader.setSchemaName("owner");
		var result = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, queries[0]);
		assertEquals(80, result.size());
		assertFalse(result.get(0).isHierachy());
		for (var privilege : result)
			assertEquals(1, DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(privilege, com.sqlapp.data.db.sql.SqlType.GRANT).size());
		row.put("grantor", null);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void readsColumnsOnceWithExactNamesAndGrantOptions() {
		var row = row();
		row.put("column_name", "col");
		row.put("column_auth", "sUR");
		int[] queries = { 0 };
		var reader = new InformixColumnPrivilegeReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode node, ParametersContext p,
					ResultSetNextHandler handler) {
				queries[0]++;
				assertTrue(node.eval(p).getSql().contains("syscolauth"));
				try {
					for (int i = 0; i < 30; i++)
						handler.handleResultSetNext(InformixMetadataReaderTest.row(row));
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var result = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, queries[0]);
		assertEquals(90, result.size());
		assertEquals("col", result.get(0).getColumnName());
		assertFalse(result.get(0).isGrantable());
		assertTrue(result.get(1).isGrantable());
		assertEquals(1, DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(result.get(0), com.sqlapp.data.db.sql.SqlType.GRANT).size());
	}
}
