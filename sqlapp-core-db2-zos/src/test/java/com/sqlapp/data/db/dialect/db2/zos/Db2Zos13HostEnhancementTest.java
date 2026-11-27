/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

import static com.sqlapp.data.db.dialect.db2.zos.Db2Zos13Test.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.db.dialect.db2.common.Db2HostIndexes;
import com.sqlapp.data.db.dialect.db2.common.Db2HostTables;
import com.sqlapp.data.db.dialect.db2.zos.resolver.Db2Zos13DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/** Indexes, physical/temporal attributes, access control and versions. */
class Db2Zos13HostEnhancementTest {

	@Test
	void resolvesDb2TwelveAndThirteenOnly() {
		var resolver = new Db2Zos13DialectResolver();
		assertInstanceOf(Db2Zos12.class, resolver.getDialect("Db2 for z/OS", 12, 1));
		assertEquals(Db2Zos13.class, resolver.getDialect("Db2 for z/OS", 13, 1).getClass());
		assertThrows(IllegalArgumentException.class, () -> resolver.getDialect("Db2 for z/OS", 11, 1));
		assertThrows(IllegalArgumentException.class, () -> resolver.getDialect("Db2 for z/OS", 14, 1));
		assertInstanceOf(Db2Zos12.class, DialectResolver.getInstance().getDialect(metadata(12, "DSN12015")));
	}

