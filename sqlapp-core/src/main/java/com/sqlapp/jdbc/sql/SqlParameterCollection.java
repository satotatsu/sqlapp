/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.jdbc.sql;

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.ToStringBuilder;

/**
 * SQLとパラメタ管理クラス
 * 
 * @author SATOH
 *
 */
public class SqlParameterCollection implements Serializable,Closeable{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4215029691132143165L;
	/**
	 * パラメタ
	 */
	private List<BindParameter> parameters = new ArrayList<BindParameter>();
	private StringBuilder sql = new StringBuilder();
	/** フェッチサイズ */
	private Integer fetchSize;
	/**
	 * 結果セットの型。TYPE_FORWARD_ONLY、TYPE_SCROLL_INSENSITIVE、または
	 * TYPE_SCROLL_SENSITIVE のうちの 1 つ
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
	 * 入力オブジェクト
	 */
	private Object inputStream;
	/**
	 * 出力オブジェクト
	 */
	private Object outputStream;

	public SqlParameterCollection() {
	}

	public SqlParameterCollection(Dialect dialect) {
		this.dialect = dialect;
	}

	public void setDialect(Dialect dialect) {
		this.dialect = dialect;
	}

	/**
	 * @return the fetchSize
	 */
	public Integer getFetchSize() {
		return fetchSize;
	}

	/**
	 * @param fetchSize
	 *            the fetchSize to set
	 */
	public void setFetchSize(Integer fetchSize) {
		this.fetchSize = fetchSize;
	}

	/**
	 * SQL
	 * 
	 */
	public String getSql() {
		return sql.toString();
	}

	private Dialect dialect = null;

	private static final Pattern pattern = Pattern.compile("^[ \t]*(\n|\r)",
			Pattern.MULTILINE);

	public SqlParameterCollection addSql(final CharSequence value, boolean condition) {
		if (!condition){
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
		return addSql(value,true);
	}

	public SqlParameterCollection addSql(char c) {
		return addSql(c, true);
	}

	public SqlParameterCollection addSql(char c, boolean condition) {
		if (!condition){
			return this;
		}
		sql.append(c);
		return this;
	}
	
	/**
	 * パラメタを追加します。
	 * 
	 * @param parameter
	 */
	public SqlParameterCollection add(BindParameter parameter) {
		int pos = parameters.size();
		StringBuilder bindName = new StringBuilder();
		bindName.append('?');
		parameter.setBindingName(bindName.toString());
		parameter.setOrdinal(pos);
		parameters.add(parameter);
		addSql(parameter.getBindingName());
		return this;
	}

	/**
	 * パラメタを追加します。
	 * 
	 * @param parameters
	 */
	public SqlParameterCollection addAll(BindParameter... parameters) {
		if (CommonUtils.isEmpty(parameters)){
			return this;
		}
		int i=0;
		for(BindParameter parameter:parameters){
			if (i>0){
				addSql(',');
			}
			add(parameter);
			i++;
		}
		return this;
	}

	/**
	 * パラメタを追加します。
	 * 
	 * @param parameters
	 */
	public SqlParameterCollection addAll(Collection<BindParameter> parameters) {
		if (CommonUtils.isEmpty(parameters)){
			return this;
		}
		int i=0;
		for(BindParameter parameter:parameters){
			if (i>0){
				addSql(',');
			}
			add(parameter);
			i++;
		}
		return this;
	}

	/**
	 * パラメタを追加します
	 * 
	 * @param name
	 * @param value
	 */
	public SqlParameterCollection add(String name, Object value) {
		BindParameter dbParameter = new BindParameter();
		dbParameter.setName(name);
		if (value instanceof String) {
			if (dialect != null && dialect.recommendsNTypeChar()) {
				dbParameter.setType(DataType.NVARCHAR);
			}
		}
		dbParameter.setValue(value);
		return add(dbParameter);
	}

	/**
	 * パラメタとSQLのマージ
	 * 
	 * @param sqlParameters
	 */
	public SqlParameterCollection merge(SqlParameterCollection sqlParameters) {
		this.sql.append(sqlParameters.getSql());
		for (BindParameter parameter : sqlParameters.getBindParameters()) {
			add(parameter);
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
	 * @param resultSetType
	 *            the resultSetType to set
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
	 * @param resultSetConcurrency
	 *            the resultSetConcurrency to set
	 */
	public void setResultSetConcurrency(
			ResultSetConcurrency resultSetConcurrency) {
		this.resultSetConcurrency = resultSetConcurrency;
	}

	/**
	 * @return the resultSetHoldability
	 */
	public ResultSetHoldability getResultSetHoldability() {
		return resultSetHoldability;
	}

	/**
	 * @param resultSetHoldability
	 *            the resultSetHoldability to set
	 */
	public void setResultSetHoldability(
			ResultSetHoldability resultSetHoldability) {
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
		builder.add("resultSetType", this.getResultSetType());
		builder.add("resultSetConcurrency", this.getResultSetConcurrency());
		builder.add("resultSetHoldability", this.getResultSetHoldability());
		return builder.toString();
	}

	public List<BindParameter> getBindParameters() {
		return parameters;
	}

	public Dialect getDialect() {
		return dialect;
	}

	public void setBindParameters(List<BindParameter> parameters) {
		this.parameters = parameters;
	}

	public void setSql(StringBuilder sql) {
		this.sql = sql;
	}

	@Override
	public void close() throws IOException {
		this.getBindParameters().forEach(c->{
			if (c.getValue() instanceof InputStream){
				FileUtils.close((InputStream)c.getValue());
			}else if (c.getValue() instanceof Reader){
				FileUtils.close((Reader)c.getValue());
			}
		});
		if (this.getInputStream() instanceof Closeable){
			if (System.in!=this.getInputStream()){
				FileUtils.close((Closeable)this.getInputStream());
			}
		}
		if (this.getOutputStream() instanceof Closeable){
			if (System.out!=this.getOutputStream()){
				FileUtils.close((Closeable)this.getOutputStream());
			}
		}
	}
}
