/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleObjectMetadataBulkTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return values.get(key);
			}

			@Override
			public Object getObject(String key) {
				return values.get(key);
			}

			@Override
			public Timestamp getTimestamp(String key) {
				return null;
			}
		};
	}

	private String ddl(String name) {
		return "CREATE MATERIALIZED VIEW APP." + name
				+ " ON PREBUILT TABLE WITH REDUCED PRECISION REFRESH COMPLETE ON DEMAND START WITH SYSDATE+1 NEXT SYSDATE+7 ENABLE QUERY REWRITE AS SELECT ID FROM APP.T";
	}

	@Test
	void materializedViewDefinitionsUseTwoBulkQueriesAndSurviveXml() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		var reader = new Oracle10gMviewReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				calls.incrementAndGet();
				try {
					for (int i = 0; i < 25; i++) {
						String name = "MV" + i;
						h.handleResultSetNext(row(Map.of("OWNER", "APP", "MVIEW_NAME", name, "QUERY",
								"SELECT ID FROM APP.T", "BUILD_MODE", "PREBUILT", "MVIEW_DEFINITION", ddl(name))));
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var views = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(2, calls.get());
		assertEquals(25, views.size());
		for (var view : views) {
			Mview restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
			assertEquals(ddl(view.getName()), DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText());
		}
	}

	@Test
	void catalogMetadataAndVersionBoundaryArePreserved() throws Exception {
		var reader = new Oracle10gMviewReader(DialectHolder.oracle10gDialect);
		assertTrue(reader.createTable(row(Map.of("OWNER", "APP", "MVIEW_NAME", "MV", "QUERY", "SELECT 1 FROM DUAL")))
				.getDefinition().isEmpty());
		assertEquals(OracleMviewReader.class,
				DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getMviewReader().getClass());
		for (var dialect : List.of(DialectHolder.oracle10gDialect, DialectHolder.oracle11gR2Dialect,
				DialectHolder.oracle19cDialect)) {
			assertEquals(Oracle10gMviewReader.class,
					dialect.getCatalogReader().getSchemaReader().getMviewReader().getClass());
		}
		for (var dialect : List.of(DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			var current = (Oracle23aiMviewReader) dialect.getCatalogReader().getSchemaReader().getMviewReader();
			assertEquals(Oracle23aiColumnReader.class, current.newColumnReader().getClass());
			assertEquals(Oracle23aiIndexReader.class, current.newIndexReader().getClass());
		}
	}

	@Test
	void privateDbLinksUseOneCatalogQueryWithoutCredentialLookups() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		var reader = new OracleDbLinkReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				calls.incrementAndGet();
				try {
					for (int i = 0; i < 25; i++)
						h.handleResultSetNext(row(Map.of("OWNER", "APP", "DB_LINK", "LINK" + i, "HOST", "remote")));
					h.handleResultSetNext(row(
							Map.of("OWNER", "APP", "DB_LINK", "FIXED", "USERNAME", "REMOTE_USER", "HOST", "remote")));
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var links = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		for (var link : links) {
			reader.setMetadataDetail(null, link);
			assertNull(link.getPassword());
			assertEquals("remote", link.getDataSource());
			assertEquals("APP", link.getSchemaName());
		}
		assertEquals(1, calls.get());
		assertEquals(26, links.size());
		assertEquals("REMOTE_USER", links.get(25).getUserId());
	}

	@Test
	void publicDbLinksUseOneCatalogQueryWithoutCredentialLookups() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		var reader = new OraclePublicDbLinkReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				calls.incrementAndGet();
				try {
					for (int i = 0; i < 25; i++)
						h.handleResultSetNext(row(Map.of("DB_LINK", "LINK" + i, "HOST", "remote")));
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var links = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		for (var link : links) {
			reader.setMetadataDetail(null, link);
			assertNull(link.getPassword());
		}
		assertEquals(1, calls.get());
		assertEquals(25, links.size());
	}
}