	@Test
	void readsAndRecreatesExtendedIndexes() throws Exception {
		var dialect = new Db2Zos13();
		var table = readTable(dialect, "normal");
		var expression = table.getIndexes().get("IX_EXPR");
		assertEquals("UPPER(B)", expression.getColumns().get(0).getStatement().get(0));
		assertEquals(Order.Desc, expression.getColumns().get(0).getOrder());
		var include = table.getIndexes().get("IX_INC");
		assertEquals(List.of("A"), include.getColumns().stream().map(ReferenceColumn::getName).toList());
		assertEquals(List.of("B"), include.getIncludes().stream().map(ReferenceColumn::getName).toList());
		assertEquals("Y", include.getSpecifics().get(Db2HostIndexes.UNIQUE_WHERE_NOT_NULL));
		assertEquals("Y", include.getSpecifics().get(Db2HostIndexes.CLUSTER));
		assertEquals("Y", include.getSpecifics().get(Db2HostIndexes.PARTITIONED));
		table = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String sql = sql(dialect, table);
		assertTrue(sql.contains("CREATE INDEX \"APP\".\"IX_EXPR\" ON \"APP\".\"T\" (UPPER(B) DESC)"), sql);
		assertTrue(sql.contains(
				"CREATE UNIQUE WHERE NOT NULL INDEX \"APP\".\"IX_INC\" ON \"APP\".\"T\" ( \"A\") INCLUDE ( \"B\") PARTITIONED CLUSTER"),
				sql);
		var sparse = new Index("IX_W").setTableName("T");
		sparse.getColumns().add("A");
		sparse.setWhere("A > 0");
		table.getIndexes().add(sparse);
		var copy = table;
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, copy));
	}

	@Test
	void readsPhysicalTemporalAttributesInSixQueriesAndRecreates() throws Exception {
		var dialect = new Db2Zos13();
		var calls = new AtomicInteger();
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("APP");
		var tables = reader.getAllFull(connection(calls, "normal"));
		assertEquals(6, calls.get(), "partitions and temporal attributes add no query");
		var table = tables.stream().filter(t -> t.getName().equals("T")).findFirst().orElseThrow();
		assertEquals("TS1", table.getTableSpaceName());
		assertEquals("DB1", table.getSpecifics().get(Db2HostTables.DATABASE));
		assertEquals("UNICODE", table.getSpecifics().get(Db2HostTables.ENCODING_SCHEME));
		assertEquals(PartitioningType.Range, table.getPartitioning().getPartitioningType());
		assertEquals(List.of("1", "2"),
				table.getPartitioning().getPartitions().stream().map(Partition::getName).toList());
		assertEquals(2, table.getTemporalPeriods().size());
		assertEquals("HIST", table.getSystemVersioning().getHistoryTableName());
		assertEquals("TX", table.getSystemVersioning().getTransactionIdColumnName());
		assertTrue(
				table.getColumns().stream().noneMatch(
						c -> c.getSpecifics().keySet().stream().anyMatch(k -> k.startsWith("DB2_HOST_TMP_"))),
				"transient markers are removed");
		var history = tables.stream().filter(t -> t.getName().equals("HIST")).findFirst().orElseThrow();
		assertEquals("HISTORY", history.getSpecifics().get(Db2HostTables.TABLE_KIND));
		assertEquals("DB1", history.getSpecifics().get(Db2HostTables.DATABASE));
		assertNull(history.getTableSpaceName(), "implicit table space is not named");
		table = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String sql = sql(dialect, table);
		assertTrue(sql.contains("PARTITION BY RANGE ( \"A\")"), sql);
		assertTrue(sql.contains("PARTITION 1 ENDING AT (100) INCLUSIVE"), sql);
		assertTrue(sql.contains("PARTITION 2 ENDING AT (MAXVALUE) INCLUSIVE"), sql);
		assertTrue(sql.contains("IN \"DB1\".\"TS1\""), sql);
		assertTrue(sql.contains("CCSID UNICODE"), sql);
		assertTrue(sql.contains("PERIOD SYSTEM_TIME ( \"SB\", \"SE\")"), sql);
		assertTrue(sql.contains("PERIOD BUSINESS_TIME ( \"BB\", \"BE\")"), sql);
		assertTrue(sql.contains("\"SB\" TIMESTAMP(12) NOT NULL GENERATED ALWAYS AS ROW BEGIN"), sql);
		assertTrue(sql.contains("\"RC\" TIMESTAMP(6) NOT NULL"), sql);
		assertTrue(sql.contains("GENERATED ALWAYS AS ROW END"), sql);
		assertTrue(sql.contains("GENERATED ALWAYS AS TRANSACTION START ID"), sql);
		assertTrue(sql.contains("GENERATED BY DEFAULT FOR EACH ROW ON UPDATE AS ROW CHANGE TIMESTAMP"), sql);
		assertTrue(sql.contains("IMPLICITLY HIDDEN"), sql);
		assertTrue(sql.contains("FOR BIT DATA"), sql);
		assertTrue(sql.contains("FOR MIXED DATA"), sql);
		assertTrue(sql.contains(
				"ALTER TABLE \"APP\".\"T\" ADD VERSIONING USE HISTORY TABLE \"APP\".\"HIST\" ON DELETE ADD EXTRA ROW"),
				sql);
		assertTrue(sql(dialect, history).contains("IN DATABASE \"DB1\""), sql(dialect, history));
	}

	@Test
	void unrepresentableTemporalAndPhysicalShapesFail() throws Exception {
		var dialect = new Db2Zos13();
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		assertThrows(RuntimeException.class, () -> reader.getAllFull(connection(new AtomicInteger(), "overlaps")));
		assertThrows(RuntimeException.class, () -> reader.getAllFull(connection(new AtomicInteger(), "lonely-begin")));
		assertThrows(RuntimeException.class, () -> reader.getAllFull(connection(new AtomicInteger(), "bad-period")));
		var table = readTable(dialect, "normal");
		table.getTemporalPeriods().clear();
		table.setSystemVersioning(null);
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, table),
				"a row-begin column cannot silently become an ordinary column");
		var plain = new Table("P").setSchemaName("APP");
		plain.getColumns().add(new Column("A").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		plain.setTableSpaceName("TS");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, plain), "table space needs its database");
	}

	@Test
	void readsMasksPermissionsAndActivationAndCreatesThemInOrder() throws Exception {
		var dialect = new Db2Zos13();
		var calls = new AtomicInteger();
		var schemas = dialect.getCatalogReader().getSchemaReader();
		schemas.setSchemaName("APP");
		var schema = schemas.getAllFull(connection(calls, "normal")).get(0);
		var mask = schema.getMasks().get("M1");
		assertEquals("SALARY", mask.getColumnName());
		assertEquals("E", mask.getSpecifics().get("TABCORRELATION"));
		var snapshot = Db2RowPermissions.get(schema);
		assertEquals(1, snapshot.permissions().size());
		assertTrue(snapshot.tables().get(0).rowActive());
		assertTrue(snapshot.tables().get(0).columnActive());
		assertNull(schema.getTables().get("T").getSpecifics().get("DB2_HOST_ACCESS_CONTROL"));
		schema = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		List<String> sql = dialect.createSqlFactoryRegistry().createSql(schema, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).toList();
		int mask1 = index(sql, "CREATE MASK \"APP\".\"M1\" ON \"APP\".\"T\" AS \"E\" FOR COLUMN \"SALARY\" RETURN");
		int permission = index(sql, "CREATE PERMISSION \"APP\".\"P1\" ON \"APP\".\"T\"");
		int activate = index(sql, "ALTER TABLE \"APP\".\"T\" ACTIVATE ROW ACCESS CONTROL");
		int versioning = index(sql, "ADD VERSIONING USE HISTORY TABLE");
		int history = index(sql, "CREATE TABLE \"APP\".\"HIST\"");
		assertTrue(history < versioning, String.join("\n", sql));
		assertTrue(mask1 < activate && permission < activate, String.join("\n", sql));
		assertFalse(String.join("\n", sql).contains("OR REPLACE MASK"));
	}

	private static int index(List<String> sql, String text) {
		for (int i = 0; i < sql.size(); i++)
			if (sql.get(i).contains(text))
				return i;
		fail(text + " not found in\n" + String.join("\n", sql));
		return -1;
	}

	private static Table readTable(Db2Zos13 dialect, String mode) {
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("APP");
		return reader.getAllFull(connection(new AtomicInteger(), mode)).stream().filter(t -> t.getName().equals("T"))
				.findFirst().orElseThrow();
	}

	private static String sql(com.sqlapp.data.db.dialect.Dialect dialect, Table table) {
		return dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).stream().map(op -> op.getSqlText())
				.reduce("", (a, b) -> a + "\n" + b);
	}

	private static DatabaseMetaData metadata(int major, String version) {
		return proxy(DatabaseMetaData.class, (p, m, a) -> switch (m.getName()) {
		case "getDatabaseProductName" -> "DB2";
		case "getDatabaseProductVersion" -> version;
		case "getDatabaseMajorVersion" -> major;
		case "getDatabaseMinorVersion" -> 1;
		default -> fallback(m.getReturnType());
		});
	}

	private static Connection connection(AtomicInteger calls, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> Db2Zos13Test.metadata("DB2", "DSN13015");
		case "prepareStatement" -> statement((String) a[0], calls, mode);
		default -> fallback(m.getReturnType());
		});
	}

	private static PreparedStatement statement(String sql, AtomicInteger calls, String mode) {
		return proxy(PreparedStatement.class, (p, m, a) -> {
			if (!m.getName().equals("executeQuery"))
				return fallback(m.getReturnType());
			calls.incrementAndGet();
			if (sql.contains("OBJECT_SCHEMA"))
				return rows(List.of(row("SCHEMA_NAME", "APP")));
			if (sql.contains("host:tables"))
				return rows(List.of(table("T", "T", "TS1", "N", 1, "1", "100"),
						table("T", "T", "TS1", "N", 1, "2", "MAXVALUE"),
						table("HIST", "H", "TSH", "Y", 0, null, null)));
			if (sql.contains("host:indexes"))
				return rows(List.of(index("IX_EXPR", "N", "N", 1, 1, null, "D", "UPPER(B)", "N", "N", "N"),
						index("IX_INC", "Y", "Y", 2, 1, "A", "A", null, "N", "Y", "Y"),
						index("IX_INC", "Y", "Y", 2, 2, "B", "A", null, "Y", "Y", "Y")));
			if (sql.contains("KEY_TYPE"))
				return rows(mode.equals("overlaps")
						? List.of(row("SCHEMA_NAME", "APP", "TABLE_NAME", "T", "CONSTRAINT_NAME", "PK", "KEY_TYPE", "P",
								"COLUMN_NAME", "A", "UNSUPPORTED_KEY", "BUSINESS_TIME WITHOUT OVERLAPS"))
						: List.of());
			if (sql.contains("IDENTITY_MODE")) {
				var values = new ArrayList<Map<String, Object>>();
				for (var t : List.of("T", "HIST")) {
					boolean temporal = t.equals("T");
					values.add(column(t, "A", "INTEGER", 4, 0, "", temporal ? 1 : 0, "", "", ""));
					values.add(column(t, "B", "VARCHAR", 20, 0, "", 0, "", "", temporal ? "B" : ""));
					values.add(column(t, "M", "VARCHAR", 20, 0, "", 0, "", "", temporal ? "M" : ""));
					values.add(
							column(t, "SB", "TIMESTMP", 12, 12, temporal ? (mode.equals("bad-period") ? "Z" : "S") : "",
									0, temporal ? "ROW_BEGIN" : "", "", ""));
					values.add(column(t, "SE", "TIMESTMP", 12, 12, temporal && !mode.equals("lonely-begin") ? "T" : "",
							0, temporal ? "ROW_END" : "", "", ""));
					values.add(column(t, "TX", "TIMESTMP", 12, 12, "", 0, temporal ? "TRANSACTION_START_ID" : "", "Y",
							""));
					values.add(column(t, "BB", "DATE", 4, 0, temporal ? "B" : "", 0, "", "", ""));
					values.add(column(t, "BE", "DATE", 4, 0, temporal ? "C" : "", 0, "", "", ""));
					values.add(column(t, "RC", "TIMESTMP", 6, 6, "", 0, temporal ? "ROW_CHANGE_DEFAULT" : "", "", ""));
				}
				return rows(values);
			}
			if (sql.contains("host:masks"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "CONTROL_NAME", "M1", "TABLE_SCHEMA", "APP", "TABLE_NAME",
						"T", "CORRELATION_NAME", "E", "COLUMN_NAME", "SALARY", "ENABLE_FLAG", "Y", "VALID_FLAG", "Y",
						"RULETEXT", "CASE WHEN 1=1 THEN E.SALARY ELSE NULL END", "REMARKS", "masked")));
			if (sql.contains("host:permissions"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "CONTROL_NAME", "P1", "TABLE_SCHEMA", "APP", "TABLE_NAME",
						"T", "CORRELATION_NAME", "", "ENFORCED", "A", "ENABLE_FLAG", "Y", "VALID_FLAG", "Y", "RULETEXT",
						"A > 0", "REMARKS", null)));
			return rows(List.of());
		});
	}

	private static Map<String, Object> table(String name, String kind, String space, String implicit, int keys,
			String part, String limit) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", name, "REMARKS", null, "TABLE_KIND", kind, "TS_DATABASE", "DB1",
				"TS_NAME", space, "TS_IMPLICIT", implicit, "DB_IMPLICIT", "N", "ENCODING_SCHEME", "U",
				"PARTITION_KEY_COUNT", keys, "HISTORY_SCHEMA", kind.equals("T") ? "APP" : "", "HISTORY_TABLE",
				kind.equals("T") ? "HIST" : "", "VERSIONING_EXTRA_ROW", kind.equals("T") ? "Y" : "N", "ACCESS_CONTROL",
				kind.equals("T") ? "B" : " ", "PARTITION_NUMBER", part == null ? null : Integer.valueOf(part),
				"PARTITION_HIGH_VALUE", limit, "UNSUPPORTED_TABLE", "");
	}

	private static Map<String, Object> index(String name, String unique, String whereNotNull, int count, int position,
			String column, String order, String expression, String include, String cluster, String partitioned) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", "T", "INDEX_SCHEMA", "APP", "INDEX_NAME", name, "UNIQUE_FLAG",
				unique, "UNIQUE_WHERE_NOT_NULL", whereNotNull, "KEY_COUNT", count, "KEY_POSITION", position,
				"COLUMN_NAME", column, "COLUMN_ORDER", order, "KEY_EXPRESSION", expression, "KEY_INCLUDE", include,
				"CLUSTER_FLAG", cluster, "PARTITIONED_FLAG", partitioned, "REMARKS", null, "UNSUPPORTED_INDEX", "");
	}

	private static Map<String, Object> column(String table, String name, String type, long length, int scale,
			String period, int partitionKey, String generation, String hidden, String subtype) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", table, "COLUMN_NAME", name, "TYPE_NAME", type, "TYPE_LENGTH",
				length, "TYPE_SCALE", scale, "NULLABLE", "N", "REMARKS", null, "UNSUPPORTED_GENERATION", "",
				"DEFAULT_SQL", null, "IDENTITY_MODE", "", "IDENTITY_START", BigDecimal.ONE, "GENERATION_KIND",
				generation, "PERIOD_ROLE", period, "HIDDEN_FLAG", hidden.isEmpty() ? "N" : hidden, "PARTITION_KEY_SEQ",
				partitionKey, "PARTITION_KEY_ORDER", partitionKey > 0 ? "A" : " ", "CHARACTER_SUBTYPE", subtype);
	}
}
