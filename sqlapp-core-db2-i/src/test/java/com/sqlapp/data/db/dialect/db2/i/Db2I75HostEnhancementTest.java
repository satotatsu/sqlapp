/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

import static com.sqlapp.data.db.dialect.db2.i.Db2I75Test.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.db.dialect.db2.common.Db2HostIndexes;
import com.sqlapp.data.db.dialect.db2.common.Db2HostTables;
import com.sqlapp.data.db.dialect.db2.i.resolver.Db2I75DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/**
 * Indexes, system names, partitioning, temporal tables, CCSIDs, RCAC and
 * versions.
 */
class Db2I75HostEnhancementTest {

	@Test
	void resolvesSupportedReleasesAndBooleanAvailability() {
		var resolver = new Db2I75DialectResolver();
		assertInstanceOf(Db2I74.class, resolver.getDialect("Db2 for i", 7, 4));
		assertEquals(Db2I75.class, resolver.getDialect("Db2 for i", 7, 5).getClass());
		assertInstanceOf(Db2I76.class, resolver.getDialect("Db2 for i", 7, 6));
		assertThrows(IllegalArgumentException.class, () -> resolver.getDialect("Db2 for i", 7, 3));
		assertThrows(IllegalArgumentException.class, () -> resolver.getDialect("Db2 for i", 8, 1));
		assertInstanceOf(Db2I76.class, DialectResolver.getInstance().getDialect(metadata(7, 6, "QSQ07060")));
		assertNotEquals("BOOLEAN", new Db2I74().getDbDataTypes().getDbTypeStrict(DataType.BOOLEAN).getTypeName(),
				"native BOOLEAN is new in 7.5");
		assertEquals("BOOLEAN", new Db2I76().getDbDataTypes().getDbTypeStrict(DataType.BOOLEAN).getTypeName());
	}

