/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleMetadataPermissionTest {
	private ExResultSet row() {
		var values = Map.of("OWNER", "APP", "MVIEW_NAME", "MV", "QUERY", "SELECT ID FROM T", "REWRITE_ENABLED", "Y", "USE_NO_INDEX", "Y");
		return row(values);
	}
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override public String getString(String key) { return values.get(key); }
			@Override public Object getObject(String key) { return values.get(key); }
		};
	}
	@Test
	void partialDomainDefinitionsAreDiscardedAfterPermissionFailure() {
		AtomicInteger calls = new AtomicInteger();
		var reader = new Oracle26aiDomainReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				if (calls.incrementAndGet() == 2) {
					try { h.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "F", "DOMAIN_KIND", "FLEXIBLE",
							"DOMAIN_DEFINITION", "CREATE FLEXIBLE DOMAIN APP.F ..."))); }
					catch (SQLException e) { throw new RuntimeException(e); }
					throw new RuntimeException(new SQLException("denied", "", 1031));
				}
				return null;
			}
		};
		assertTrue(reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).isEmpty());
		assertEquals(2, calls.get()); // No dependencies or flexible-column queries for discarded rows.
	}
	@Test
	void partialMviewDefinitionsAreDiscardedAndMissingDdlStillFails() {
		for (boolean denied : new boolean[] {true, false}) {
			AtomicInteger calls = new AtomicInteger();
			var reader = new Oracle10gMviewReader(DialectHolder.oracle26aiDialect) {
				@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
					int call = calls.incrementAndGet();
					try {
						if (call == 1) {
							h.handleResultSetNext(row());
							h.handleResultSetNext(row(Map.of("OWNER", "APP", "MVIEW_NAME", "MV2", "QUERY", "SELECT ID FROM T")));
						} else {
							h.handleResultSetNext(row(Map.of("OWNER", "APP", "MVIEW_NAME", "MV", "MVIEW_DEFINITION", "CREATE MATERIALIZED VIEW APP.MV AS SELECT ID FROM T")));
							if (denied) throw new RuntimeException(new SQLException("denied", "", 31603));
						}
					} catch (SQLException e) { throw new RuntimeException(e); }
					return null;
				}
			};
			if (denied) {
				var views = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
				assertEquals(2, views.size());
				for (var view : views) assertTrue(view.getDefinition().isEmpty());
			} else {
				assertTrue(assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo())).getMessage().contains("MV2"));
			}
			assertEquals(2, calls.get());
		}
	}
	@Test
	void emptyMviewCatalogDoesNotQueryDefinitions() {
		AtomicInteger calls = new AtomicInteger();
		var reader = new Oracle10gMviewReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				calls.incrementAndGet(); return null;
			}
		};
		assertTrue(reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).isEmpty());
		assertEquals(1, calls.get());
	}
	@Test
	void accessErrorsIncludeWrappedAndChainedSqlExceptionsOnly() {
		assertTrue(OracleMetadataAccess.isDenied(new RuntimeException(new SQLException("denied", "", 1031))));
		SQLException chained = new SQLException("wrapper"); chained.setNextException(new SQLException("inaccessible", "", 31603));
		assertTrue(OracleMetadataAccess.isDenied(chained));
		assertFalse(OracleMetadataAccess.isDenied(new RuntimeException(new SQLException("syntax", "", 900))));
	}
	@Test
	void domainPermissionFailureRetainsMetadataAndPreventsIncompleteRecreation() {
		AtomicInteger calls = new AtomicInteger();
		var reader = new Oracle26aiDomainReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				int call = calls.incrementAndGet();
				if (call == 3) throw new RuntimeException(new SQLException("test denied", "", 1031));
				if (call == 1) {
					var values = Map.of("OWNER", "APP", "DOMAIN_NAME", "D", "DATA_TYPE", "NUMBER", "NULLABLE", "Y");
					try { h.handleResultSetNext(new ExResultSet(null) {
						@Override public String getString(String key) { return values.get(key); }
						@Override public int getInt(String key) { return 0; }
						@Override public long getLong(String key) { return 0; }
						@Override public boolean getBoolean(String key) { return false; }
						@Override public boolean wasNull() { return true; }
					}); } catch (SQLException e) { throw new RuntimeException(e); }
				}
				return null;
			}
		};
		var domains = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, domains.size()); assertEquals("D", domains.get(0).getName()); assertEquals(4, calls.get());
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(domains.get(0), com.sqlapp.data.db.sql.SqlType.CREATE));
	}
	@Test
	void missingPrivilegeRetainsCatalogAndOtherErrorsPropagate() {
		for (int code : new int[] {1031, 31603, 900}) {
			AtomicInteger calls = new AtomicInteger();
			var reader = new Oracle10gMviewReader(DialectHolder.oracle26aiDialect) {
				@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
					if (calls.incrementAndGet() == 2) throw new RuntimeException(new SQLException("test error", "", code));
					try { h.handleResultSetNext(row()); } catch (SQLException e) { throw new RuntimeException(e); }
					return null;
				}
			};
			if (code == 900) assertThrows(RuntimeException.class, () -> reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()));
			else {
				var views = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
				assertEquals(1, views.size()); assertEquals(java.util.List.of("SELECT ID FROM T"), views.get(0).getStatement());
				assertTrue(views.get(0).getDefinition().isEmpty());
				String generated = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(views.get(0), com.sqlapp.data.db.sql.SqlType.CREATE).get(0).getSqlText();
				assertTrue(generated.contains("ENABLE QUERY REWRITE"));
				assertTrue(generated.contains("USING NO INDEX"));
			}
			assertEquals(2, calls.get());
		}
	}
}
