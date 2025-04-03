package com.sqlapp.jdbc.sql;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.sql.GeneratedKeyHandler.GeneratedKeyInfo;

public class JdbcHandlerUtils {

	/**
	 * バインド変数の設定
	 * 
	 * @param statement
	 * @param sqlParameters
	 * @throws SQLException
	 */
	public static List<BindParameter> setBind(final PreparedStatement statement, final Dialect dialect,
			final SqlParameterCollection sqlParameters) throws SQLException {
		final List<BindParameter> list = sqlParameters.getBindParameters();
		final int size = list.size();
		for (int i = 0; i < size; i++) {
			final BindParameter bindParameter = list.get(i);
			setParameters(statement, dialect, bindParameter, i + 1);
		}
		return list;
	}

	/**
	 * PreparedStatementに値を設定します
	 * 
	 * @param statement     PreparedStatement
	 * @param dialect       Dialect
	 * @param bindParameter BindParameter
	 * @param index
	 * @throws SQLException
	 */
	public static void setParameters(final PreparedStatement statement, final Dialect dialect,
			final BindParameter bindParameter, final int index) throws SQLException {
		final DataType type = bindParameter.getType();
		final Object value = bindParameter.getValue();
		if (dialect != null && bindParameter.getType() != null) {
			final DbDataType<?> dbDataType = dialect.getDbDataTypes().getDbType(type);
			dbDataType.getJdbcTypeHandler().setObject(statement, index, value);
		} else {
			if (value instanceof String) {
				if (dialect != null && dialect.recommendsNTypeChar()) {
					statement.setNString(index, (String) value);
				} else {
					statement.setString(index, (String) value);
				}
			} else if (value instanceof Number) {
				if (value instanceof Integer) {
					statement.setInt(index, (Integer) value);
				} else if (value instanceof Long) {
					statement.setLong(index, (Long) value);
				} else if (value instanceof BigDecimal) {
					statement.setBigDecimal(index, (BigDecimal) value);
				} else if (value instanceof Byte) {
					statement.setByte(index, (Byte) value);
				} else if (value instanceof Float) {
					statement.setFloat(index, (Float) value);
				} else if (value instanceof Double) {
					statement.setDouble(index, (Double) value);
				} else {
					statement.setBigDecimal(index, Converters.getDefault().convertObject(value, BigDecimal.class));
				}
			} else if (value instanceof Boolean) {
				statement.setBoolean(index, (Boolean) value);
			} else if (value instanceof byte[]) {
				statement.setBytes(index, (byte[]) value);
			} else if (value instanceof Enum) {
				statement.setObject(index, Converters.getDefault().convertString(value));
			} else if (value instanceof java.sql.Date) {
				statement.setDate(index, (java.sql.Date) value);
			} else if (value instanceof java.sql.Time) {
				statement.setTime(index, (java.sql.Time) value);
			} else if (value instanceof Date) {
				statement.setTimestamp(index, Converters.getDefault().convertObject(value, Timestamp.class));
			} else if (value instanceof InputStream) {
				statement.setBinaryStream(index, (InputStream) value);
			} else if (value instanceof URL) {
				statement.setURL(index, (URL) value);
			} else {
				statement.setObject(index, value);
			}
		}
	}

	/**
	 * 生成されたキーを扱います
	 * 
	 * @param statement           PreparedStatement
	 * @param generatedKeyHandler GeneratedKeyHandler
	 * @throws SQLException
	 */
	public static void handleGeneratedKeys(final PreparedStatement statement, GeneratedKeyHandler generatedKeyHandler)
			throws SQLException {
		if (generatedKeyHandler == null) {
			return;
		}
		try (final ResultSet rs = statement.getGeneratedKeys()) {
			final ResultSetMetaData metaData = rs.getMetaData();
			long rowNo = 0;
			while (rs.next()) {
				for (int i = 1; i <= metaData.getColumnCount(); i++) {
					generatedKeyHandler.handle(rowNo, new GeneratedKeyInfo(metaData, rs, i));
				}
				rowNo++;
			}
		}
	}
}
