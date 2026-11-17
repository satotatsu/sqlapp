/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle;

import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.StandardIntervalJdbcTypeHandler;

/**
 * Uses the driver's exact INTERVALYM/INTERVALDS String constructor without
 * introducing a mandatory Oracle driver dependency into the dialect module.
 */
public final class OracleIntervalJdbcTypeHandler extends StandardIntervalJdbcTypeHandler {
	private static final long serialVersionUID = 1L;
	private final boolean yearMonth;

	public OracleIntervalJdbcTypeHandler(DataType type) {
		super(requireSupported(type), 0, 9);
		yearMonth = type == DataType.INTERVAL_YEAR_TO_MONTH;
	}

	private static DataType requireSupported(DataType type) {
		if (type != DataType.INTERVAL_YEAR_TO_MONTH && type != DataType.INTERVAL_DAY_TO_SECOND)
			throw new IllegalArgumentException("Oracle requires YEAR TO MONTH or DAY TO SECOND");
		return type;
	}

	@Override
	protected void bindText(PreparedStatement statement, int index, String text) throws SQLException {
		String className = "oracle.sql." + (yearMonth ? "INTERVALYM" : "INTERVALDS");
		Object nativeValue = createNativeValue(statement.getConnection().getClass().getClassLoader(), className, text);
		statement.setObject(index, nativeValue);
	}

	/** Package-visible for offline driver tests; no connection or database is needed. */
	static Object createNativeValue(ClassLoader loader, String className, String text) throws SQLException {
		try {
			Class<?> driverType;
			try {
				driverType = Class.forName(className, true, loader);
			} catch (ClassNotFoundException notVisibleFromWrapper) {
				driverType = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
			}
			return driverType.getConstructor(String.class).newInstance(text);
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof SQLException sql) throw sql;
			throw new SQLException("Oracle interval driver rejected validated text", cause);
		} catch (ReflectiveOperationException | LinkageError exception) {
			throw new SQLException("Oracle JDBC driver must expose " + className + "(String)", exception);
		}
	}

	@Override
	protected void bindNull(PreparedStatement statement, int index) throws SQLException {
		statement.setNull(index, yearMonth ? -103 : -104);
	}
}
