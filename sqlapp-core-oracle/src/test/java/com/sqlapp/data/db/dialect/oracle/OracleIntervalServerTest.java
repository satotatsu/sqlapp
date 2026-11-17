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
@EnabledIfSystemProperty(named = "sqlapp.interval.oracle.url",
		matches = "jdbc:oracle:thin:@//127[.]0[.]0[.]1:11521/FREEPDB1")
class OracleIntervalServerTest {
	@Test
	void realDriverAndServerPreserveYearDayBoundsNanosAndNull() throws Exception {
		String table = "I_RT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 15).toUpperCase(java.util.Locale.ROOT);
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
						year.setObject(insert, 1, month, yc); day.setObject(insert, 2, value, dc); insert.executeUpdate();
						year.setObject(insert, 1, null, yc); day.setObject(insert, 2, null, dc); insert.executeUpdate();
					}
					try (var rows = statement.executeQuery("SELECT * FROM " + table)) {
						assertTrue(rows.next()); assertEquals(month, year.getObject(rows, 1));
						assertEquals(day.convert(value, dc).value(), day.getObject(rows, 2));
						assertTrue(rows.next()); assertNull(year.getObject(rows, 1)); assertNull(day.getObject(rows, 2));
					}
				}
				try (var insert = connection.prepareStatement("INSERT INTO " + table + " VALUES(NULL,?)")) {
					assertThrows(ArithmeticException.class, () -> day.setObject(insert, 1,
							IntervalValue.parse("PT0.000000001S"), dc.clone().setScale(6)));
				}
			} finally { statement.execute("DROP TABLE " + table + " PURGE"); }
		}
	}
}
