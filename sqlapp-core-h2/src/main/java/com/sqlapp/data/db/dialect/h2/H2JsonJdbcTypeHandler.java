/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.sqlapp.data.converter.JsonStringConverter;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;

/**
 * H2 accepts UTF-8 JSON bytes without converting documents into JSON strings.
 */
public final class H2JsonJdbcTypeHandler implements JdbcTypeHandler {
	@Override
	public Object getObject(ResultSet rows, int index) throws SQLException {
		return rows.getString(index);
	}

	@Override
	public Object getObject(ResultSet rows, String label) throws SQLException {
		return rows.getString(label);
	}

	@Override
	public void setObject(PreparedStatement statement, int index, Object value) throws SQLException {
		byte[] bytes = value instanceof byte[] binary ? binary
				: value == null ? null
						: new JsonStringConverter().convertObject(value).getBytes(StandardCharsets.UTF_8);
		statement.setBytes(index, bytes);
	}
}
