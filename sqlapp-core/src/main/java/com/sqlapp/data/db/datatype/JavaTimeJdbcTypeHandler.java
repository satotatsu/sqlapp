/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.db.datatype;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import com.sqlapp.data.converter.Converter;

/**
 * Reads and binds a {@code java.time} value through the JDBC 4.2 typed API.
 * <p>
 * Values are read with {@link ResultSet#getObject(int, Class)} and bound as the
 * {@code java.time} value itself, so the offset and fractional seconds of
 * {@code TIME WITH TIME ZONE} and {@code TIMESTAMP WITH TIME ZONE} survive the
 * JDBC round trip. Other inputs are converted with the supplied converter
 * before binding; a value without zone information receives the converter's
 * default zone, which is the JVM default time zone unless configured otherwise.
 * </p>
 * <p>
 * The driver must support {@code getObject(int, Class)} for the value class.
 * There is no per-row fallback; a dialect whose driver cannot do this replaces
 * the handler on its data type.
 * </p>
 *
 * @param <T> java.time value class
 */
public class JavaTimeJdbcTypeHandler<T> implements Serializable, JdbcTypeHandler {

	private static final long serialVersionUID = 1L;

	private final java.sql.JDBCType jdbcType;
	private final Class<T> valueClass;
	private final Converter<T> converter;

	/**
	 * @param jdbcType   JDBC type used for binding and NULL
	 * @param valueClass java.time class read from the driver
	 * @param converter  converter for values that are not already of the value
	 *                   class
	 */
	public JavaTimeJdbcTypeHandler(final java.sql.JDBCType jdbcType, final Class<T> valueClass,
			final Converter<T> converter) {
		this.jdbcType = Objects.requireNonNull(jdbcType, "jdbcType");
		this.valueClass = Objects.requireNonNull(valueClass, "valueClass");
		this.converter = Objects.requireNonNull(converter, "converter");
	}

	@Override
	public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
		return rs.getObject(columnIndex, valueClass);
	}

	@Override
	public Object getObject(final ResultSet rs, final String columnLabel) throws SQLException {
		return rs.getObject(columnLabel, valueClass);
	}

	@Override
	public void setObject(final PreparedStatement stmt, final int parameterIndex, final Object x)
			throws SQLException {
		final T value = x == null ? null : converter.convertObject(x);
		if (value == null) {
			stmt.setNull(parameterIndex, jdbcType.getVendorTypeNumber());
		} else {
			stmt.setObject(parameterIndex, value, jdbcType.getVendorTypeNumber());
		}
	}

	public java.sql.JDBCType getJdbcType() {
		return jdbcType;
	}

	public Class<T> getValueClass() {
		return valueClass;
	}

	public Converter<T> getConverter() {
		return converter;
	}
}
