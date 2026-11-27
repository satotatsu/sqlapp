/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

import static com.sqlapp.data.db.dialect.db2.zos.Db2Zos13Test.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.db2.common.Db2HostIndexes;
import com.sqlapp.data.db.dialect.db2.common.Db2HostTables;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/** Table options, transparent archiving and BUSINESS_TIME WITHOUT OVERLAPS. */
class Db2Zos13TableOptionTest {

	@Test
	void readsAndRecreatesTableOptionsAndArchiving() throws Exception {
		var dialect = new Db2Zos13();
		var calls = new AtomicInteger();
		var tables = tables(dialect, calls, "normal");
		assertEquals(6, calls.get());
		var base = get(tables, "BASE");
		assertEquals("Y", base.getSpecifics().get(Db2HostTables.APPEND));
		assertEquals("Y", base.getSpecifics().get(Db2HostTables.VOLATILE));
		assertEquals("Y", base.getSpecifics().get(Db2HostTables.DATA_CAPTURE));
		assertEquals("CHANGES", base.getSpecifics().get(Db2HostTables.AUDIT));
		assertEquals("VPROG", base.getSpecifics().get(Db2HostTables.VALIDPROC));
		assertEquals("BASE_ARC", base.getSpecifics().get(Db2HostTables.ARCHIVE_TABLE));
		assertEquals("ARCHIVE", get(tables, "BASE_ARC").getSpecifics().get(Db2HostTables.TABLE_KIND));
		base = SchemaUtils.readXml(new java.io.StringReader(base.asXml()));
		String sql = sql(dialect, base);
		for (String expected : List.of("VALIDPROC VPROG", "AUDIT CHANGES", "DATA CAPTURE CHANGES", "APPEND YES",
				"VOLATILE", "ALTER TABLE \"APP\".\"BASE\" ENABLE ARCHIVE USE \"APP\".\"BASE_ARC\""))
			assertTrue(sql.contains(expected), expected + "\n" + sql);
		var plain = sql(dialect, get(tables, "BASE_ARC"));
		assertFalse(plain.contains("APPEND") || plain.contains("AUDIT") || plain.contains("ENABLE ARCHIVE"), plain);
		var ibmi = new com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect() {
			private static final long serialVersionUID = 1L;

			@Override
			public Platform getPlatform() {
				return Platform.IBM_I;
			}
		};
		var copy = base;
		assertThrows(IllegalArgumentException.class, () -> sql(ibmi, copy), "z/OS-only options fail on IBM i");
	}

	@Test
	void editProceduresFailInsteadOfBeingDropped() {
		assertThrows(RuntimeException.class, () -> tables(new Db2Zos13(), new AtomicInteger(), "editproc"));
	}

