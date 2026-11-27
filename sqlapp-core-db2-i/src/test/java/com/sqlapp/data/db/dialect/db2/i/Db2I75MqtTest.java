/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

import static com.sqlapp.data.db.dialect.db2.i.Db2I75Test.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/**
 * IBM i MQTs are rebuilt from the stored query, the catalog options and column
 * names.
 */
class Db2I75MqtTest {

	@Test
	void buildsCompleteCreateFromQueryOptionsAndColumns() throws Exception {
		var dialect = new Db2I75();
		var reader = dialect.getCatalogReader().getSchemaReader().getMviewReader();
		reader.setSchemaName("APP");
		var mview = (Mview) reader.getAllFull(connection("normal")).get(0);
		String definition = String.join("\n", mview.getDefinition());
		assertEquals(
				"CREATE TABLE \"APP\".\"MQ\" (\"K\", \"TOTAL\") AS (SELECT K, SUM(V) FROM APP.T GROUP BY K)"
						+ " DATA INITIALLY DEFERRED REFRESH DEFERRED DISABLE QUERY OPTIMIZATION MAINTAINED BY USER",
				definition);
		assertEquals("CS", mview.getSpecifics().get("DB2_HOST_MQT_ISOLATION"));
		assertNull(mview.getSpecifics().get("DB2_HOST_TMP_MQT_QUERY"));
		mview = SchemaUtils.readXml(new java.io.StringReader(mview.asXml()));
		var sql = dialect.createSqlFactoryRegistry().createSql(mview, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).toList();
		assertTrue(sql.contains(definition), sql.toString());
	}

	@Test
	void unknownOptionsFail() {
		var reader = new Db2I75().getCatalogReader().getSchemaReader().getMviewReader();
		assertThrows(RuntimeException.class, () -> reader.getAllFull(connection("immediate")));
	}

	@Test
	void generatesSequenceNextValues() {
		String sql = new Db2I75().createSqlFactoryRegistry()
				.createSql(new Sequence("S").setSchemaName("APP"), SqlType.SEQUENCE_NEXT_VALUES).get(0).getSqlText();
		assertTrue(sql.contains("WITH SQLAPP_SEQ_N (N)"), sql);
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
			if (sql.contains("host:mviews"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "TABLE_NAME", "MQ", "REMARKS", null, "MQT_TEXT",
						"SELECT K, SUM(V) FROM APP.T GROUP BY K", "TEXT_KIND", "Q", "SQL_PATH", null, "REFRESH",
						mode.equals("immediate") ? "IMMEDIATE" : "DEFERRED", "ENABLE_FLAG", "NO", "MAINTENANCE", "USER",
						"ISOLATION", "CS")));
			if (sql.contains("IDENTITY_MODE"))
				return rows(List.of(column("K"), column("TOTAL")));
			return rows(List.of());
		});
	}

	private static Map<String, Object> column(String name) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", "MQ", "COLUMN_NAME", name, "TYPE_NAME", "INTEGER", "TYPE_LENGTH",
				4L, "TYPE_SCALE", 0, "NULLABLE", "Y", "REMARKS", null, "UNSUPPORTED_GENERATION", "", "DEFAULT_SQL",
				null, "IDENTITY_MODE", "");
	}
}
