/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

class PhoenixDefaultFamilyReaderTest {
	private PhoenixTableReader reader() {
		final var reader = new PhoenixTableReader(DialectHolder.defaultDialect5_3_1);
		reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
		return reader;
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static class Jdbc {
		final PhoenixColumnReaderTest.Jdbc headers;
		final Connection connection;
		final List<String> parameters = new ArrayList<>();
		String sql;
		int queries;
		boolean resultClosed, statementClosed;
		SQLException error;

		Jdbc(List<Map<String, Object>> rows, List<Map<String, Object>> properties) {
			headers = new PhoenixColumnReaderTest.Jdbc(rows, "getTables");
			final int[] position = { -1 };
			final var result = proxy(ResultSet.class, (p, method, args) -> switch (method.getName()) {
			case "next" -> ++position[0] < properties.size();
			case "getString" -> properties.get(position[0]).get(args[0]);
			case "close" -> {
				resultClosed = true;
				yield null;
			}
			default -> throw new UnsupportedOperationException(method.getName());
			});
			final var statement = proxy(PreparedStatement.class, (p, method, args) -> switch (method.getName()) {
			case "setString" -> {
				parameters.add((String) args[1]);
				yield null;
			}
			case "executeQuery" -> {
				queries++;
				if (error != null)
					throw error;
				yield result;
			}
			case "close" -> {
				statementClosed = true;
				yield null;
			}
			default -> throw new UnsupportedOperationException(method.getName());
			});
			connection = proxy(Connection.class, (p, method, args) -> switch (method.getName()) {
			case "getMetaData" -> headers.connection.getMetaData();
			case "prepareStatement" -> {
				assertTrue(headers.closed);
				sql = (String) args[0];
				yield statement;
			}
			default -> throw new UnsupportedOperationException(method.getName());
			});
		}
	}

	private Map<String, Object> header(String tenant, String schema, String name) {
		final var row = new HashMap<String, Object>();
		row.put("TABLE_CAT", tenant);
		row.put("TABLE_SCHEM", schema);
		row.put("TABLE_NAME", name);
		return row;
	}

	private Map<String, Object> property(String tenant, String schema, String name, String family) {
		final var row = new HashMap<String, Object>();
		row.put("TENANT_ID", tenant);
		row.put("TABLE_SCHEM", schema);
		row.put("TABLE_NAME", name);
		row.put("DEFAULT_COLUMN_FAMILY", family);
		return row;
	}

	@Test
	void readsNullStorageOnlyOnFourThreeAndLaterWithoutAdditionalQueries() {
		for (int minor : new int[] { 1, 2, 3, 7 }) {
			final var values = property(null, null, "T", null);
			values.put("STORE_NULLS", "true");
			final var jdbc = new Jdbc(List.of(header(null, null, "T")), List.of(values));
			final var table = reader().doGetAll(jdbc.connection, new ParametersContext(),
					new ProductVersionInfo().setMajorVersion(4).setMinorVersion(minor)).get(0);
			assertEquals(minor >= 3, jdbc.sql.contains("STORE_NULLS"));
			assertEquals(minor >= 3 ? "true" : null, table.getSpecifics().get("phoenix.storeNulls"));
			assertEquals(1, jdbc.queries);
		}
	}

	@Test
	void readsFortyFamiliesInOneAdditionalQueryWithExactIdentityAndBoundNames() throws Exception {
		final var rows = new ArrayList<Map<String, Object>>();
		final var properties = new ArrayList<Map<String, Object>>();
		for (String tenant : Arrays.asList(null, "tenant'a"))
			for (String schema : Arrays.asList(null, "quoted schema"))
				for (int i = 0; i < 10; i++) {
					rows.add(header(tenant, schema, "table'" + i));
					properties.add(property(tenant, schema, "table'" + i, "f'" + i));
				}
		properties.add(property("other tenant", null, "table'0", "wrong"));
		final var jdbc = new Jdbc(rows, properties);
		final var reader = reader();
		final var tables = reader.doGetAll(jdbc.connection, new ParametersContext(),
				new ProductVersionInfo().setMajorVersion(5));
		assertEquals(40, tables.size());
		assertEquals(1, jdbc.headers.queries);
		assertEquals(1, jdbc.queries);
		assertTrue(jdbc.resultClosed && jdbc.statementClosed);
		assertFalse(jdbc.sql.contains("tenant'a"));
		assertFalse(jdbc.sql.contains("table'0"));
		assertTrue(jdbc.sql.contains("TENANT_ID IS NULL OR TENANT_ID IN (?)"));
		assertTrue(jdbc.sql.contains("TABLE_SCHEM IS NULL OR TABLE_SCHEM IN (?)"));
		assertEquals(12, jdbc.parameters.size());
		for (var table : tables) {
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			final String family = "f'" + table.getName().substring(table.getName().length() - 1);
			assertEquals(family, restored.getSpecifics().get("phoenix.defaultColumnFamily"));
			assertTrue(DialectHolder.defaultDialect5_3_1.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
					.get(0).getSqlText().contains("DEFAULT_COLUMN_FAMILY='" + family.replace("'", "''") + "'"));
		}
	}

	@Test
	void keepsOlderUnknownAndEmptyPathsFreeOfTheAdditionalCatalogQuery() {
		for (ProductVersionInfo version : Arrays.asList(null, new ProductVersionInfo(),
				new ProductVersionInfo().setMajorVersion(3).setMinorVersion(1),
				new ProductVersionInfo().setMajorVersion(4),
				new ProductVersionInfo().setMajorVersion(4).setMinorVersion(0))) {
			final var jdbc = new Jdbc(List.of(header(null, null, "T")), List.of());
			reader().doGetAll(jdbc.connection, new ParametersContext(), version);
			assertNull(jdbc.sql);
		}
		final var jdbc = new Jdbc(List.of(), List.of());
		assertTrue(
				reader().doGetAll(jdbc.connection, new ParametersContext(), new ProductVersionInfo().setMajorVersion(5))
						.isEmpty());
		assertNull(jdbc.sql);
	}

	@Test
	void distinguishesNullFamiliesFromMissingHeadersAndPropagatesFailures() {
		final var version = new ProductVersionInfo().setMajorVersion(4).setMinorVersion(1);
		final var reader = reader();
		final var knownNull = new Jdbc(List.of(header("", "", "T")), List.of(property(null, null, "T", null)));
		assertNull(reader.doGetAll(knownNull.connection, new ParametersContext(), version).get(0).getSpecifics()
				.get("phoenix.defaultColumnFamily"));
		for (var properties : List.of(List.<Map<String, Object>>of(), List.of(property(null, null, "T", " ")),
				List.of(property(null, null, "T", null), property(null, null, "T", "F")))) {
			final var jdbc = new Jdbc(List.of(header(null, null, "T")), properties);
			assertThrows(IllegalStateException.class,
					() -> reader.doGetAll(jdbc.connection, new ParametersContext(), version));
			assertTrue(jdbc.headers.closed && jdbc.resultClosed && jdbc.statementClosed);
		}
		final var jdbc = new Jdbc(List.of(header(null, null, "T")), List.of());
		jdbc.error = new SQLException("permission denied");
		final var failure = assertThrows(IllegalStateException.class,
				() -> reader.doGetAll(jdbc.connection, new ParametersContext(), version));
		assertSame(jdbc.error, failure.getCause());
		assertTrue(jdbc.headers.closed && jdbc.statementClosed);
	}

	@Test
	void preservesBooleanOptionsInTheSameQueryAndGatesTheNewerCatalogColumn() throws Exception {
		for (int minor : new int[] { 1, 7, 8, 9 }) {
			final var property = property(null, null, "T", "F");
			property.put("DISABLE_WAL", "false");
			property.put("APPEND_ONLY_SCHEMA", "true");
			final var jdbc = new Jdbc(List.of(header(null, null, "T")), List.of(property));
			final var table = reader().doGetAll(jdbc.connection, new ParametersContext(),
					new ProductVersionInfo().setMajorVersion(4).setMinorVersion(minor)).get(0);
			assertEquals(1, jdbc.queries);
			assertEquals("false", table.getSpecifics().get("phoenix.disableWal"));
			assertEquals(minor >= 8, jdbc.sql.contains("APPEND_ONLY_SCHEMA"));
			assertEquals(minor >= 8 ? "true" : null, table.getSpecifics().get("phoenix.appendOnlySchema"));
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			final String sql = DialectHolder.defaultDialect5_3_1.createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("DISABLE_WAL=FALSE"));
			assertEquals(minor >= 8, sql.contains("APPEND_ONLY_SCHEMA=TRUE"));
		}
	}

