/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleDomainNamedChecksTest {
	@Test
	void allChecksSurviveReadXmlAndCreateWithConstantQueryCount() throws Exception {
		AtomicInteger count = new AtomicInteger();
		var reader = new Oracle23aiDomainReader(DialectHolder.oracle23aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				if (count.incrementAndGet() != 1)
					return null;
				for (String owner : List.of("APP", "OTHER")) {
					for (String name : List.of("LOWER", "UPPER")) {
						Map<String, String> values = Map.ofEntries(Map.entry("OWNER", owner),
								Map.entry("DOMAIN_NAME", "RANGE"), Map.entry("DATA_TYPE", "NUMBER"),
								Map.entry("NULLABLE", "Y"), Map.entry("CONSTRAINT_NAME", name),
								Map.entry("SEARCH_CONDITION", name.equals("LOWER") ? "VALUE > 0" : "VALUE < 100"),
								Map.entry("CONSTRAINT_STATUS", "DISABLED"), Map.entry("VALIDATED", "NOT VALIDATED"),
								Map.entry("RELY", "RELY"), Map.entry("DEFERRABLE", "DEFERRABLE"),
								Map.entry("DEFERRED", "DEFERRED"));
						try {
							handler.handleResultSetNext(new ExResultSet(null) {
								@Override
								public String getString(String column) {
									return values.get(column);
								}

								@Override
								public long getLong(String column) {
									return 0;
								}

								@Override
								public int getInt(String column) {
									return 0;
								}

								@Override
								public boolean wasNull() {
									return true;
								}

								@Override
								public boolean getBoolean(String column) {
									return false;
								}
							});
						} catch (SQLException e) {
							throw new RuntimeException(e);
						}
					}
				}
				return null;
			}
		};
		var domains = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(2, count.get());
		assertEquals(2, domains.size());
		for (Domain domain : domains) {
			assertEquals(2, domain.getChecks().size());
			assertNull(domain.getCheck());
			Domain restored = SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
			String sql = DialectHolder.oracle23aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
					.get(0).getSqlText();
			assertTrue(sql.contains(" AS NUMBER CONSTRAINT"), sql);
			assertFalse(sql.contains("NUMBER(0,0)"), sql);
			assertTrue(
					sql.contains(
							"CONSTRAINT LOWER CHECK (VALUE > 0) DEFERRABLE INITIALLY DEFERRED RELY DISABLE NOVALIDATE"),
					sql);
			assertTrue(sql.contains("CONSTRAINT UPPER CHECK (VALUE < 100)"), sql);
		}
	}
}
