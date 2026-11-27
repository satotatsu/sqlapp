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
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleDomainMetadataIntegrityTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return values.get(name);
			}

			@Override
			public boolean getBoolean(String name) {
				return Boolean.parseBoolean(values.get(name));
			}

			@Override
			public int getInt(String name) {
				return 0;
			}

			@Override
			public long getLong(String name) {
				return 0;
			}

			@Override
			public boolean wasNull() {
				return true;
			}
		};
	}

	private List<Domain> flexible(int returnedValues, int expectedValues, int expectedDiscriminants) {
		AtomicInteger calls = new AtomicInteger();
		var reader = new Oracle26aiDomainReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				try {
					int call = calls.incrementAndGet();
					if (call == 2)
						h.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "F", "DOMAIN_KIND", "FLEXIBLE",
								"DOMAIN_DEFINITION",
								"CREATE FLEXIBLE DOMAIN APP.F (V1,V2) CHOOSE DOMAIN USING (K NUMBER) FROM DECODE(K,1,APP.D(V1,V2))")));
					if (call == 4) {
						for (int i = 0; i <= returnedValues; i++)
							h.handleResultSetNext(row(Map.of("OWNER", "APP", "DOMAIN_NAME", "F", "COLUMN_NAME",
									i == 0 ? "K" : "V" + i, "DISCRIMINANT", Boolean.toString(i == 0),
									"EXPECTED_VALUE_COLUMNS", Integer.toString(expectedValues),
									"EXPECTED_DISCRIMINANTS", Integer.toString(expectedDiscriminants))));
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var result = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(4, calls.get());
		return result;
	}

	@Test
	void flexibleMetadataRequiresEveryValueAndDiscriminant() throws Exception {
		Domain domain = flexible(2, 2, 1).get(0);
		Domain restored = SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
		assertEquals(List.of("K", "V1", "V2"), restored.getColumns().stream().map(DomainColumn::getName).toList());
		assertTrue(assertThrows(IllegalStateException.class, () -> flexible(1, 2, 1)).getMessage().contains("APP.F"));
		assertTrue(assertThrows(IllegalStateException.class, () -> flexible(2, 2, 2)).getMessage().contains("APP.F"));
	}

	@Test
	void multilineColumnAnnotationRequiresAndPreservesCompleteDdl() throws Exception {
		String value = "First\nSecond";
		String ddl = "CREATE DOMAIN APP.D AS NUMBER ANNOTATIONS(Label '" + value + "')";
		AtomicInteger calls = new AtomicInteger();
		var reader = new Oracle26aiDomainReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				try {
					int call = calls.incrementAndGet();
					if (call == 1)
						h.handleResultSetNext(row(
								Map.of("OWNER", "APP", "DOMAIN_NAME", "D", "DATA_TYPE", "NUMBER", "NULLABLE", "Y")));
					if (call == 2)
						h.handleResultSetNext(row(Map.of("ANNOTATION_OWNER", "APP", "OBJECT_NAME", "D", "COLUMN_NAME",
								"D", "ANNOTATION_NAME", "Label", "ANNOTATION_VALUE", value)));
					if (call == 3) {
						assertEquals(List.of("APP"), p.get("definitionOwner"));
						h.handleResultSetNext(
								row(Map.of("OWNER", "APP", "DOMAIN_NAME", "D", "DOMAIN_DEFINITION", ddl)));
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		Domain restored = SchemaUtils.readXml(new java.io.StringReader(
				reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).get(0).asXml()));
		assertEquals(ddl, DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
				.get(0).getSqlText());
		assertEquals(4, calls.get());
	}

	@Test
	void annotationsRejectUnknownOrWrongCaseComponents() {
		Domain domain = new Domain("PAIR").setSchemaName("APP");
		domain.getColumns().add(new DomainColumn("ValuePart"));
		var reader = new Oracle23aiDomainReader(DialectHolder.oracle23aiDialect);
		for (String name : List.of("missing", "VALUEPART")) {
			assertTrue(assertThrows(IllegalStateException.class,
					() -> reader.setDomainAnnotation(domain,
							row(Map.of("COLUMN_NAME", name, "ANNOTATION_NAME", "Label", "ANNOTATION_VALUE", "text"))))
					.getMessage().contains("APP.PAIR"));
		}
	}
}
