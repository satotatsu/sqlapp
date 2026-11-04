/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.db2.zos.Db2Zos13Test.*;
import java.sql.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class Db2Zos13CatalogTest {
	private static final boolean ZOS = true;

	@Test
	void readsViewTextColumnsAndCheckOptionAndRecreates() throws Exception {
		var calls = new AtomicInteger();
		var dialect = new Db2Zos13();
		var reader = dialect.getCatalogReader().getSchemaReader().getViewReader();
		reader.setSchemaName("APP");
		var view = (View) reader.getAllFull(connection(calls, "normal")).get(0);
		assertEquals(2, calls.get(), "views and their columns are fetched in bulk");
		assertEquals(List.of("A", "B"), view.getColumns().stream().map(Column::getName).toList());
		if (ZOS)
			assertTrue(String.join("", view.getDefinition()).contains("long-view-text"));
		else
			assertFalse(view.getStatement().isEmpty());
		view = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
		String sql = dialect.createSqlFactoryRegistry().createSql(view, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).reduce("", (a, b) -> a + "\n" + b);
		assertTrue(sql.contains("CREATE VIEW"), sql);
		assertTrue(sql.contains("WITH LOCAL CHECK OPTION"), sql);
		assertTrue(sql.contains("' trailing  '"), sql);
	}

	@Test
	void rejectsMissingOrTruncatedViewAndCheckText() {
		var schemas = new Db2Zos13().getCatalogReader().getSchemaReader();
		assertThrows(RuntimeException.class,
				() -> schemas.getViewReader().getAll(connection(new AtomicInteger(), "missing-view")));
		assertThrows(RuntimeException.class, () -> schemas.getTableReader().getCheckConstraintReader()
				.getAll(connection(new AtomicInteger(), "missing-check")));
	}

	@Test
	void sequenceRetainsExactBoundsAndRequiresAnHonestType() {
		var dialect = new Db2Zos13();
		var calls = new AtomicInteger();
		var sequence = dialect.getCatalogReader().getSchemaReader().getSequenceReader()
				.getAllFull(connection(calls, "normal")).get(0);
		assertEquals(1, calls.get());
		assertEquals(new BigInteger("9223372036854775808"), sequence.getStartValue());
		assertEquals(BigInteger.valueOf(-3), sequence.getIncrementBy());
		assertEquals(0, sequence.getCacheSize());
		if (ZOS) {
			assertNull(sequence.getDataType());
			assertEquals("opaque-type-id", sequence.getSpecifics().get("DB2_HOST_DATATYPE_ID"));
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE));
			sequence.setDataType(DataType.DECIMAL).setPrecision(31);
		}
		String sql = dialect.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("START WITH 9223372036854775808"), sql);
		assertTrue(sql.contains("INCREMENT BY -3"), sql);
		assertTrue(sql.contains("NO CACHE"), sql);
		assertTrue(sql.contains("NO ORDER"), sql);
		assertFalse(sql.contains("OR REPLACE"), sql);
	}

	@Test
	void readsOrdinaryIndexesAndChecksAndCreatesTheirDdl() throws Exception {
		var dialect = new Db2Zos13();
		var calls = new AtomicInteger();
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("APP");
		var table = reader.getAllFull(connection(calls, "normal")).get(0);
		assertEquals(6, calls.get());
		assertEquals(1, table.getIndexes().size());
		assertEquals("B", table.getIndexes().get(0).getColumns().get(0).getName());
		assertEquals(Order.Desc, table.getIndexes().get(0).getColumns().get(0).getOrder());
		assertEquals(1, table.getConstraints().getCheckConstraints().size());
		table = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String sql = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).reduce("", (a, b) -> a + "\n" + b);
		assertTrue(sql.contains("CHECK"), sql);
		assertTrue(sql.contains("CREATE INDEX \"IXOWNER\".\"IX\""), sql);
		assertTrue(sql.contains("DESC") || sql.contains("Desc"), sql);
	}

	@Test
	void rejectsUnsupportedIndexShapesAndCheckActions() {
		var reader = new Db2Zos13().getCatalogReader().getSchemaReader().getTableReader();
		assertThrows(RuntimeException.class,
				() -> reader.getAllFull(connection(new AtomicInteger(), "unsupported-index")));
		assertThrows(RuntimeException.class, () -> reader.getAllFull(connection(new AtomicInteger(), "missing-key")));
		assertThrows(RuntimeException.class,
				() -> reader.getCheckConstraintReader().getAll(connection(new AtomicInteger(), "unsupported-check")));
	}

	private static Connection connection(AtomicInteger calls, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> statement((String) a[0], calls, mode);
		default -> fallback(m.getReturnType());
		});
	}

	private static PreparedStatement statement(String sql, AtomicInteger calls, String mode) {
		return proxy(PreparedStatement.class, (p, m, a) -> {
			if (!m.getName().equals("executeQuery"))
				return fallback(m.getReturnType());
			calls.incrementAndGet();
			if (sql.contains("host:views")) {
				String query = "SELECT ' trailing  ' AS A, 2 AS B FROM SYSIBM.SYSDUMMY1";
				return rows(List.of(row("SCHEMA_NAME", "APP", "TABLE_NAME", "T", "VIEW_TEXT",
						mode.equals("missing-view") ? null
								: ZOS ? "CREATE VIEW APP.T AS " + query + " /*long-view-text " + "x".repeat(2000)
										+ "*/ WITH LOCAL CHECK OPTION" : query,
						"TEXT_KIND", ZOS ? "D" : "S", "CHECK_OPTION", "Y", "SQL_PATH", ZOS ? "SYSIBM,APP" : null,
						"REMARKS", null)));
			}
			if (sql.contains("host:sequences"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "SEQUENCE_NAME", "S", "SEQUENCE_TYPE",
						ZOS ? null : "DECIMAL", "TYPE_PRECISION", 31, "NATIVE_TYPE_ID", "opaque-type-id", "START_VALUE",
						new BigDecimal("9223372036854775808"), "INCREMENT_VALUE", new BigDecimal("-3"), "MINIMUM_VALUE",
						BigDecimal.ONE, "MAXIMUM_VALUE", new BigDecimal("9999999999999999999999999999999"),
						"CYCLE_FLAG", "N", "ORDER_FLAG", "N", "CACHE_SIZE", 0, "REMARKS", null)));
			if (sql.contains("host:indexes"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "TABLE_NAME", "T", "INDEX_SCHEMA", "IXOWNER",
						"INDEX_NAME", "IX", "KEY_COUNT", mode.equals("missing-key") ? 2 : 1, "KEY_POSITION", 1,
						"UNIQUE_FLAG", "N", "COLUMN_NAME", "B", "COLUMN_ORDER", "D", "REMARKS", null,
						"UNSUPPORTED_INDEX", mode.equals("unsupported-index") ? "EXPRESSION" : "")));
			if (sql.contains("host:checks"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "TABLE_NAME", "T", "CONSTRAINT_NAME", "IX", "CHECK_TEXT",
						mode.equals("missing-check") ? null : "A > 0", "ENFORCED", "Y", "UNSUPPORTED_CHECK",
						mode.equals("unsupported-check") ? "VIOLATION ACTION" : "")));
			if (sql.contains("KEY_TYPE") || sql.contains("REF_COLUMN_NAME"))
				return rows(List.of());
			if (sql.contains("TYPE_NAME"))
				return rows(List.of(column("A"), column("B")));
			return rows(List.of(row("SCHEMA_NAME", "APP", "TABLE_NAME", "T", "REMARKS", null)));
		});
	}

	private static Map<String, Object> column(String name) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", "T", "COLUMN_NAME", name, "TYPE_NAME", "INTEGER", "TYPE_LENGTH",
				10L, "TYPE_SCALE", 0, "NULLABLE", "N", "REMARKS", null, "UNSUPPORTED_GENERATION", "", "DEFAULT_SQL",
				null, "IDENTITY_MODE", "");
	}
}
