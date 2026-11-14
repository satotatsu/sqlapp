/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

import static com.sqlapp.data.db.dialect.db2.i.Db2I75Test.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/**
 * IBM i global variables: CCSID, expression defaults and read-only rejection.
 */
class Db2I75VariableTest {

	@Test
	void readsVariablesAndRecreatesThem() throws Exception {
		var dialect = new Db2I75();
		var reader = dialect.getCatalogReader().getSchemaReader().getConstantReader();
		reader.setSchemaName("APP");
		var registry = dialect.createSqlFactoryRegistry();
		var sql = new ArrayList<String>();
		for (var v : reader.getAllFull(connection("normal"))) {
			Constant copy = SchemaUtils.readXml(new java.io.StringReader(v.asXml()));
			registry.createSql(copy, SqlType.CREATE).forEach(op -> sql.add(op.getSqlText()));
		}
		String all = String.join("\n", sql);
		assertTrue(sql.contains("CREATE VARIABLE \"APP\".\"V_NAME\" VARCHAR(20) CCSID 1208 DEFAULT (UPPER(USER))"),
				all);
		assertTrue(sql.contains("CREATE VARIABLE \"APP\".\"V_TS\" TIMESTAMP(3) DEFAULT (CURRENT TIMESTAMP)"), all);
		assertTrue(sql.contains("CREATE VARIABLE \"APP\".\"V_DEC\" DECIMAL(9, 2) DEFAULT NULL")
				|| sql.contains("CREATE VARIABLE \"APP\".\"V_DEC\" DECIMAL(9,2) DEFAULT NULL"), all);
		assertTrue(sql.contains("COMMENT ON VARIABLE \"APP\".\"V_DEC\" IS 'amount'"), all);
	}

	@Test
	void readOnlyVariablesFail() {
		var reader = new Db2I75().getCatalogReader().getSchemaReader().getConstantReader();
		assertThrows(RuntimeException.class, () -> reader.getAllFull(connection("readonly")));
	}

	private static Connection connection(String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2 UDB for AS/400", "QSQ07050");
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
			if (mode.equals("readonly"))
				return rows(List.of(var("V_RO", "INTEGER", 4L, 0, null, "1", "READONLY Y", null)));
			return rows(List.of(var("V_NAME", "VARCHAR", 20L, null, 1208, "UPPER(USER)", "", null),
					var("V_TS", "TIMESTMP", 3L, null, null, "CURRENT TIMESTAMP", "", null),
					var("V_DEC", "DECIMAL", 9L, 2, null, "NULL", "", "amount")));
		});
	}

	private static Map<String, Object> var(String name, String type, Long length, Integer scale, Integer ccsid,
			String defaultSql, String unsupported, String remarks) {
		return row("SCHEMA_NAME", "APP", "VARIABLE_NAME", name, "UDT_SCHEMA", null, "UDT_NAME", null, "TYPE_NAME", type,
				"TYPE_LENGTH", length, "TYPE_SCALE", scale, "VARIABLE_CCSID", ccsid, "DEFAULT_SQL", defaultSql,
				"UNSUPPORTED_VARIABLE", unsupported, "REMARKS", remarks);
	}
}
