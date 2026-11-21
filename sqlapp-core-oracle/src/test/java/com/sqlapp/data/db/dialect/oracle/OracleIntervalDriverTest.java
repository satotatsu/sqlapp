/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

/**
 * Offline driver-object tests only; no external database or connection is
 * opened.
 */
@EnabledIf("driverAvailable")
class OracleIntervalDriverTest {
	static boolean driverAvailable() {
		try {
			Class.forName("oracle.sql.INTERVALDS");
			return true;
		} catch (ClassNotFoundException exception) {
			return false;
		}
	}

	@Test
	void realDriverObjectsPreserveMaximumLeadingFieldsAndNineFractionalDigits() throws Exception {
		var loader = getClass().getClassLoader();
		var connection = (Connection) Proxy.newProxyInstance(loader, new Class<?>[] { Connection.class },
				(proxy, method, args) -> null);
		for (var type : new DataType[] { DataType.INTERVAL_YEAR_TO_MONTH, DataType.INTERVAL_DAY_TO_SECOND }) {
			var handler = new OracleIntervalJdbcTypeHandler(type);
			var column = new Column("C").setDataType(type).setLength(9).setScale(9);
			for (var value : type == DataType.INTERVAL_YEAR_TO_MONTH
					? new IntervalValue[] { IntervalValue.parse("P999999999Y11M"),
							IntervalValue.parse("P-999999999Y-11M") }
					: new IntervalValue[] { IntervalValue.parse("P999999999DT23H59M59.123456789S"),
							IntervalValue.parse("P-999999999DT-23H-59M-59.123456789S"), IntervalValue.parse("P1DT-1H"),
							IntervalValue.parse("PT0.1S"), IntervalValue.parse("PT-0.000000001S"),
							IntervalValue.ZERO }) {
				var statement = (PreparedStatement) Proxy.newProxyInstance(loader,
						new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
							if (method.getName().equals("getConnection"))
								return connection;
							assertEquals("setObject", method.getName());
							assertEquals(type == DataType.INTERVAL_YEAR_TO_MONTH ? "oracle.sql.INTERVALYM"
									: "oracle.sql.INTERVALDS", args[1].getClass().getName());
							assertEquals(handler.convert(value, column).value(),
									handler.codec(column).parse(args[1].toString()));
							return null;
						});
				handler.setObject(statement, 1, value, column);
			}
		}
	}
}
