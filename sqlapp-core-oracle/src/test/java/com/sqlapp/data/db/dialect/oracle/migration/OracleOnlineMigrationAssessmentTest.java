/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Method;

class OracleOnlineMigrationAssessmentTest {
	private final OracleMigrationAssessmentProvider provider = new OracleMigrationAssessmentProvider();

	@Test
	void readsNlsAndColumnMetadataWithoutScanningByDefault() {
		final var executed = new ArrayList<String>();
		final var result = provider.assess(connection("Oracle", executed, false), List.of(schema()), "26ai",
				Method.LOGICAL_MIGRATION, "AL32UTF8", false);
		assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("oracle.charset.database-settings")
				&& f.reason().contains("JA16SJIS") && f.evidence().name().equals("DATABASE")));
		assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("oracle.charset.database-column")
				&& "T\"ABLE".equals(f.object().table()) && "COL".equals(f.object().name())));
		assertFalse(result.findings().stream().anyMatch(f -> f.object() != null && "OUTSIDE_SCOPE".equals(f.object().table())));
		assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("oracle.charset.data-scan-disabled")));
		assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("oracle.charset.online-coverage")
				&& f.reason().contains("selected tables=1") && f.reason().contains("character columns=1")
				&& f.reason().contains("scan candidates=1") && f.reason().contains("successful scans=0")));
		assertFalse(executed.stream().anyMatch(sql -> sql.contains("MAX(")));
	}

	@Test
	void scansByteColumnsAndReportsOnlyAggregates() {
		final var executed = new ArrayList<String>();
		final var result = provider.assess(connection("Oracle Database", executed, false), List.of(schema()), "26ai",
				Method.LOGICAL_MIGRATION, "AL32UTF8", true);
		final var finding = result.findings().stream().filter(f -> f.ruleId().equals("oracle.charset.data-overflow"))
				.findFirst().orElseThrow();
		assertTrue(finding.reason().contains("maximum converted bytes=31"));
		assertTrue(finding.reason().contains("overflow rows=2"));
		assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("oracle.charset.online-coverage")
				&& f.reason().contains("scan candidates=1") && f.reason().contains("successful scans=1")
				&& f.reason().contains("failed scans=0")));
		assertTrue(executed.stream().anyMatch(sql -> sql.contains("\"APP\".\"T\"\"ABLE\"")
				&& sql.contains("CONVERT(\"COL\", 'AL32UTF8', 'JA16SJIS')")));
		assertFalse(result.toString().contains("actual-value"));
	}

	@Test
	void recordsPerColumnScanFailureAndRejectsWrongDatabase() {
		final var result = provider.assess(connection("Oracle", new ArrayList<>(), true), List.of(schema()), "26ai",
				Method.LOGICAL_MIGRATION, "AL32UTF8", true);
		assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("oracle.charset.data-scan-failed")
				&& f.reason().contains("17002")));
		assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("oracle.charset.online-coverage")
				&& f.reason().contains("successful scans=0") && f.reason().contains("failed scans=1")));
		assertThrows(IllegalArgumentException.class, () -> provider.assess(connection("PostgreSQL", new ArrayList<>(), false),
				List.of(schema()), "26ai", Method.LOGICAL_MIGRATION, "AL32UTF8", false));
	}

	private Schema schema() {
		final var schema = new Schema("APP").setProductName("Oracle").setProductMajorVersion(10);
		schema.getTables().add(new Table("T\"ABLE"));
		return schema;
	}

	private Connection connection(final String product, final List<String> executed, final boolean failScan) {
		final DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (method, args) ->
				"getDatabaseProductName".equals(method) ? product : defaultValue(args.returnType()));
		return proxy(Connection.class, (method, args) -> {
			if ("getMetaData".equals(method)) return metadata;
			if ("prepareStatement".equals(method)) {
				final String sql = (String) args.values()[0];
				executed.add(sql);
				return statement(sql, failScan);
			}
			return defaultValue(args.returnType());
		});
	}

	private PreparedStatement statement(final String sql, final boolean failScan) {
		return proxy(PreparedStatement.class, (method, args) -> {
			if ("executeQuery".equals(method)) {
				if (sql.contains("MAX(") && failScan) throw new SQLException("hidden", "08006", 17002);
				if (sql.contains("nls_database_parameters")) return resultSet(List.of(
						row("PARAMETER", "NLS_CHARACTERSET", "VALUE", "JA16SJIS"),
						row("PARAMETER", "NLS_NCHAR_CHARACTERSET", "VALUE", "AL16UTF16")));
				if (sql.contains("all_tab_columns")) return resultSet(List.of(
						row("TABLE_NAME", "T\"ABLE", "COLUMN_NAME", "COL", "DATA_TYPE", "VARCHAR2",
								"CHAR_USED", "B", "CHAR_LENGTH", 20L, "DATA_LENGTH", 20L),
						row("TABLE_NAME", "OUTSIDE_SCOPE", "COLUMN_NAME", "SECRET", "DATA_TYPE", "VARCHAR2",
								"CHAR_USED", "B", "CHAR_LENGTH", 20L, "DATA_LENGTH", 20L)));
				return resultSet(List.of(row("1", 31L, "2", 2L)));
			}
			return defaultValue(args.returnType());
		});
	}

	private ResultSet resultSet(final List<Map<String, Object>> rows) {
		final int[] index = { -1 };
		final boolean[] wasNull = { false };
		return proxy(ResultSet.class, (method, args) -> {
			if ("next".equals(method)) return ++index[0] < rows.size();
			if ("wasNull".equals(method)) return wasNull[0];
			if ("getString".equals(method) || "getLong".equals(method)) {
				final Object key = args.values()[0];
				final Object value = rows.get(index[0]).get(key instanceof Integer ? String.valueOf(key) : key);
				wasNull[0] = value == null;
				return "getLong".equals(method) ? (value == null ? 0L : ((Number) value).longValue())
						: value == null ? null : String.valueOf(value);
			}
			return defaultValue(args.returnType());
		});
	}

	private Map<String, Object> row(final Object... values) {
		final var row = new LinkedHashMap<String, Object>();
		for (int i = 0; i < values.length; i += 2) row.put(String.valueOf(values[i]), values[i + 1]);
		if (row.containsKey("PARAMETER")) {
			row.put("1", row.get("PARAMETER")); row.put("2", row.get("VALUE"));
		}
		return row;
	}

	private interface Invocation { Object call(String method, Arguments arguments) throws Throwable; }
	private record Arguments(Object[] values, Class<?> returnType) { }
	@SuppressWarnings("unchecked")
	private <T> T proxy(final Class<T> type, final Invocation invocation) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(proxy, method, args) -> invocation.call(method.getName(), new Arguments(args == null ? new Object[0] : args,
						method.getReturnType())));
	}
	private Object defaultValue(final Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		return 0;
	}
}
