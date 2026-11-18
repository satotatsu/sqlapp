/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

import com.sqlapp.data.schemas.Column;

/** Common interval projection/precision boundary; vendor behavior stays in its adapter. */
public interface IntervalTypeHandler extends JdbcTypeHandler {
	/** Whether this declaration can be represented by one JDBC parameter. */
	default boolean supportsJdbcBinding(Column column) { return true; }
	default boolean fixedArrayDimensions() { return true; }
	default String intervalQualifier(Column column) {
		return column.getDataTypeName() == null ? column.getDataType().name() : column.getDataTypeName();
	}
	IntervalConversion prepareInterval(Object value, Column destination);
	/** True when an exact value requires this adapter's JDBC binding rather than a text bulk loader. */
	default boolean requiresJdbcBinding(Object value, Column destination, java.sql.DatabaseMetaData server)
			throws java.sql.SQLException { return false; }
	/** Validates declarations even when the source has no non-null interval values. */
	default void validateIntervalColumn(Column destination) { intervalPrecision(destination); }
	/** Adds known server-version limits before target DDL or writes. */
	default IntervalConversion prepareInterval(Object value, Column destination, java.sql.DatabaseMetaData server)
			throws java.sql.SQLException {
		return prepareInterval(value, destination);
	}
	IntervalPrecision intervalPrecision(Column column);
	/** Maps semantic source precision onto a destination column; values must still be validated. */
	Column adaptIntervalColumn(Column destination, IntervalPrecision sourcePrecision);
}
