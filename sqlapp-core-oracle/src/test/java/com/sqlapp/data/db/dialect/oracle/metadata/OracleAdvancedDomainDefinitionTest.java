/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleAdvancedDomainDefinitionTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override public String getString(String name) { return values.get(name); }
			@Override public boolean getBoolean(String name) { return Boolean.parseBoolean(values.get(name)); }
		};
	}

	@Test
	void advancedDefinitionsSurviveOneBulkQueryAndXml() throws Exception {
		var definitions = Map.of(
				"STATUS", "CREATE DOMAIN APP.STATUS AS ENUM (New = 1, Open = 2)",
				"CHOICE", "CREATE FLEXIBLE DOMAIN APP.CHOICE (v) CHOOSE DOMAIN USING (kind VARCHAR2(10)) FROM DECODE(kind, 'A', APP.D(v))",
				"REQUIRED", "CREATE DOMAIN APP.REQUIRED AS NUMBER CONSTRAINT CK_REQUIRED NOT NULL DISABLE NOVALIDATE");
		AtomicInteger calls = new AtomicInteger();
		var reader = new Oracle26aiDomainReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				int call = calls.incrementAndGet();
				if (call == 4) {
					try {
						for (String part : java.util.List.of("v", "kind")) h.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "CHOICE",
								"COLUMN_NAME", part, "DISCRIMINANT", Boolean.toString(part.equals("kind")))));
					} catch (SQLException e) { throw new RuntimeException(e); }
					return null;
				}
				if (call == 3) {
					try {
						h.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "CHOICE", "REFERENCED_OWNER", "APP", "REFERENCED_NAME", "STATUS")));
						h.handleResultSetNext(row(Map.of("OWNER", "OTHER", "DOMAIN_NAME", "CHOICE", "REFERENCED_OWNER", "APP", "REFERENCED_NAME", "CHOICE")));
					} catch (SQLException e) { throw new RuntimeException(e); }
					return null;
				}
				if (call != 2) return null;
				try {
					for (var entry : definitions.entrySet()) h.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", entry.getKey(),
							"DOMAIN_KIND", entry.getKey().equals("STATUS") ? "ENUMERATED" : entry.getKey().equals("CHOICE") ? "FLEXIBLE" : "REGULAR",
							"DOMAIN_DEFINITION", entry.getValue())));
				} catch (SQLException e) { throw new RuntimeException(e); }
				return null;
			}
		};
		var domains = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(4, calls.get(), "Definitions, dependencies and flexible components use bulk SELECTs");
		assertEquals(3, domains.size());
		var names = domains.stream().map(Domain::getName).toList();
		assertTrue(names.indexOf("STATUS") < names.indexOf("CHOICE"));
		for (Domain domain : domains) {
			Domain restored = SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
			assertEquals(definitions.get(domain.getName()), DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText());
		}
	}

	@Test
	void missingFlexibleComponentsFailWithIdentity() {
		AtomicInteger calls = new AtomicInteger();
		var reader = new Oracle26aiDomainReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				if (calls.incrementAndGet() == 2) {
					try { h.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "CHOICE", "DOMAIN_KIND", "FLEXIBLE",
							"DOMAIN_DEFINITION", "CREATE FLEXIBLE DOMAIN APP.CHOICE (v) CHOOSE DOMAIN USING (kind NUMBER) FROM DECODE(kind, 1, APP.D(v))"))); }
					catch (SQLException e) { throw new RuntimeException(e); }
				}
				return null;
			}
		};
		var error = assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()));
		assertTrue(error.getMessage().contains("APP.CHOICE"));
	}

	@Test
	void missingDdlFailsWithIdentity() {
		AtomicInteger calls = new AtomicInteger();
		var reader = new Oracle26aiDomainReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				if (calls.incrementAndGet() == 2) {
					try { h.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "STATUS"))); }
					catch (SQLException e) { throw new RuntimeException(e); }
				}
				return null;
			}
		};
		var error = assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()));
		assertTrue(error.getMessage().contains("APP.STATUS"));
	}
}
