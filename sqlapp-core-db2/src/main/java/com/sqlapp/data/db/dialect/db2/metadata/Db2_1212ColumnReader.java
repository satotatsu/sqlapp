/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.jdbc.ExResultSet;

public class Db2_1212ColumnReader extends Db2_1050ColumnReader {

	private static final Pattern VECTOR_TYPE =
			Pattern.compile("VECTOR\\s*\\(\\s*(FLOAT32|REAL|INT8)\\s*\\)", Pattern.CASE_INSENSITIVE);

	protected Db2_1212ColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Column createColumn(final ExResultSet rs) throws SQLException {
		final Column column = super.createColumn(rs);
		final Matcher matcher = VECTOR_TYPE.matcher(getString(rs, "TYPENAME"));
		if (matcher.matches()) {
			column.setDataType(DataType.VECTOR);
			column.setDataTypeName("VECTOR");
			column.setVectorElementDataType("INT8".equalsIgnoreCase(matcher.group(1))
					? DataType.TINYINT : DataType.REAL);
			column.setVectorDimension(getInteger(rs, "LENGTH"));
			column.setLength((Long) null);
			column.setScale(null);
		}
		return column;
	}
}