	@Test
	void readsAndRecreatesExpressionSparseAndPartitionedIndexes() throws Exception {
		var dialect = new Db2I75();
		var table = read(dialect, "normal", "T");
		var expression = table.getIndexes().get("IX_EXPR");
		assertEquals("UPPER(\"B\")", expression.getColumns().get(0).getStatement().get(0));
		var sparse = table.getIndexes().get("IX_W");
		assertEquals("A > 0", sparse.getWhere());
		assertEquals("Y", sparse.getSpecifics().get(Db2HostIndexes.UNIQUE_WHERE_NOT_NULL));
		assertEquals("N", sparse.getSpecifics().get(Db2HostIndexes.PARTITIONED));
		table = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String sql = sql(dialect, table);
		assertTrue(sql.contains("CREATE INDEX \"APP\".\"IX_EXPR\" ON \"APP\".\"T\" (UPPER(\"B\"))"), sql);
		assertTrue(sql.contains(
				"CREATE UNIQUE WHERE NOT NULL INDEX \"APP\".\"IX_W\" ON \"APP\".\"T\" ( \"A\" DESC) WHERE A > 0 NOT PARTITIONED"),
				sql);
		table.getIndexes().get("IX_W").getSpecifics().put(Db2HostIndexes.CLUSTER, "Y");
		var copy = table;
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, copy), "CLUSTER is z/OS only");
		assertThrows(RuntimeException.class, () -> read(dialect, "bad-spanning", "T"));
	}

	@Test
	void readsSystemNameCcsidTemporalAndPartitioningInSixQueries() throws Exception {
		var dialect = new Db2I75();
		var calls = new AtomicInteger();
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("APP");
		var tables = reader.getAllFull(connection(calls, "normal"));
		assertEquals(6, calls.get());
		var table = tables.stream().filter(t -> t.getName().equals("T")).findFirst().orElseThrow();
		assertEquals("T0001", table.getSpecifics().get(Db2HostTables.SYSTEM_NAME));
		assertEquals("1208", table.getColumns().get("B").getSpecifics().get(Db2HostTables.CCSID));
		assertEquals("0", table.getColumns().get("BIN").getSpecifics().get("CODEPAGE"));
		assertEquals(PartitioningType.Range, table.getPartitioning().getPartitioningType());
		assertEquals(2, table.getPartitioning().getPartitions().size());
		assertEquals("HIST", table.getSystemVersioning().getHistoryTableName());
		assertEquals("APP", table.getSystemVersioning().getHistoryTableSchemaName());
		var hashed = tables.stream().filter(t -> t.getName().equals("H")).findFirst().orElseThrow();
		assertEquals(PartitioningType.Hash, hashed.getPartitioning().getPartitioningType());
		assertEquals(3, hashed.getPartitioning().getPartitionSize());
		table = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String sql = sql(dialect, table);
		assertTrue(sql.contains("CREATE TABLE \"APP\".\"T\" FOR SYSTEM NAME T0001"), sql);
		assertTrue(sql.contains("\"B\" VARCHAR(20) CCSID 1208"), sql);
		assertTrue(sql.contains("\"BIN\" CHAR(8) FOR BIT DATA"), sql);
		assertTrue(sql.contains("\"SB\" TIMESTAMP(12) NOT NULL GENERATED ALWAYS AS ROW BEGIN IMPLICITLY HIDDEN"), sql);
		assertTrue(sql.contains("GENERATED ALWAYS AS TRANSACTION START ID"), sql);
		assertTrue(sql.contains("GENERATED ALWAYS FOR EACH ROW ON UPDATE AS ROW CHANGE TIMESTAMP"), sql);
		assertTrue(sql.contains("PERIOD SYSTEM_TIME ( \"SB\", \"SE\")"), sql);
		assertTrue(sql.contains("PARTITION BY RANGE ( \"A\")"), sql);
		assertTrue(
				sql.contains("PARTITION \"PART000001\" STARTING FROM (MINVALUE) INCLUSIVE ENDING AT (100) EXCLUSIVE"),
				sql);
		assertTrue(sql.contains("ADD VERSIONING USE HISTORY TABLE \"APP\".\"HIST\""), sql);
		assertFalse(sql.contains("ON DELETE ADD EXTRA ROW"), sql);
		assertTrue(sql(dialect, hashed).contains("PARTITION BY HASH ( \"A\") INTO 3 PARTITIONS"), sql(dialect, hashed));
	}

	@Test
	void unresolvableCatalogStatesAndZosOnlyAttributesFail() {
		var dialect = new Db2I75();
		assertThrows(RuntimeException.class, () -> read(dialect, "versioning-defined", "T"));
		assertThrows(RuntimeException.class, () -> read(dialect, "bad-keys", "T"));
		var table = new Table("Z").setSchemaName("APP");
		table.getColumns().add(new Column("A").setDataType(DataType.INT));
		table.getSpecifics().put(Db2HostTables.DATABASE, "DB");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, table), "IN DATABASE is z/OS only");
		var mixed = new Table("M").setSchemaName("APP");
		mixed.getColumns().add(new Column("A").setDataType(DataType.VARCHAR).setLength(5));
		mixed.getColumns().get("A").getSpecifics().put(Db2HostTables.SUBTYPE, "M");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, mixed), "subtypes are z/OS only");
	}

	@Test
	void readsAndCreatesMasksAndPermissions() throws Exception {
		var dialect = new Db2I75();
		var schemas = dialect.getCatalogReader().getSchemaReader();
		schemas.setSchemaName("APP");
		var schema = schemas.getAllFull(connection(new AtomicInteger(), "normal")).get(0);
		assertEquals(1, schema.getMasks().size());
		var snapshot = Db2RowPermissions.get(schema);
		assertEquals("P1", snapshot.permissions().get(0).name());
		assertTrue(snapshot.tables().get(0).rowActive());
		assertFalse(snapshot.tables().get(0).columnActive());
		String sql = String.join("\n", dialect.createSqlFactoryRegistry().createSql(schema, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).toList());
		assertTrue(sql.contains("CREATE MASK \"APP\".\"M1\" ON \"APP\".\"T\" AS \"E\" FOR COLUMN \"B\" RETURN"), sql);
		assertTrue(sql.contains("COMMENT ON MASK \"APP\".\"M1\" IS 'mask'"), sql);
		assertTrue(sql.contains("ENFORCED FOR ALL ACCESS ENABLE"), sql);
		assertTrue(sql.indexOf("ACTIVATE ROW ACCESS CONTROL") > sql.indexOf("CREATE PERMISSION"), sql);
		assertFalse(sql.contains("ACTIVATE COLUMN ACCESS CONTROL"), sql);
	}

	private static final ThreadLocal<String> MODE = ThreadLocal.withInitial(() -> "E");
	private static final ThreadLocal<String> KEYS = ThreadLocal.withInitial(() -> "A");

	private static Table read(Db2I75 dialect, String mode, String name) {
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("APP");
		return reader.getAllFull(connection(new AtomicInteger(), mode)).stream().filter(t -> t.getName().equals(name))
				.findFirst().orElseThrow();
	}

	private static String sql(com.sqlapp.data.db.dialect.Dialect dialect, Table table) {
		return dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).stream().map(op -> op.getSqlText())
				.reduce("", (a, b) -> a + "\n" + b);
	}

	private static DatabaseMetaData metadata(int major, int minor, String version) {
		return proxy(DatabaseMetaData.class, (p, m, a) -> switch (m.getName()) {
		case "getDatabaseProductName" -> "DB2 UDB for AS/400";
		case "getDatabaseProductVersion" -> version;
		case "getDatabaseMajorVersion" -> major;
		case "getDatabaseMinorVersion" -> minor;
		default -> fallback(m.getReturnType());
		});
	}

	private static Connection connection(AtomicInteger calls, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata(7, 5, "QSQ07050");
		case "prepareStatement" -> statement((String) a[0], calls, mode);
		default -> fallback(m.getReturnType());
		});
	}

	private static PreparedStatement statement(String sql, AtomicInteger calls, String mode) {
		return proxy(PreparedStatement.class, (p, m, a) -> {
			if (!m.getName().equals("executeQuery"))
				return fallback(m.getReturnType());
			calls.incrementAndGet();
			if (sql.contains("SYSSCHEMAS"))
				return rows(List.of(row("SCHEMA_NAME", "APP")));
			MODE.set(mode.equals("versioning-defined") ? "D" : "E");
			KEYS.set(mode.equals("bad-keys") ? "NOPE" : "A");
			if (sql.contains("host:tables"))
				return rows(List.of(table("T", "R", "1", "MINVALUE", "Y", "100", "N"),
						table("T", "R", "2", "100", "Y", "MAXVALUE", "Y"), table("H", "H", "1", null, null, null, null),
						table("H", "H", "2", null, null, null, null), table("H", "H", "3", null, null, null, null),
						table("HIST", null, null, null, null, null, null)));
			if (sql.contains("host:indexes"))
				return rows(List.of(index("IX_EXPR", "N", "N", null, "A", "UPPER(\"B\")", null, null),
						index("IX_W", "Y", "Y", "A", "D", null, "A > 0", mode.equals("bad-spanning") ? "X" : "N")));
			if (sql.contains("IDENTITY_MODE")) {
				var values = new ArrayList<Map<String, Object>>();
				for (var t : List.of("T", "H", "HIST")) {
					boolean temporal = t.equals("T");
					values.add(column(t, "A", "INTEGER", 4, null, "", "N"));
					values.add(column(t, "B", "VARCHAR", 20, 1208, "", "N"));
					values.add(column(t, "BIN", "CHARACTER", 8, 65535, "", "N"));
					values.add(
							column(t, "SB", "TIMESTMP", 12, null, temporal ? "ROW_BEGIN" : "", temporal ? "Y" : "N"));
					values.add(column(t, "SE", "TIMESTMP", 12, null, temporal ? "ROW_END" : "", "N"));
					values.add(column(t, "TX", "TIMESTMP", 12, null, temporal ? "TRANSACTION_START_ID" : "", "N"));
					values.add(column(t, "RC", "TIMESTMP", 6, null, temporal ? "ROW_CHANGE_ALWAYS" : "", "N"));
				}
				return rows(values);
			}
			if (sql.contains("host:masks"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "CONTROL_NAME", "M1", "TABLE_SCHEMA", "APP", "TABLE_NAME",
						"T", "CORRELATION_NAME", "E", "COLUMN_NAME", "B", "ENABLE_FLAG", "N", "VALID_FLAG", "Y",
						"RULETEXT", "CASE WHEN VERIFY_GROUP_FOR_USER(SESSION_USER, 'HR') = 1 THEN E.B ELSE 'X' END",
						"REMARKS", "mask")));
			if (sql.contains("host:permissions"))
				return rows(List.of(row("SCHEMA_NAME", "APP", "CONTROL_NAME", "P1", "TABLE_SCHEMA", "APP", "TABLE_NAME",
						"T", "CORRELATION_NAME", "T", "ENFORCED", "A", "ENABLE_FLAG", "Y", "VALID_FLAG", "Y",
						"RULETEXT", "T.A > 0", "REMARKS", null)));
			return rows(List.of());
		});
	}

	private static Map<String, Object> table(String name, String type, String number, String low, String lowInclusive,
			String high, String highInclusive) {
		boolean temporal = name.equals("T");
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", name, "REMARKS", null, "SYSTEM_NAME", temporal ? "T0001" : name,
				"ACCESS_CONTROL", temporal ? "R" : " ", "PERIOD_NAME", temporal ? "SYSTEM_TIME" : null, "PERIOD_BEGIN",
				temporal ? "SB" : null, "PERIOD_END", temporal ? "SE" : null, "HISTORY_SCHEMA", temporal ? "APP" : null,
				"HISTORY_TABLE", temporal ? "HIST" : null, "VERSIONING_STATUS", temporal ? MODE.get() : null,
				"VERSIONING_EXTRA_ROW", "N", "PARTITION_TYPE", type, "PARTITION_KEYS", type == null ? null : KEYS.get(),
				"PARTITION_NAME", number == null ? null : "PART00000" + number, "PARTITION_NUMBER",
				number == null ? null : Integer.valueOf(number), "PARTITION_LOW_INCLUSIVE", lowInclusive,
				"PARTITION_LOW_VALUE", low, "PARTITION_HIGH_INCLUSIVE", highInclusive, "PARTITION_HIGH_VALUE", high,
				"UNSUPPORTED_TABLE", "");
	}

	private static Map<String, Object> index(String name, String unique, String whereNotNull, String column,
			String order, String expression, String where, String partitioned) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", "T", "INDEX_SCHEMA", "APP", "INDEX_NAME", name, "UNIQUE_FLAG",
				unique, "UNIQUE_WHERE_NOT_NULL", whereNotNull, "KEY_COUNT", 1, "KEY_POSITION", 1, "COLUMN_NAME", column,
				"COLUMN_ORDER", order, "KEY_EXPRESSION", expression, "KEY_INCLUDE", "N", "WHERE_TEXT", where,
				"PARTITIONED_FLAG", partitioned, "REMARKS", null, "UNSUPPORTED_INDEX", "");
	}

	private static Map<String, Object> column(String table, String name, String type, long length, Integer ccsid,
			String generation, String hidden) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", table, "COLUMN_NAME", name, "TYPE_NAME", type, "TYPE_LENGTH",
				length, "TYPE_SCALE", null, "NULLABLE", "N", "REMARKS", null, "UNSUPPORTED_GENERATION", "",
				"DEFAULT_SQL", null, "IDENTITY_MODE", "", "GENERATION_KIND", generation, "HIDDEN_FLAG", hidden,
				"COLUMN_CCSID", ccsid);
	}
}
