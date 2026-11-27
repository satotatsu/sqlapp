/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class FirebirdStorageMetadataTest {
	@Test
	void monitoringCatalogIsOnlyUsedOn21OrLater() {
		var reader = new FirebirdTableSpaceReader(DialectHolder.defaultDialect20);
		for (int minor : new int[] { 0, 1, 5 }) {
			var sql = new SqlParameterCollection();
			reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(2).setMinorVersion(minor))
					.eval(new ParametersContext(), sql);
			assertEquals(minor >= 1, sql.getSql().contains("FROM MON$DATABASE"));
			assertEquals(minor == 0, sql.getSql().contains("RDB$GET_CONTEXT"));
		}
		assertNull(DialectHolder.defaultDialect.getCatalogReader().getTableSpaceReader());
		assertNotNull(DialectHolder.defaultDialect20.getCatalogReader().getTableSpaceReader());
	}

	@Test
	void storageNamesPreserveDotsAndAcceptAliasesInOneRead() {
		for (String path : new String[] { "C:\\data\\my.database.fdb", "/data/my.database.fdb", "my.database" }) {
			int[] queries = { 0 };
			var reader = reader(path, queries);
			var result = reader.doGetAll(null, new ParametersContext(),
					new ProductVersionInfo().setMajorVersion(2).setMinorVersion(0));
			assertEquals(1, queries[0]);
			assertEquals("my.database", result.get(0).getName());
			assertEquals(path, result.get(0).getTableSpaceFiles().get(0).getName());
			assertNull(result.get(0).getCreatedAt());
		}
	}

	@Test
	void missingDatabaseIdentityFailsClearly() {
		var failure = assertThrows(IllegalStateException.class,
				() -> reader(null, new int[1]).doGetAll(null, new ParametersContext(), null));
		assertTrue(failure.getCause().getMessage().contains("database path or alias"));
	}

	private FirebirdTableSpaceReader reader(String path, int[] queries) {
		return new FirebirdTableSpaceReader(DialectHolder.defaultDialect20) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				try {
					handler.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String key) {
							return path;
						}

						@Override
						public java.sql.Timestamp getTimestamp(String key) {
							return null;
						}
					});
				} catch (SQLException exception) {
					throw new IllegalStateException(exception);
				}
				return null;
			}
		};
	}
}
