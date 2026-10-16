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

class OracleMultiColumnDomainTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			private boolean nullValue;

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
				nullValue = values.get(column) == null;
				return nullValue ? 0 : Long.parseLong(values.get(column));
			}

			@Override
			public int getInt(String column) {
				return (int) getLong(column);
			}

			@Override
			public boolean wasNull() {
				return nullValue;
			}

			@Override
			public boolean getBoolean(String column) {
				return Boolean.parseBoolean(values.get(column));
			}
		};
	}

	@Test
	void alternativeTypesRetainPrimaryComponentsForTableAssociations() throws Exception {
		AtomicInteger count = new AtomicInteger();
		String ddl = "CREATE DOMAIN APP.PAIR AS (X AS NUMBER, Y AS NUMBER)";
		var reader = new Oracle26aiDomainReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				try {
					switch (count.incrementAndGet()) {
					case 1 -> handler.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "PAIR",
							"DOMAIN_COLUMN_COUNT", "2", "ALTERNATIVE_TYPE_COUNT", "1")));
					case 2 -> {
						assertTrue(node.toString().contains("WHERE C.DATA_TYPE_ID=1"));
						for (String part : java.util.List.of("X", "Y"))
							handler.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "PAIR", "COLUMN_NAME",
									part, "DATA_TYPE_ID", "1", "DATA_TYPE", "NUMBER", "NULLABLE", "Y")));
					}
					case 4 -> handler.handleResultSetNext(
							row(Map.of("OWNER", "APP", "DOMAIN_NAME", "PAIR", "DOMAIN_DEFINITION", ddl)));
					default -> {
					}
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var domains = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(5, count.get());
		com.sqlapp.data.schemas.Schema schema = new com.sqlapp.data.schemas.Schema("APP");
		schema.getDomains().add(domains.get(0));
		var table = new com.sqlapp.data.schemas.Table("T");
		schema.getTables().add(table);
		for (String part : java.util.List.of("Y", "X"))
			table.getColumns().add(
					new com.sqlapp.data.schemas.Column("C" + part).setDomain(domains.get(0)).setDomainColumnName(part));
		com.sqlapp.data.schemas.Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertEquals(ddl, registry.createSql(restored.getDomains().get(0), SqlType.CREATE).get(0).getSqlText());
		String sql = registry.createSql(restored.getTables().get(0), SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(sql.contains("DOMAIN APP.PAIR( CX, CY)"), sql);
	}

	@Test
	void scalarCollationSurvivesReadXmlAndCreate() throws Exception {
		for (var dialect : java.util.List.of(DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			Oracle23aiDomainReader reader = dialect == DialectHolder.oracle23aiDialect
					? new Oracle23aiDomainReader(dialect)
					: new Oracle26aiDomainReader(dialect);
			Domain domain = reader.createDomain(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "LABEL", "DATA_TYPE",
					"VARCHAR2", "CHAR_LENGTH", "80", "COLLATION", "BINARY_CI", "NULLABLE", "Y")));
			Domain restored = SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
			assertEquals("BINARY_CI", restored.getCollation());
			String sql = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("COLLATE BINARY_CI"), sql);
		}
	}

	@Test
	void readsComponentsInOneBulkQueryAndRecreatesAfterXml() throws Exception {
		AtomicInteger count = new AtomicInteger();
		var reader = new Oracle26aiDomainReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				try {
					switch (count.incrementAndGet()) {
					case 1 -> handler.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "ADDRESS",
							"DOMAIN_COLUMN_COUNT", "2", "CONSTRAINT_NAME", "CK_ZIP", "SEARCH_CONDITION", "ZIP > 0")));
					case 2 -> {
						handler.handleResultSetNext(row(
								Map.of("OWNER", "APP", "DOMAIN_NAME", "ADDRESS", "COLUMN_NAME", "CITY", "DATA_TYPE_ID",
										"1", "DATA_TYPE", "VARCHAR2", "CHAR_LENGTH", "80", "NULLABLE", "Y")));
						handler.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "ADDRESS", "COLUMN_NAME",
								"ZIP", "DATA_TYPE_ID", "1", "DATA_TYPE", "NUMBER", "DATA_PRECISION", "5", "DATA_SCALE",
								"0", "NULLABLE", "N")));
					}
					case 3 ->
						handler.handleResultSetNext(row(Map.of("ANNOTATION_OWNER", "APP", "OBJECT_NAME", "ADDRESS",
								"COLUMN_NAME", "CITY", "ANNOTATION_NAME", "Label", "ANNOTATION_VALUE", "City name")));
					default -> {
					}
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var domains = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(5, count.get());
		Domain restored = SchemaUtils.readXml(new java.io.StringReader(domains.get(0).asXml()));
		assertEquals(2, restored.getColumns().size());
		assertFalse(restored.getSpecifics().containsKey("oracle.domain.expectedColumns"));
		String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
				.get(0).getSqlText();
		assertTrue(sql.contains("CITY AS VARCHAR2(80)"), sql);
		assertTrue(sql.contains("ANNOTATIONS"), sql);
		assertTrue(sql.contains("ZIP AS NUMBER(5,0) NOT NULL"), sql);
		assertTrue(sql.contains("CONSTRAINT CK_ZIP CHECK (ZIP > 0)"), sql);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle21cDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
	}

	@Test
	void oracle23aiReadsAllComponentsAndColumnAnnotations() throws Exception {
		AtomicInteger count = new AtomicInteger();
		var reader = new Oracle23aiDomainReader(DialectHolder.oracle23aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				try {
					switch (count.incrementAndGet()) {
					case 1 -> {
						for (String owner : java.util.List.of("App", "APP")) {
							handler.handleResultSetNext(
									row(Map.of("OWNER", owner, "DOMAIN_NAME", "PAIR", "DOMAIN_COLUMN_COUNT", "2")));
						}
					}
					case 2 -> {
						for (String owner : java.util.List.of("App", "APP")) {
							for (String column : java.util.List.of("X", "Y"))
								handler.handleResultSetNext(
										row(Map.of("OWNER", owner, "DOMAIN_NAME", "PAIR", "COLUMN_NAME", column,
												"DATA_TYPE_ID", "1", "DATA_TYPE", "NUMBER", "NULLABLE", "Y")));
						}
					}
					case 3 -> handler.handleResultSetNext(row(Map.of("ANNOTATION_OWNER", "App", "OBJECT_NAME", "PAIR",
							"COLUMN_NAME", "Y", "ANNOTATION_NAME", "Label", "ANNOTATION_VALUE", "Second")));
					default -> fail("Unexpected per-object query");
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var domains = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(3, count.get());
		assertEquals(2, domains.size());
		for (Domain domain : domains) {
			Domain restored = SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
			assertEquals(java.util.List.of("X", "Y"), restored.getColumns().stream().map(c -> c.getName()).toList());
			String sql = DialectHolder.oracle23aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
					.get(0).getSqlText();
			assertTrue(sql.contains("X AS NUMBER"), sql);
			assertTrue(sql.contains("Y AS NUMBER"), sql);
			assertEquals(restored.getSchemaName().equals("App"), sql.contains("ANNOTATIONS"), sql);
		}
	}

	@Test
	void incompleteComponentsFailRatherThanNarrowTheDomain() {
		var reader = new Oracle26aiDomainReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				return null;
			}
		};
		Domain domain = new Domain("D").setSchemaName("APP");
		domain.getSpecifics().put("oracle.domain.expectedColumns", 3);
		assertThrows(IllegalStateException.class,
				() -> reader.loadColumns(null, new ParametersContext(), java.util.List.of(domain)));
	}
}