	@Test
	void rejectsInvalidBooleanCatalogValuesAndClosesTheWholeResult() {
		for (String column : List.of("DISABLE_WAL", "APPEND_ONLY_SCHEMA")) {
			final var property = property(null, null, "T", "F");
			property.put(column, "invalid");
			final var jdbc = new Jdbc(List.of(header(null, null, "T")), List.of(property));
			assertThrows(IllegalStateException.class, () -> reader().doGetAll(jdbc.connection, new ParametersContext(),
					new ProductVersionInfo().setMajorVersion(5)));
			assertTrue(jdbc.headers.closed && jdbc.resultClosed && jdbc.statementClosed);
		}
	}

	@Test
	void readsTuningColumnsOnlyAtTheirVersionBoundariesInTheSameQuery() throws Exception {
		for (int minor : new int[] { 1, 6, 7, 8, 9 }) {
			final var property = property(null, null, "T", "F");
			property.put("UPDATE_CACHE_FREQUENCY", "300000");
			property.put("GUIDE_POSTS_WIDTH", "10000000");
			final var jdbc = new Jdbc(List.of(header(null, null, "T")), List.of(property));
			final var table = reader().doGetAll(jdbc.connection, new ParametersContext(),
					new ProductVersionInfo().setMajorVersion(4).setMinorVersion(minor)).get(0);
			assertEquals(1, jdbc.queries);
			assertEquals(minor >= 7, jdbc.sql.contains("UPDATE_CACHE_FREQUENCY"));
			assertEquals(minor >= 9, jdbc.sql.contains("GUIDE_POSTS_WIDTH"));
			assertEquals(minor >= 7 ? "300000" : null, table.getSpecifics().get("phoenix.updateCacheFrequency"));
			assertEquals(minor >= 9 ? "10000000" : null, table.getSpecifics().get("phoenix.guidePostsWidth"));
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			final var sql = DialectHolder.defaultDialect5_3_1.createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertEquals(minor >= 7, sql.contains("UPDATE_CACHE_FREQUENCY=300000"));
			assertEquals(minor >= 9, sql.contains("GUIDE_POSTS_WIDTH=10000000"));
		}
	}

	@Test
	void preservesObservedGuidepostNullAndRejectsBadTuningMetadata() {
		final var jdbc = new Jdbc(List.of(header(null, null, "T")), List.of(property(null, null, "T", null)));
		final var table = reader()
				.doGetAll(jdbc.connection, new ParametersContext(), new ProductVersionInfo().setMajorVersion(5)).get(0);
		assertEquals("NULL", table.getSpecifics().get("phoenix.guidePostsWidth"));
		assertTrue(DialectHolder.defaultDialect5_3_1.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0)
				.getSqlText().contains("GUIDE_POSTS_WIDTH=NULL"));
		for (String name : List.of("UPDATE_CACHE_FREQUENCY", "GUIDE_POSTS_WIDTH")) {
			final var row = property(null, null, "T", null);
			row.put(name, "-1");
			final var invalid = new Jdbc(List.of(header(null, null, "T")), List.of(row));
			assertThrows(IllegalArgumentException.class, () -> reader().doGetAll(invalid.connection,
					new ParametersContext(), new ProductVersionInfo().setMajorVersion(5)));
			assertTrue(invalid.resultClosed && invalid.statementClosed);
		}
	}
}
