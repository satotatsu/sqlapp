/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class PhoenixSequenceReaderTest {
	private ExResultSet row(Map<String, Object> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return (String) values.get(name);
			}

			@Override
			public Long getLongValue(String name) {
				return (Long) values.get(name);
			}

			@Override
			public Boolean getBooleanValue(String name) {
				return (Boolean) values.get(name);
			}
		};
	}

	@ParameterizedTest
	@CsvSource({ "3,0,sequences.sql", "3,1,sequences31.sql", "4,0,sequences.sql", "4,1,sequences31.sql",
			"5,0,sequences31.sql", "5,3,sequences31.sql", "6,0,sequences31.sql" })
	void selectsCatalogColumnsAtTheirVersionBoundary(int major, int minor, String expected) {
		assertEquals(expected, PhoenixSequenceReader
				.sqlResource(new ProductVersionInfo().setMajorVersion(major).setMinorVersion(minor)));
		assertEquals("sequences.sql", PhoenixSequenceReader.sqlResource(null));
	}

	@Test
	void readsManySequencesInOneQueryAndRecreatesConfigurationThroughXml() throws Exception {
		final int[] queries = { 0 };
		final var reader = new PhoenixSequenceReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				queries[0]++;
				try {
					for (int i = 0; i < 40; i++) {
						final var values = new HashMap<String, Object>();
						values.put("catalog_name", "tenant" + (i / 20));
						values.put("schema_name", "schema" + (i % 2));
						values.put("sequence_name", "seq" + (i % 20));
						values.put("start_with", 10L);
						values.put("increment_by", 2L);
						values.put("min_value", 1L);
						values.put("max_value", 1000L);
						values.put("cache_size", 20L);
						values.put("cycle_flag", true);
						h.handleResultSetNext(row(values));
					}
				} catch (java.sql.SQLException e) {
					throw new IllegalStateException(e);
				}
				return null;
			}
		};
		final var sequences = reader.doGetAll(null, new ParametersContext(),
				new ProductVersionInfo().setMajorVersion(5));
		assertEquals(1, queries[0]);
		assertEquals(40, sequences.size());
		for (var sequence : sequences) {
			final Sequence restored = SchemaUtils.readXml(new java.io.StringReader(sequence.asXml()));
			assertEquals(sequence.getCatalogName(), restored.getCatalogName());
			assertEquals(sequence.getSchemaName(), restored.getSchemaName());
			assertEquals(BigInteger.TEN, restored.getStartValue());
			assertEquals(BigInteger.TWO, restored.getIncrementBy());
			assertNull(restored.getLastValue());
			final String ddl = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(ddl.contains("START WITH 10 INCREMENT BY 2 MINVALUE 1 MAXVALUE 1000 CYCLE CACHE 20"), ddl);
		}
	}

	@Test
	void preservesUnknownNumericValuesAndHandlesCacheOverflowExplicitly() throws Exception {
		final var reader = new PhoenixSequenceReader(DialectHolder.defaultDialect);
		final var sequence = reader.createSequence(row(Map.of("sequence_name", "s")));
		assertNull(sequence.getStartValue());
		assertNull(sequence.getIncrementBy());
		assertNull(sequence.getMinValue());
		assertNull(sequence.getMaxValue());
		assertNull(sequence.getCacheSize());
		assertNull(sequence.getLastValue());
		assertThrows(ArithmeticException.class,
				() -> reader.createSequence(row(Map.of("sequence_name", "s", "cache_size", Long.MAX_VALUE))));
	}

	@Test
	void parsesQueriesWithFiltersAndUsesGlobalScopeByDefault() {
		final var reader = new PhoenixSequenceReader(DialectHolder.defaultDialect) {
			SqlNode query(String resource) {
				return getSqlNodeCache().getString(resource);
			}
		};
		for (String resource : List.of("sequences.sql", "sequences31.sql")) {
			final var node = reader.query(resource);
			final var defaults = new SqlParameterCollection();
			node.eval(new ParametersContext(), defaults);
			assertTrue(defaults.getSql().contains("TENANT_ID IS NULL"), defaults.getSql());
			final var context = new ParametersContext();
			context.put("catalogName", "tenant'1");
			context.put("schemaName", "app");
			context.put("sequenceName", "seq");
			final var filtered = new SqlParameterCollection();
			node.eval(context, filtered);
			assertTrue(filtered.getSql().contains("TENANT_ID IN"));
			assertFalse(filtered.getSql().contains("TENANT_ID IS NULL"));
			assertFalse(filtered.getSql().contains("tenant'1"));
			assertFalse(filtered.getSql().contains("CURRENT_VALUE"));
			if (resource.equals("sequences.sql")) {
				assertFalse(filtered.getSql().contains("MIN_VALUE AS"));
				assertTrue(filtered.getSql().contains("CAST(NULL AS BIGINT) AS min_value"));
			} else {
				assertTrue(filtered.getSql().contains("MIN_VALUE AS min_value"));
			}
		}
	}

	@Test
	void exposesDedicatedReadersFromBothDialects() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			assertInstanceOf(PhoenixCatalogReader.class, dialect.getCatalogReader());
			assertInstanceOf(PhoenixSchemaReader.class, dialect.getCatalogReader().getSchemaReader());
			assertInstanceOf(PhoenixSequenceReader.class,
					dialect.getCatalogReader().getSchemaReader().getSequenceReader());
		}
	}
}
