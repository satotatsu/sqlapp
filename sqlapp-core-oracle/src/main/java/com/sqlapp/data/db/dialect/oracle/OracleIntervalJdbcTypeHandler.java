/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle;

import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.db.dialect.oracle.metadata.OracleIntervalCollections;

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
	protected void validateArrayDimensions(int dimensions) {
		if (dimensions != 1) throw new IllegalArgumentException("Oracle interval arrays require a one-dimensional named SQL collection");
	}

	@Override public void validateIntervalColumn(Column column) {
		super.validateIntervalColumn(column);
		if (column.getArrayDimension() > 0) {
			OracleIntervalCollections.qualifiedName(column);
			String kind = column.getSpecifics().get(OracleIntervalCollections.KIND);
			if (!"TABLE".equals(kind) && !"VARYING ARRAY".equals(kind))
				throw new IllegalArgumentException("Resolve the Oracle TABLE or VARRAY collection metadata before use");
		}
	}
	@Override public Column adaptIntervalColumn(Column column, IntervalPrecision source) {
		if (column.getArrayDimension() > 0) { validateIntervalColumn(column); return column; }
		return super.adaptIntervalColumn(column, source);
	}
	@Override public IntervalConversion prepareInterval(Object value, Column column) {
		validateIntervalColumn(column);
		var converted = super.prepareInterval(value, column);
		if (column.getArrayDimension() == 0 || !"TABLE".equals(column.getSpecifics().get(OracleIntervalCollections.KIND))) return converted;
		Object[] values = (Object[]) converted.value();
		if (values != null) java.util.Arrays.sort(values, java.util.Comparator.nullsFirst(java.util.Comparator.comparing(Object::toString)));
		var warnings = new java.util.ArrayList<>(converted.warnings());
		warnings.add("INTERVAL_COLLECTION_UNORDERED");
		return new IntervalConversion(values, warnings);
	}
	@Override public IntervalConversion prepareInterval(Object value, Column column, java.sql.DatabaseMetaData server) throws SQLException {
		if (column.getArrayDimension() > 0) OracleIntervalCollections.require(server.getConnection(), column);
		return prepareInterval(value, column);
	}
	@Override public void setObject(PreparedStatement statement, int index, Object value, Column column) throws SQLException {
		if (column == null || column.getArrayDimension() == 0) { super.setObject(statement, index, value, column); return; }
		OracleIntervalCollections.require(statement.getConnection(), column);
		String name = OracleIntervalCollections.qualifiedName(column);
		if (value == null) { statement.setNull(index, java.sql.Types.ARRAY, name); return; }
		var scalar = column.clone().setArrayDimension(0);
		Object[] values = (Object[]) prepareInterval(value, column).value();
		Object[] nativeValues = new Object[values.length];
		for (int i=0; i<values.length; i++) if (values[i] != null)
			nativeValues[i] = createNativeValue(statement.getConnection().getClass().getClassLoader(),
					"oracle.sql." + (yearMonth ? "INTERVALYM" : "INTERVALDS"), codec(scalar).convert(values[i]).text());
		try {
			Class<?> driver = Class.forName("oracle.jdbc.OracleConnection");
			Object connection = statement.getConnection().unwrap(driver);
			var array = (java.sql.Array) driver.getMethod("createOracleArray", String.class, Object.class).invoke(connection, name, nativeValues);
			try { statement.setArray(index, array); } finally { array.free(); }
		} catch (InvocationTargetException failure) {
			if (failure.getCause() instanceof SQLException sql) throw sql;
			throw new SQLException("Cannot bind Oracle interval collection", failure.getCause());
		} catch (ReflectiveOperationException failure) { throw new SQLException("Oracle JDBC createOracleArray is required", failure); }
	}
	@Override public Object getObject(java.sql.ResultSet rows, int index, Column column) throws SQLException {
		if (column.getArrayDimension() == 0) return super.getObject(rows, index);
		validateIntervalColumn(column);
		var array = rows.getArray(index);
		if (array == null) return null;
		var values = new java.util.ArrayList<Object>();
		var codec = codec(column.clone().setArrayDimension(0));
		try (var elements = array.getResultSet()) {
			while (elements.next()) {
				String text = elements.getString(2);
				values.add(text == null ? null : codec.parse(text));
			}
		} finally { array.free(); }
		return prepareInterval(values.toArray(), column).value();
	}
	@Override public String literal(Object value, Column column) {
		if (column.getArrayDimension() == 0) return super.literal(value, column);
		validateIntervalColumn(column);
		String name = OracleIntervalCollections.qualifiedName(column);
		if (value == null) return "CAST(NULL AS " + name + ")";
		Object[] values = (Object[]) prepareInterval(value, column).value();
		var scalar = column.clone().setArrayDimension(0);
		return name + "(" + java.util.Arrays.stream(values).map(leaf -> leaf == null ? "NULL" : codec(scalar).literal(leaf))
				.collect(java.util.stream.Collectors.joining(",")) + ")";
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
