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
import com.sqlapp.data.db.dialect.oracle.sql.OracleAnnotationUtils;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleDomainAnnotationIdentityTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String column) {
				return values.get(column);
			}

			@Override
			public Object getObject(String column) {
				return values.get(column);
			}

			@Override
			public long getLong(String column) {
				return 0L;
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
		};
	}

	@Test
	void annotationsUseExactOwnerAndNameWithOneBulkQuery() {
		AtomicInteger count = new AtomicInteger();
		var reader = new Oracle23aiDomainReader(DialectHolder.oracle23aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				try {
					if (count.incrementAndGet() == 1) {
						for (String owner : List.of("APP", "App", "Other")) {
							handler.handleResultSetNext(row(Map.of("OWNER", owner, "DOMAIN_NAME", "MONEY", "DATA_TYPE",
									"NUMBER", "NULLABLE", "Y")));
							handler.handleResultSetNext(row(Map.of("OWNER", owner, "DOMAIN_NAME", "Money", "DATA_TYPE",
									"NUMBER", "NULLABLE", "Y")));
						}
					} else {
						handler.handleResultSetNext(row(Map.of("ANNOTATION_OWNER", "App", "OBJECT_NAME", "Money",
								"ANNOTATION_NAME", "purpose", "ANNOTATION_VALUE", "mixed-case")));
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var domains = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(2, count.get());
		assertEquals(6, domains.size());
		for (var domain : domains) {
			assertEquals(domain.getSchemaName().equals("App") && domain.getName().equals("Money")
					? Map.of("purpose", "mixed-case")
					: Map.of(), OracleAnnotationUtils.getAnnotations(domain));
		}
	}
}
