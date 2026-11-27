/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class PostgresDomainNamedChecksTest {
	@Test
	void namesAndExpressionsSurviveOneBulkQueryAndXml() throws Exception {
		var dialect = DialectResolver.getInstance().getDialect("postgres", 18, 0);
		AtomicInteger count = new AtomicInteger();
		var reader = new PostgresDomainReader(dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				count.incrementAndGet();
				for (String name : List.of("lower", "upper")) {
					Map<String, String> values = Map.of("domain_name", "range", "domain_schema", "public", "typname",
							"integer", "oid", "100", "constraint_name", name, "consrc",
							name.equals("lower") ? "VALUE > 0" : "VALUE < 100");
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
				return null;
			}
		};
		var domains = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, count.get());
		assertEquals(1, domains.size());
		Domain restored = SchemaUtils.readXml(new java.io.StringReader(domains.get(0).asXml()));
		assertEquals(2, restored.getChecks().size());
		String sql = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("CONSTRAINT lower CHECK (VALUE > 0)"), sql);
		assertTrue(sql.contains("CONSTRAINT upper CHECK (VALUE < 100)"), sql);
		restored.getChecks().get(0).setEnable(false);
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
	}
}
