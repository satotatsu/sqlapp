/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.jdbc.sql;

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.ToStringBuilder;

/**
 * SQLとパラメタ管理クラス
 * 
 * @author SATOH
 *
 */
public class SqlParameterCollection implements Serializable, Closeable, Cloneable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4215029691132143165L;
	/**
	 * パラメタ
	 */
	private final List<BindParameterHolder> parameters = new ArrayList<BindParameterHolder>();
	private StringBuilder sql = new StringBuilder();
	/** フェッチサイズ */
	private Integer fetchSize;
	/** 使用するテーブル */
	private Table table;
	/**
	 * 結果セットの型。TYPE_FORWARD_ONLY、TYPE_SCROLL_INSENSITIVE、または TYPE_SCROLL_SENSITIVE
	 * のうちの 1 つ
	 */
	private ResultSetType resultSetType = null;
	/**
	 * 並行処理の種類。CONCUR_READ_ONLY または CONCUR_UPDATABLE
	 */
	private ResultSetConcurrency resultSetConcurrency = null;
	/**
	 * resultSetの保持期間。 HOLD_CURSORS_OVER_COMMIT または CLOSE_CURSORS_AT_COMMIT
	 */
	private ResultSetHoldability resultSetHoldability = null;
	/**
	 * resultSetのFetchDirection。 FETCH_FORWARD または FETCH_REVERSE またはFETCH_UNKNOWN
	 */
	private FetchDirection fetchDirection = null;
	/**
	 * GeneratedKey。 RETURN_GENERATED_KEYS または NO_GENERATED_KEYS
	 */
	private GeneratedKey generatedKey = null;
	/**
	 * 入力オブジェクト
	 */
	private Object inputStream;
	/**
	 * 出力オブジェクト
	 */
	private Object outputStream;

	public SqlParameterCollection() {
	}

	public Table getTable() {
		return table;
	}

	public void setTable(Table table) {
		this.table = table;
	}

	public SqlParameterCollection(Dialect dialect) {
		this.dialect = dialect;
	}

	public void setDialect(Dialect dialect) {
		this.dialect = dialect;
	}

	/**
	 * @return the fetchDirection
	 */
	public FetchDirection getFetchDirection() {
		return fetchDirection;
	}

	/**
	 * @param fetchDirection the fetchDirection to set
	 */
	public void setFetchDirection(FetchDirection fetchDirection) {
		this.fetchDirection = fetchDirection;
	}

	/**
	 * @return the fetchSize
	 */
	public Integer getFetchSize() {
		return fetchSize;
	}

	/**
	 * @param fetchSize the fetchSize to set
	 */
	public void setFetchSize(Integer fetchSize) {
		this.fetchSize = fetchSize;
	}

	/**
	 * @return the generatedKey
	 */
	public GeneratedKey getGeneratedKey() {
		return generatedKey;
	}

	/**
	 * @param generatedKey the generatedKey to set
	 */
	public void setGeneratedKey(GeneratedKey generatedKey) {
		this.generatedKey = generatedKey;
	}

	/**
	 * SQL
	 * 
	 */
	public String getSql() {
		return sql.toString();
	}

	private Dialect dialect = null;

	private static final Pattern pattern = Pattern.compile("^[ \t]*(\n|\r)", Pattern.MULTILINE);

	public SqlParameterCollection addSql(final CharSequence value, boolean condition) {
		if (!condition) {
			return this;
		}
		if (isEmpty(sql)) {
			Matcher matcher = pattern.matcher(value);
			if (matcher.matches()) {
				return this;
			}
		} else {
			char c = sql.charAt(sql.length() - 1);
			if ('\n' == c || '\r' == c) {
				Matcher matcher = pattern.matcher(value);
				if (matcher.matches()) {
					return this;
				}
			}
		}
		sql.append(value);
		return this;
	}

	public SqlParameterCollection addSql(final CharSequence value) {
		return addSql(value, true);
	}

	public SqlParameterCollection addSql(char c) {
		return addSql(c, true);
	}

	public SqlParameterCollection addSql(char c, boolean condition) {
		if (!condition) {
			return this;
		}
		sql.append(c);
		return this;
	}

	private int parameterSize = 0;

	public int getParameterSize() {
		return parameterSize;
	}

	public void setParameterSize(int parameterSize) {
		this.parameterSize = parameterSize;
	}

	/**
	 * パラメタを追加します。
	 * 
	 * @param parameter
	 * 
	 */
	public void add(BindParameter parameter) {
		int pos = parameters.size();
		parameter.setBindingName("?");
		parameter.setOrdinal(pos);
		parameters.add(new BindParameterHolder(parameter));
		addSql(parameter.getBindingName());
		parameterSize++;
	}

	/**
	 * パラメタを追加します。
	 * 
	 * @param parameter
	 * 
	 */
	public void add(BindParameterHolder parameter) {
		parameters.add(parameter);
		if (parameter.getBindParameter() != null) {
			parameterSize++;
		} else {
			parameterSize += parameter.getBindParameters().size();
		}
	}

	/**
	 * パラメタを追加します。
	 * 
	 * @param parameters
	 */
	public void addAll(Collection<BindParameter> parameters) {
		if (CommonUtils.isEmpty(parameters)) {
			return;
		}
		int i = 0;
		for (BindParameter parameter : parameters) {
			if (i > 0) {
				addSql(',');
			}
			add(parameter);
			i++;
		}
	}

	/**
	 * パラメタを追加します
	 * 
	 * @param name
	 * @param value
	 */
	public void add(String name, Object value) {
		BindParameter dbParameter = new BindParameter();
		dbParameter.setName(name);
		if (value instanceof String) {
			if (dialect != null && dialect.recommendsNTypeChar()) {
				dbParameter.setDataType(DataType.NVARCHAR);
			}
		}
		dbParameter.setValue(value);
		add(dbParameter);
	}

	/**
	 * パラメタとSQLのマージ
	 * 
	 * @param sqlParameters
	 */
	public SqlParameterCollection merge(SqlParameterCollection sqlParameters) {
		this.sql.append(sqlParameters.getSql());
		for (BindParameterHolder parameter : sqlParameters.getBindParameters()) {
			this.parameters.add(parameter);
		}
		return this;
	}

	/**
	 * @return the resultSetType
	 */
	public ResultSetType getResultSetType() {
		return resultSetType;
	}

	/**
	 * @param resultSetType the resultSetType to set
	 */
	public void setResultSetType(ResultSetType resultSetType) {
		this.resultSetType = resultSetType;
	}

	/**
	 * @return the resultSetConcurrency
	 */
	public ResultSetConcurrency getResultSetConcurrency() {
		return resultSetConcurrency;
	}

	/**
	 * @param resultSetConcurrency the resultSetConcurrency to set
	 */
	public void setResultSetConcurrency(ResultSetConcurrency resultSetConcurrency) {
		this.resultSetConcurrency = resultSetConcurrency;
	}

	/**
	 * @return the resultSetHoldability
	 */
	public ResultSetHoldability getResultSetHoldability() {
		return resultSetHoldability;
	}

	/**
	 * @param resultSetHoldability the resultSetHoldability to set
	 */
	public void setResultSetHoldability(ResultSetHoldability resultSetHoldability) {
		this.resultSetHoldability = resultSetHoldability;
	}

	/**
	 * @return the inputStream
	 */
	public Object getInputStream() {
		return inputStream;
	}

	/**
	 * @param inputStream the inputStream to set
	 */
	public void setInputStream(Object inputStream) {
		this.inputStream = inputStream;
	}

	/**
	 * @return the outputStream
	 */
	public Object getOutputStream() {
		return outputStream;
	}

	/**
	 * @param outputStream the outputStream to set
	 */
	public void setOutputStream(Object outputStream) {
		this.outputStream = outputStream;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return CommonUtils.hashCode(sql);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof SqlParameterCollection)) {
			return false;
		}
		SqlParameterCollection val = (SqlParameterCollection) obj;
		if (!eq(this.sql, val.sql)) {
			return false;
		}
		if (!eq(this.parameters, val.parameters)) {
			return false;
		}
		if (!eq(this.getFetchSize(), val.getFetchSize())) {
			return false;
		}
		if (!eq(this.getResultSetConcurrency(), val.getResultSetConcurrency())) {
			return false;
		}
		if (!eq(this.getResultSetHoldability(), val.getResultSetHoldability())) {
			return false;
		}
		if (!eq(this.getResultSetType(), val.getResultSetType())) {
			return false;
		}
		if (!eq(this.getFetchDirection(), val.getFetchDirection())) {
			return false;
		}
		if (!eq(this.getGeneratedKey(), val.getGeneratedKey())) {
			return false;
		}
		if (!eq(this.getInputStream(), val.getInputStream())) {
			return false;
		}
		if (!eq(this.getOutputStream(), val.getOutputStream())) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this.getClass());
		builder.add("sql", this.sql);
		builder.add("parameters", this.parameters);
		builder.add("fetchSize", this.getFetchSize());
		builder.add("fetchDirection", this.getFetchDirection());
		if (this.getGeneratedKey() == null) {
			builder.add("resultSetType", this.getResultSetType());
			builder.add("resultSetConcurrency", this.getResultSetConcurrency());
			builder.add("resultSetHoldability", this.getResultSetHoldability());
		} else {
			builder.add("generatedKey", this.getGeneratedKey());
		}
		return builder.toString();
	}

	public List<BindParameterHolder> getBindParameters() {
		return parameters;
	}

	public Dialect getDialect() {
		return dialect;
	}

	public void setSql(StringBuilder sql) {
		this.sql = sql;
	}

	@Override
	public void close() throws IOException {
		this.getBindParameters().forEach(c -> {
			c.close();
		});
		if (this.getInputStream() instanceof Closeable) {
			if (System.in != this.getInputStream()) {
				FileUtils.close((Closeable) this.getInputStream());
			}
		}
		if (this.getOutputStream() instanceof Closeable) {
			if (System.out != this.getOutputStream()) {
				FileUtils.close((Closeable) this.getOutputStream());
			}
		}
	}

	/**
	 * PreparedStatementを生成します
	 * 
	 * @param connection    Connection
	 * @param sqlParameters SqlParameterCollection
	 * @return PreparedStatement
	 * @throws SQLException
	 */
	public PreparedStatement createStatement(final Connection connection) throws SQLException {
		PreparedStatement statement = null;
		if (getGeneratedKey() != null) {
			statement = connection.prepareStatement(getSql(), getGeneratedKey().getValue());
		} else {
			if (getResultSetType() != null || getResultSetHoldability() != null || getResultSetConcurrency() != null) {
				statement = createStatementForQuery(connection, getResultSetType(), getResultSetConcurrency(),
						getResultSetHoldability());
			} else {
				statement = connection.prepareStatement(getSql());
			}
		}
		if (getFetchSize() != null) {
			statement.setFetchSize(getFetchSize().intValue());
		}
		statement.setFetchDirection(
				getFetchDirection() != null ? getFetchDirection().getValue() : FetchDirection.getDefault().getValue());
		return statement;
	}

	/**
	 * PreparedStatementを生成します
	 * 
	 * @param connection           connection
	 * @param resultSetType        ResultSetType
	 * @param resultSetConcurrency ResultSetConcurrency
	 * @return resultSetHoldability ResultSetHoldability
	 * @return PreparedStatement
	 * @throws SQLException
	 */
	public PreparedStatement createStatementForQuery(final Connection connection, ResultSetType resultSetType,
			ResultSetConcurrency resultSetConcurrency, ResultSetHoldability resultSetHoldability) throws SQLException {
		final PreparedStatement statement = connection.prepareStatement(this.getSql(),
				resultSetType != null ? resultSetType.getValue() : ResultSetType.getDefault().getValue(),
				resultSetConcurrency != null ? resultSetConcurrency.getValue()
						: ResultSetConcurrency.getDefault().getValue(),
				resultSetHoldability != null ? resultSetHoldability.getValue()
						: ResultSetHoldability.getDefault().getValue());
		return statement;
	}

	/**
	 * バインド変数の設定
	 * 
	 * @param statement
	 * @param sqlParameters
	 * @throws SQLException
	 */
	public List<BindParameter> setBind(final PreparedStatement statement) throws SQLException {
		final List<BindParameter> list = CommonUtils.list();
		int i = 0;
		for (BindParameterHolder bindParameterHolder : getBindParameters()) {
			if (bindParameterHolder.getBindParameter() != null) {
				final BindParameter bindParameter = bindParameterHolder.getBindParameter();
				setParameters(statement, dialect, bindParameter, i + 1);
				i++;
			} else {
				for (final BindParameter bindParameter : bindParameterHolder.getBindParameters()) {
					setParameters(statement, dialect, bindParameter, i + 1);
					i++;
				}
			}
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
	private void setParameters(final PreparedStatement statement, final Dialect dialect,
			final BindParameter bindParameter, final int index) throws SQLException {
		final DataType type = bindParameter.getDataType();
		final Object value = bindParameter.getValue();
		if (dialect != null && bindParameter.getDataType() != null) {
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
			} else if (value instanceof java.sql.Time || value instanceof LocalTime) {
				statement.setTime(index, Converters.getDefault().convertObject(value, java.sql.Time.class));
			} else if (value instanceof java.sql.Date || value instanceof LocalDate) {
				statement.setDate(index, Converters.getDefault().convertObject(value, java.sql.Date.class));
			} else if (value instanceof Date || value instanceof LocalDateTime) {
				statement.setTimestamp(index, Converters.getDefault().convertObject(value, Timestamp.class));
			} else if (value instanceof InputStream) {
				statement.setBinaryStream(index, (InputStream) value);
			} else if (value instanceof URL) {
				statement.setURL(index, Converters.getDefault().convertObject(value, URL.class));
			} else if (value instanceof URI) {
			} else {
				statement.setObject(index, value);
			}
		}
	}

	@Override
	public SqlParameterCollection clone() {
		try {
			SqlParameterCollection clone = (SqlParameterCollection) super.clone();
			clone.sql = new StringBuilder(this.sql);
			List<BindParameterHolder> parameters = CommonUtils.list(this.parameters);
			clone.parameters.clear();
			clone.parameters.addAll(parameters);
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
}