	@Test
	void readsAndRecreatesBusinessTimeWithoutOverlaps() throws Exception {
		var dialect = new Db2Zos13();
		var table = get(tables(dialect, new AtomicInteger(), "normal"), "POLICY");
		var pk = table.getConstraints().getPrimaryKeyConstraint();
		assertEquals(List.of("ID"), pk.getColumns().stream().map(c -> c.getName()).toList());
		assertEquals("Y", pk.getSpecifics().get(Db2HostIndexes.BUSINESS_TIME_WITHOUT_OVERLAPS));
		var index = table.getIndexes().get("UIX");
		assertEquals(List.of("CODE"), index.getColumns().stream().map(ReferenceColumn::getName).toList());
		table = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String sql = sql(dialect, table);
		assertTrue(sql.contains("CONSTRAINT \"PK\" PRIMARY KEY ( \"ID\", BUSINESS_TIME WITHOUT OVERLAPS)"), sql);
		assertTrue(sql.indexOf("PERIOD BUSINESS_TIME") < sql.indexOf("PRIMARY KEY"), sql);
		assertTrue(sql.contains(
				"CREATE UNIQUE INDEX \"APP\".\"UIX\" ON \"APP\".\"POLICY\" ( \"CODE\", BUSINESS_TIME WITHOUT OVERLAPS)"),
				sql);
		table.getTemporalPeriods().clear();
		var copy = table;
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, copy), "requires the BUSINESS_TIME period");
		assertThrows(RuntimeException.class, () -> tables(dialect, new AtomicInteger(), "inclusive"));
		assertThrows(RuntimeException.class, () -> tables(dialect, new AtomicInteger(), "trailing-column"));
	}

	@Test
	void schemaCreateEnablesArchivingAfterBothTables() throws Exception {
		var dialect = new Db2Zos13();
		var schemas = dialect.getCatalogReader().getSchemaReader();
		schemas.setSchemaName("APP");
		var schema = schemas.getAllFull(connection(new AtomicInteger(), "normal")).get(0);
		List<String> sql = dialect.createSqlFactoryRegistry().createSql(schema, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).toList();
		int archive = -1, enable = -1;
		for (int i = 0; i < sql.size(); i++) {
			if (sql.get(i).startsWith("CREATE TABLE \"APP\".\"BASE_ARC\""))
				archive = i;
			if (sql.get(i).contains("ENABLE ARCHIVE USE"))
				enable = i;
		}
		assertTrue(archive >= 0 && enable > archive, String.join("\n", sql));
	}

	private static Table get(List<Table> tables, String name) {
		return tables.stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
	}

	private static List<Table> tables(Db2Zos13 dialect, AtomicInteger calls, String mode) {
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("APP");
		return reader.getAllFull(connection(calls, mode));
	}

	private static String sql(com.sqlapp.data.db.dialect.Dialect dialect, Table table) {
		return dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).stream().map(op -> op.getSqlText())
				.reduce("", (a, b) -> a + "\n" + b);
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
			if (sql.contains("OBJECT_SCHEMA"))
				return rows(List.of(row("SCHEMA_NAME", "APP")));
			if (sql.contains("host:tables"))
				return rows(List.of(
						table("BASE", "T", "BASE_ARC", "Y", "Y", "Y", "C", "VPROG",
								mode.equals("editproc") ? "EDITPROC ENCRYPT" : ""),
						table("BASE_ARC", "R", "", "N", "N", " ", " ", "", ""),
						table("POLICY", "T", "", "N", "N", " ", " ", "", "")));
			if (sql.contains("KEY_TYPE"))
				return rows(List.of(key("ID", " "), key("BB", mode.equals("inclusive") ? "I" : "B"), key("BE", "C")));
			if (sql.contains("host:indexes")) {
				var keys = new ArrayList<Map<String, Object>>(
						List.of(index(1, "CODE", " "), index(2, "BB", "B"), index(3, "BE", "C")));
				if (mode.equals("trailing-column"))
					keys.set(2, index(3, "ID", " "));
				return rows(keys);
			}
			if (sql.contains("IDENTITY_MODE")) {
				var values = new ArrayList<Map<String, Object>>();
				for (var t : List.of("BASE", "BASE_ARC"))
					values.add(column(t, "ID", "INTEGER", 4, " "));
				values.add(column("POLICY", "ID", "INTEGER", 4, " "));
				values.add(column("POLICY", "CODE", "INTEGER", 4, " "));
				values.add(column("POLICY", "BB", "DATE", 4, "B"));
				values.add(column("POLICY", "BE", "DATE", 4, "C"));
				return rows(values);
			}
			return rows(List.of());
		});
	}

	private static Map<String, Object> table(String name, String kind, String archive, String append,
			String volatileFlag, String capture, String audit, String validproc, String unsupported) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", name, "REMARKS", null, "TABLE_KIND", kind, "TS_DATABASE", "DB1",
				"TS_NAME", "TS" + name.length(), "TS_IMPLICIT", "Y", "DB_IMPLICIT", "N", "ENCODING_SCHEME", "E",
				"PARTITION_KEY_COUNT", 0, "HISTORY_SCHEMA", "", "HISTORY_TABLE", "", "VERSIONING_EXTRA_ROW", "N",
				"ACCESS_CONTROL", " ", "ARCHIVE_SCHEMA", archive.isEmpty() ? "" : "APP", "ARCHIVE_TABLE", archive,
				"APPEND_FLAG", append, "VOLATILE_FLAG", volatileFlag, "DATA_CAPTURE", capture, "AUDIT_MODE", audit,
				"VALIDPROC", validproc, "PARTITION_NUMBER", null, "PARTITION_HIGH_VALUE", null, "UNSUPPORTED_TABLE",
				unsupported);
	}

	private static Map<String, Object> key(String column, String period) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", "POLICY", "CONSTRAINT_NAME", "PK", "KEY_TYPE", "P",
				"COLUMN_NAME", column, "KEY_PERIOD", period);
	}

	private static Map<String, Object> index(int position, String column, String period) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", "POLICY", "INDEX_SCHEMA", "APP", "INDEX_NAME", "UIX",
				"UNIQUE_FLAG", "Y", "UNIQUE_WHERE_NOT_NULL", "N", "KEY_COUNT", 3, "KEY_POSITION", position,
				"COLUMN_NAME", column, "COLUMN_ORDER", "A", "KEY_EXPRESSION", null, "KEY_PERIOD", period, "KEY_INCLUDE",
				"N", "CLUSTER_FLAG", "N", "PARTITIONED_FLAG", "N", "REMARKS", null, "UNSUPPORTED_INDEX", "");
	}

	private static Map<String, Object> column(String table, String name, String type, long length, String period) {
		return row("SCHEMA_NAME", "APP", "TABLE_NAME", table, "COLUMN_NAME", name, "TYPE_NAME", type, "TYPE_LENGTH",
				length, "TYPE_SCALE", 0, "NULLABLE", "N", "REMARKS", null, "UNSUPPORTED_GENERATION", "", "DEFAULT_SQL",
				null, "IDENTITY_MODE", "", "GENERATION_KIND", "", "PERIOD_ROLE", period, "HIDDEN_FLAG", "N",
				"PARTITION_KEY_SEQ", 0, "PARTITION_KEY_ORDER", " ", "CHARACTER_SUBTYPE", "");
	}
}
