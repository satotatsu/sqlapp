/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.jdbc.ExResultSet;

public class Oracle23aiColumnReader extends Oracle12cColumnReader {

	private static final Pattern VECTOR_INFO = Pattern.compile(
			"VECTOR\\s*\\(\\s*(\\*|\\d+)\\s*,\\s*(FLOAT32|FLOAT64|INT8|BINARY)\\s*\\)",
			Pattern.CASE_INSENSITIVE);

	protected Oracle23aiColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Column createColumn(final ExResultSet rs) throws SQLException {
		final Column column = super.createColumn(rs);
		if (column.getDataType() != DataType.VECTOR) {
			return column;
		}
		final String vectorInfo = getString(rs, "VECTOR_INFO");
		if (vectorInfo == null) {
			return column;
		}
		final Matcher matcher = VECTOR_INFO.matcher(vectorInfo);
		if (!matcher.find()) {
			return column;
		}
		if (!"*".equals(matcher.group(1))) {
			column.setVectorDimension(Integer.valueOf(matcher.group(1)));
		}
		column.setVectorElementDataType(toElementDataType(matcher.group(2)));
		return column;
	}

	private DataType toElementDataType(final String value) {
		if ("FLOAT32".equalsIgnoreCase(value)) {
			return DataType.REAL;
		}
		if ("FLOAT64".equalsIgnoreCase(value)) {
			return DataType.DOUBLE;
		}
		if ("INT8".equalsIgnoreCase(value)) {
			return DataType.TINYINT;
		}
		return DataType.BINARY;
	}
}
