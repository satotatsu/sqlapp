/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

import static com.sqlapp.data.db.dialect.db2.zos.Db2Zos13Test.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/** Global variables: catalog reading, CREATE/DROP and comment-only ALTER. */
class Db2Zos13VariableTest {

	@Test
	void readsVariablesAndRecreatesThem() throws Exception {
		var dialect = new Db2Zos13();
		var reader = dialect.getCatalogReader().getSchemaReader().getConstantReader();
		reader.setSchemaName("APP");
		List<Constant> variables = new ArrayList<>();
		for (var v : reader.getAllFull(connection("normal")))
			variables.add(SchemaUtils.readXml(new java.io.StringReader(v.asXml())));
		assertEquals(4, variables.size());
		var registry = dialect.createSqlFactoryRegistry();
		var sql = new ArrayList<String>();
		for (var v : variables)
			registry.createSql(v, SqlType.CREATE).forEach(op -> sql.add(op.getSqlText()));
		String all = String.join("\n", sql);
		assertTrue(sql.contains("CREATE VARIABLE \"APP\".\"V_NAME\" VARCHAR(20)"), all);
		assertTrue(sql.contains("COMMENT ON VARIABLE \"APP\".\"V_NAME\" IS 'a''b'"), all);
		assertTrue(
				sql.stream()
						.anyMatch(s -> s.matches(
								"CREATE VARIABLE \"APP\"\\.\"V_TS\" TIMESTAMP\\(6\\)" + " DEFAULT CURRENT TIMESTAMP")),
				all);
		assertTrue(sql.contains("CREATE VARIABLE \"APP\".\"V_AMT\" \"APP\".\"MONEY\" DEFAULT 0"), all);
		assertTrue(
				sql.stream().anyMatch(
						s -> s.startsWith("CREATE VARIABLE \"APP\".\"V_BIN\" CHAR(8)") && s.contains("FOR BIT DATA")),
				all);
		assertEquals("DROP VARIABLE \"APP\".\"V_NAME\"",
				registry.createSql(variables.get(0), SqlType.DROP).get(0).getSqlText());
	}

	@Test
	void stringConstantDefaultsAndUnknownCodesFail() {
		var reader = new Db2Zos13().getCatalogReader().getSchemaReader().getConstantReader();
		var e = assertThrows(RuntimeException.class, () -> reader.getAllFull(connection("string")));
		assertTrue(String.valueOf(e.getMessage()).contains("STRING CONSTANT DEFAULT")
				|| String.valueOf(e.getCause()).contains("STRING CONSTANT DEFAULT"), e.toString());
	}

	@Test
	void alterIsCommentOnlyAndOtherChangesFail() {
		var registry = new Db2Zos13().createSqlFactoryRegistry();
		var before = variable();
		var after = variable();
		after.setRemarks("new");
		var factory = registry.getSqlFactory(after, SqlType.ALTER);
		var sql = factory.createDiffSql(before.diff(after)).stream().map(op -> op.getSqlText()).toList();
		assertEquals(List.of("COMMENT ON VARIABLE \"APP\".\"V\" IS 'new'"), sql);
		var changed = variable();
		changed.getSpecifics().put("DB2_HOST_VARIABLE_TYPE_LENGTH", "40");
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(before.diff(changed)));
	}

	@Test
	void variableMarkerIsRequired() {
		var registry = new Db2Zos13().createSqlFactoryRegistry();
		var plain = new Constant("C").setSchemaName("APP");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(plain, SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(plain, SqlType.DROP));
	}

	private static Constant variable() {
		var v = new Constant("V").setSchemaName("APP");
		v.getSpecifics().put("DB2_OBJECT_TYPE", "VARIABLE");
		v.getSpecifics().put("DB2_HOST_VARIABLE_TYPE_NAME", "VARCHAR");
		v.getSpecifics().put("DB2_HOST_VARIABLE_TYPE_LENGTH", "20");
		return v;
	}

	private static Connection connection(String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", "DSN13015");
		case "prepareStatement" -> statement((String) a[0], mode);
		default -> fallback(m.getReturnType());
		});
	}

	private static PreparedStatement statement(String sql, String mode) {
		return proxy(PreparedStatement.class, (p, m, a) -> {
			if (!m.getName().equals("executeQuery"))
				return fallback(m.getReturnType());
			if (!sql.contains("host:variables"))
				return rows(List.of());
			if (mode.equals("string"))
				return rows(List.of(var("V_S", "VARCHAR", 10L, 0, 1208, null, "STRING CONSTANT DEFAULT", null, null)));
			return rows(List.of(var("V_NAME", "VARCHAR", 20L, 0, 1208, null, "", null, null, "a'b"),
					var("V_TS", "TIMESTAMP", 6L, 6, 0, "CURRENT TIMESTAMP", "", null, null),
					var("V_AMT", null, 8L, 2, 0, "0", "", "APP", "MONEY"),
					var("V_BIN", "CHAR", 8L, 0, 65535, null, "", null, null)));
		});
	}

	private static Map<String, Object> var(String name, String type, Long length, Integer scale, Integer ccsid,
			String defaultSql, String unsupported, String udtSchema, String udtName) {
		return var(name, type, length, scale, ccsid, defaultSql, unsupported, udtSchema, udtName, null);
	}

	private static Map<String, Object> var(String name, String type, Long length, Integer scale, Integer ccsid,
			String defaultSql, String unsupported, String udtSchema, String udtName, String remarks) {
		var row = row("SCHEMA_NAME", "APP", "VARIABLE_NAME", name, "TYPE_NAME", type, "TYPE_LENGTH", length,
				"TYPE_SCALE", scale, "VARIABLE_CCSID", ccsid, "DEFAULT_SQL", defaultSql, "UNSUPPORTED_VARIABLE",
				unsupported, "UDT_SCHEMA", udtSchema, "UDT_NAME", udtName);
		row.put("REMARKS", remarks);
		row.put("CREATED_AT", Timestamp.valueOf("2026-01-02 03:04:05"));
		return row;
	}
}
