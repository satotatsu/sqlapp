/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

import static com.sqlapp.data.db.dialect.db2.zos.Db2Zos13Test.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/** Materialized query tables and sequence NEXT VALUE generation. */
class Db2Zos13MqtTest {

	@Test
	void readsCompleteMqtStatementColumnsAndIndexesAndRecreates() throws Exception {
		var dialect = new Db2Zos13();
		var calls = new AtomicInteger();
		var reader = dialect.getCatalogReader().getSchemaReader().getMviewReader();
		reader.setSchemaName("APP");
		var mview = (Mview) reader.getAllFull(connection(calls, "normal")).get(0);
		assertEquals(3, calls.get(), "MQTs, columns and indexes are fetched in bulk");
		assertEquals(List.of("K", "TOTAL"), mview.getColumns().stream().map(Column::getName).toList());
		assertEquals(1, mview.getIndexes().size());
		mview = SchemaUtils.readXml(new java.io.StringReader(mview.asXml()));
		var sql = dialect.createSqlFactoryRegistry().createSql(mview, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).toList();
		assertTrue(sql.stream().anyMatch(s -> s.startsWith("SET CURRENT SCHEMA = 'APP'")), sql.toString());
		assertTrue(sql.stream().anyMatch(s -> s.startsWith("CREATE TABLE APP.MQ")), sql.toString());
		assertTrue(sql.stream().anyMatch(s -> s.startsWith("CREATE INDEX \"APP\".\"MQ_IX\"")), sql.toString());
		var drop = dialect.createSqlFactoryRegistry().createSql(mview, SqlType.DROP).get(0).getSqlText();
		assertTrue(drop.startsWith("DROP TABLE"), drop);
	}

	@Test
	void mqtTextThatIsNotACompleteCreateFails() {
		var reader = new Db2Zos13().getCatalogReader().getSchemaReader().getMviewReader();
		assertThrows(RuntimeException.class, () -> reader.getAllFull(connection(new AtomicInteger(), "alter")));
	}

	@Test
	void generatesSequenceNextValuesWithValidHostIdentifiers() {
		var sequence = new Sequence("S").setSchemaName("APP");
		String sql = new Db2Zos13().createSqlFactoryRegistry().createSql(sequence, SqlType.SEQUENCE_NEXT_VALUES).get(0)
				.getSqlText();
		assertTrue(sql.contains("FROM SYSIBM.SYSDUMMY1"), sql);
		assertTrue(sql.contains("SELECT NEXT VALUE FOR \"APP\".\"S\""), sql);
		assertFalse(sql.matches("(?s).*\\b_N\\b.*"), "identifiers cannot start with an underscore");
	}

	private static Connection connection(AtomicInteger calls, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", "DSN13015");
		case "prepareStatement" -> statement((String) a[0], calls, mode);
		default -> fallback(m.getReturnType());
		});
	}

	private static PreparedStatement statement(String sql, AtomicInteger calls, String mode) {
		return proxy(PreparedStatement.class, (p, m, a) -> {
			if (!m.getName().equals("executeQuery"))
				return fallback(m.getReturnType());
			calls.incrementAndGet();
			if (sql.contains("host:mviews"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "TABLE_NAME", "MQ", "REMARKS", null, "MQT_TEXT",
						mode.equals("alter") ? "ALTER TABLE APP.MQ ADD MATERIALIZED QUERY (SELECT 1 FROM T)"
								: "CREATE TABLE APP.MQ (K, TOTAL) AS (SELECT K, SUM(V) FROM APP.T GROUP BY K) "
										+ "DATA INITIALLY DEFERRED REFRESH DEFERRED MAINTAINED BY USER",
						"TEXT_KIND", "D", "SQL_PATH", "\"SYSIBM\",\"APP\"", "REFRESH", "D", "ENABLE_FLAG", "Y",
						"MAINTENANCE", "U", "ISOLATION", "S")));
			if (sql.contains("IDENTITY_MODE"))
				return rows(List.of(column("K"), column("TOTAL")));
			if (sql.contains("host:indexes"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "TABLE_NAME", "MQ", "INDEX_SCHEMA", "APP", "INDEX_NAME",
						"MQ_IX", "UNIQUE_FLAG", "N", "KEY_COUNT", 1, "KEY_POSITION", 1, "COLUMN_NAME", "K",
						"COLUMN_ORDER", "A", "REMARKS", null, "UNSUPPORTED_INDEX", "")));
			return rows(List.of());
		});
	}

	private static Map<String, Object> column(String name) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", "MQ", "COLUMN_NAME", name, "TYPE_NAME", "INTEGER", "TYPE_LENGTH",
				4L, "TYPE_SCALE", 0, "NULLABLE", "Y", "REMARKS", null, "UNSUPPORTED_GENERATION", "", "DEFAULT_SQL",
				null, "IDENTITY_MODE", "");
	}
}
