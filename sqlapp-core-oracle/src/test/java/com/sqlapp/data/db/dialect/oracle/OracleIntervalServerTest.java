/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

/** Opt-in real-server test against the task-owned Oracle Free user only. */
@EnabledIfSystemProperty(named = "sqlapp.interval.oracle.url", matches = "jdbc:oracle:thin:@//127[.]0[.]0[.]1:11521/FREEPDB1")
class OracleIntervalServerTest {
	@Test
	void internalCollectionKeysAreOmittedButUserInvisibleUniqueColumnsRemain() throws Exception {
		String type = "I_KEY_"
				+ UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(java.util.Locale.ROOT);
		String table = type + "_T";
		try (var connection = DriverManager.getConnection(System.getProperty("sqlapp.interval.oracle.url"),
				"interval_test", "interval_test_2026"); var sql = connection.createStatement()) {
			sql.execute("CREATE TYPE " + type + " AS TABLE OF INTERVAL DAY(9) TO SECOND(9)");
			try {
				sql.execute("CREATE TABLE " + table + "(ID NUMBER PRIMARY KEY,U RAW(4) INVISIBLE UNIQUE,C " + type
						+ ") NESTED TABLE C STORE AS " + type + "_S");
				try {
					var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection);
					var tables = dialect.getCatalogReader().getSchemaReader().getTableReader();
					tables.setSchemaName("INTERVAL_TEST");
					tables.setObjectName(table);
					var captured = tables.getAllFull(connection).getFirst();
					assertEquals(java.util.Set.of("ID", "U", "C"), captured.getColumns().stream().map(Column::getName)
							.collect(java.util.stream.Collectors.toSet()));
					assertEquals("true", captured.getColumns().get("U").getSpecifics().get("oracle.column.invisible"));
					assertEquals(2, captured.getConstraints().getUniqueConstraints().size());
					assertEquals(java.util.Set.of("ID", "U"),
							captured.getConstraints().getUniqueConstraints().stream()
									.flatMap(key -> key.getColumns().stream()).map(column -> column.getName())
									.collect(java.util.stream.Collectors.toSet()));
					assertEquals(2, captured.getIndexes().size());
					assertEquals(java.util.Set.of("ID", "U"),
							captured.getIndexes().stream().flatMap(index -> index.getColumns().stream())
									.map(column -> column.getName()).collect(java.util.stream.Collectors.toSet()));
				} finally {
					sql.execute("DROP TABLE " + table + " PURGE");
				}
			} finally {
				sql.execute("DROP TYPE " + type);
			}
		}
	}

	@Test
	void namedCollectionsPreserveNanosNullsBoundsAndBagSemantics() throws Exception {
		try (var connection = DriverManager.getConnection(System.getProperty("sqlapp.interval.oracle.url"),
				"interval_test", "interval_test_2026"); var sql = connection.createStatement()) {
			for (boolean nested : new boolean[] { false, true }) {
				String name = "I_COL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
						.toUpperCase(java.util.Locale.ROOT);
				String table = name + "_T";
				sql.execute("CREATE TYPE " + name + " AS " + (nested ? "TABLE" : "VARRAY(8)")
						+ " OF INTERVAL DAY(9) TO SECOND(9)");
				try {
					var column = com.sqlapp.data.db.dialect.oracle.metadata.OracleIntervalCollections
							.column(connection, name).setName("C");
					var handler = new OracleIntervalJdbcTypeHandler(DataType.INTERVAL_DAY_TO_SECOND);
					Object[] input = { IntervalValue.parse("P999999999DT0.123456789S"), null,
							IntervalValue.parse("PT-0.000000001S"), IntervalValue.parse("PT-0.000000001S") };
					Object[] expected = (Object[]) handler.prepareInterval(input, column).value();
					assertEquals(nested, handler.prepareInterval(input, column).warnings()
							.contains("INTERVAL_COLLECTION_UNORDERED"));
					sql.execute("CREATE TABLE " + table + "(id INT,C " + name + ")"
							+ (nested ? " NESTED TABLE C STORE AS " + name + "_S" : ""));
					try {
						var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection);
						var reader = dialect.getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
						reader.setSchemaName("INTERVAL_TEST");
						reader.setObjectName(table);
						var captured = reader.getAll(connection).stream().filter(item -> item.getName().equals("C"))
								.findFirst().orElseThrow();
						assertEquals(1, captured.getArrayDimension());
						assertEquals(9L, captured.getLength());
						assertEquals(9, captured.getScale());
						var recreated = new com.sqlapp.data.schemas.Table(table + "_COPY");
						recreated.getColumns().add(captured.clone());
						for (var operation : dialect.createSqlFactoryRegistry().createSql(recreated,
								com.sqlapp.data.db.sql.SqlType.CREATE))
							sql.execute(operation.getSqlText());
						sql.execute("DROP TABLE " + table + "_COPY PURGE");
						try (var insert = connection.prepareStatement("INSERT INTO " + table + " VALUES(?,?)")) {
							for (int i = 1; i <= 3; i++) {
								insert.setInt(1, i);
								handler.setObject(insert, 2, i == 1 ? input : i == 2 ? new Object[0] : null, column);
								insert.addBatch();
							}
							insert.executeBatch();
						}
						sql.execute("INSERT INTO " + table + " VALUES(4," + handler.literal(input, column) + ")");
						try (var rows = sql.executeQuery("SELECT C FROM " + table + " ORDER BY id")) {
							assertTrue(rows.next());
							assertArrayEquals(expected, (Object[]) handler.getObject(rows, 1, column));
							assertTrue(rows.next());
							assertArrayEquals(new Object[0], (Object[]) handler.getObject(rows, 1, column));
							assertTrue(rows.next());
							assertNull(handler.getObject(rows, 1, column));
							assertTrue(rows.next());
							assertArrayEquals(expected, (Object[]) handler.getObject(rows, 1, column));
						}
						if (!nested)
							assertThrows(IllegalArgumentException.class,
									() -> handler.prepareInterval(new Object[9], column));
					} finally {
						sql.execute("DROP TABLE " + table + " PURGE");
					}
				} finally {
					sql.execute("DROP TYPE " + name);
				}
			}
		}
	}

	@Test
	void realDriverAndServerPreserveYearDayBoundsNanosAndNull() throws Exception {
		String table = "I_RT_"
				+ UUID.randomUUID().toString().replace("-", "").substring(0, 15).toUpperCase(java.util.Locale.ROOT);
		try (var connection = DriverManager.getConnection(System.getProperty("sqlapp.interval.oracle.url"),
				"interval_test", "interval_test_2026"); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE " + table + "(Y INTERVAL YEAR(9) TO MONTH,D INTERVAL DAY(9) TO SECOND(9))");
			try {
				var year = new OracleIntervalJdbcTypeHandler(DataType.INTERVAL_YEAR_TO_MONTH);
				var day = new OracleIntervalJdbcTypeHandler(DataType.INTERVAL_DAY_TO_SECOND);
				var yc = new Column("Y").setDataType(DataType.INTERVAL_YEAR_TO_MONTH).setLength(9);
				var dc = new Column("D").setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(9).setScale(9);
				for (var value : new IntervalValue[] { IntervalValue.parse("P999999999DT23H59M59.123456789S"),
						IntervalValue.parse("P-999999999DT-23H-59M-59.123456789S"),
						IntervalValue.parse("PT-0.000000001S"), IntervalValue.parse("P1DT-1H"), IntervalValue.ZERO }) {
					statement.executeUpdate("DELETE FROM " + table);
					var month = IntervalValue.parse("P-999999999Y-11M");
					try (var insert = connection.prepareStatement("INSERT INTO " + table + " VALUES(?,?)")) {
						year.setObject(insert, 1, month, yc);
						day.setObject(insert, 2, value, dc);
						insert.executeUpdate();
						year.setObject(insert, 1, null, yc);
						day.setObject(insert, 2, null, dc);
						insert.executeUpdate();
					}
					try (var rows = statement.executeQuery("SELECT * FROM " + table)) {
						assertTrue(rows.next());
						assertEquals(month, year.getObject(rows, 1));
						assertEquals(day.convert(value, dc).value(), day.getObject(rows, 2));
						assertTrue(rows.next());
						assertNull(year.getObject(rows, 1));
						assertNull(day.getObject(rows, 2));
					}
				}
				try (var insert = connection.prepareStatement("INSERT INTO " + table + " VALUES(NULL,?)")) {
					assertThrows(ArithmeticException.class, () -> day.setObject(insert, 1,
							IntervalValue.parse("PT0.000000001S"), dc.clone().setScale(6)));
				}
			} finally {
				statement.execute("DROP TABLE " + table + " PURGE");
			}
		}
	}
}
