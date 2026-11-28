package com.sqlapp.data.db.dialect.mysql;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DefaultJdbcTypeHandler;

final class MySqlDatetimeJdbcTypeHandler extends DefaultJdbcTypeHandler {
	private static final long serialVersionUID = 1L;

	MySqlDatetimeJdbcTypeHandler() {
		super(DataType.TIMESTAMP);
	}

	@Override
	public Object getObject(final ResultSet rows, final int index) throws SQLException {
		return toTimestamp(rows.getObject(index, LocalDateTime.class));
	}

	@Override
	public Object getObject(final ResultSet rows, final String label) throws SQLException {
		return toTimestamp(rows.getObject(label, LocalDateTime.class));
	}

	private static Timestamp toTimestamp(final LocalDateTime value) throws SQLException {
		if (value == null) {
			return null;
		}
		final Timestamp timestamp = Timestamp.valueOf(value);
		if (!timestamp.toLocalDateTime().equals(value)) {
			throw new SQLDataException("DATETIME cannot be represented as a local JDBC Timestamp: " + value, "22007");
		}
		return timestamp;
	}

	@Override
	public void setObject(final PreparedStatement statement, final int index, final Object value) throws SQLException {
		if (value instanceof Timestamp timestamp) {
			// DATETIME stores wall-clock fields. A JDBC TIMESTAMP bind can shift them.
			statement.setString(index, timestamp.toString());
		} else {
			super.setObject(statement, index, value);
		}
	}
}
