/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DefaultJdbcTypeHandler;

/**
 * Reads tiny integers without Connector/J's lossy getObject Boolean conversion.
 */
final class MySqlTinyIntJdbcTypeHandler extends DefaultJdbcTypeHandler {
	private static final long serialVersionUID = 1L;
	private final boolean unsigned;

	MySqlTinyIntJdbcTypeHandler(final boolean unsigned) {
		super(unsigned ? DataType.UTINYINT : DataType.TINYINT);
		this.unsigned = unsigned;
	}

	@Override
	public Object getObject(final ResultSet rows, final int index) throws SQLException {
		final int value = rows.getInt(index);
		if (rows.wasNull())
			return null;
		return value(value);
	}

	@Override
	public Object getObject(final ResultSet rows, final String label) throws SQLException {
		final int value = rows.getInt(label);
		if (rows.wasNull())
			return null;
		return value(value);
	}

	private Object value(final int value) throws SQLException {
		if (value < (unsigned ? 0 : -128) || value > (unsigned ? 255 : 127))
			throw new SQLDataException("Value outside " + (unsigned ? "UTINYINT" : "TINYINT") + " range: " + value,
					"22003");
		if (unsigned)
			return Short.valueOf((short) value);
		return Byte.valueOf((byte) value);
	}
}
