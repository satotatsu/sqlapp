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

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.GeneratedKeyHandler.GeneratedKeyInfo;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DbUtils;
import com.sqlapp.util.FileUtils;

/**
 * Nodeをラップして、JDBCを扱うためのクラス
 * 
 * @author satoh
 *
 */
public class JdbcHandler {

	private final SqlNode node;
	/**
	 * 生成されたキーをハンドルするオブジェクト
	 */
	protected GeneratedKeyHandler generatedKeyHandler = null;
	/**
	 * クエリのタイムアウト(秒)
	 */
	private Integer queryTimeout = null;
	private Dialect dialect = null;
	/**
	 * フェッチしたサイズの結果
	 */
	private long fetchSizeResult = 0;
	/**
	 * フェッチにかかった合計処理時間(ミリ秒)
	 */
	private long totalFetchProcessTime = 0;
	/**
	 * 更新結果件数
	 */
	private long updateCount = 0;

	public JdbcHandler(SqlNode node) {
		this.node = node;
	}

	public JdbcHandler(SqlNode node, GeneratedKeyHandler generatedKeyHandler) {
		this.node = node;
		this.generatedKeyHandler = generatedKeyHandler;
	}

	/**
	 * 複数の結果の取得もしくは結果が<code>java.sql.ResultSet</code>を返すか不明の場合にこのメソッドでSQLを実行します。
	 * 
	 * @param connection
	 * @param context
	 * @param generatedKeyHandler
	 */
	@SuppressWarnings("unchecked")
	public <T extends JdbcHandler> T execute(Connection connection,
			Object context, GeneratedKeyHandler generatedKeyHandler) {
		try {
			this.generatedKeyHandler = generatedKeyHandler;
			if (context instanceof ParametersContext) {
				doExecute(connection, (ParametersContext) context);
			} else {
				doExecute(connection, context);
			}
			return (T) this;
		} catch (SQLException e) {
			handleSqlException(e);
			return (T) this;
		}
	}

	/**
	 * 複数の結果の取得もしくは結果が<code>java.sql.ResultSet</code>を返すか不明の場合にこのメソッドでSQLを実行します。
	 * 
	 * @param connection
	 * @param context
	 */
	public <T extends JdbcHandler> T execute(Connection connection,
			Object context) {
		return this.execute(connection, context, null);
	}

	/**
	 * 複数の結果の取得もしくは結果が<code>java.sql.ResultSet</code>を返すか不明の場合にこのメソッドでSQLを実行します。
	 * 
	 * @param connection
	 * @param context
	 */
	public <T extends JdbcHandler> T execute(Connection connection,
			ParametersContext context) {
		return this.execute(connection, context, null);
	}

	/**
	 * @return the node
	 */
	protected SqlNode getNode() {
		return node;
	}

	/**
	 * 複数の結果の取得もしくは結果が<code>java.sql.ResultSet</code>を返すか不明の場合にこのメソッドでSQLを実行します。
	 * 
	 * @param connection
	 * @param sqlParameters
	 * @param generatedKeyHandler
	 */
	@SuppressWarnings("unchecked")
	public <T extends JdbcHandler> T execute(Connection connection,
			SqlParameterCollection sqlParameters,
			GeneratedKeyHandler generatedKeyHandler) {
		try {
			this.generatedKeyHandler = generatedKeyHandler;
			doExecute(connection, sqlParameters);
			return (T) this;
		} catch (SQLException e) {
			handleSqlException(e);
			return (T) this;
		}
	}

	/**
	 * 複数の結果の取得もしくは結果が<code>java.sql.ResultSet</code>を返すか不明の場合にこのメソッドでSQLを実行します。
	 * 
	 * @param connection
	 * @param sqlParameters
	 */
	@SuppressWarnings("unchecked")
	public <T extends JdbcHandler> T execute(Connection connection,
			SqlParameterCollection sqlParameters) {
		try {
			this.doExecute(connection, sqlParameters);
		} catch (SQLException e) {
			handleSqlException(e);
			return (T) this;
		}
		return (T) this;
	}

	/**
	 * 複数の結果の取得もしくは結果が<code>java.sql.ResultSet</code>を返すか不明の場合にこのメソッドでSQLを実行します。
	 * 
	 * @param connection
	 * @param context
	 * @throws SQLException
	 */
	protected void doExecute(Connection connection, Object context)
			throws SQLException {
		StatementSqlParametersHolder statementSqlParametersHolder = null;
		try {
			statementSqlParametersHolder = createStatement(connection, context, null);
			handlePreparedStatement(statementSqlParametersHolder.getPreparedStatement());
		} finally {
			if (statementSqlParametersHolder!=null){
				close(statementSqlParametersHolder);
			}
		}
	}

	protected void close(StatementSqlParametersHolder statementSqlParametersHolder){
		if (statementSqlParametersHolder!=null){
			close(statementSqlParametersHolder.getPreparedStatement(), statementSqlParametersHolder.getSqlParameters());
		}
	}
	
	protected void close(ResultSet resultSet){
		DbUtils.close(resultSet);
	}
	
	protected void close(PreparedStatement statement, SqlParameterCollection sqlParameters){
		DbUtils.close(statement);
		if (sqlParameters!=null){
			FileUtils.close(sqlParameters);
		}
	}

	/**
	 * 複数の結果の取得もしくは結果が<code>java.sql.ResultSet</code>を返すか不明の場合にこのメソッドでSQLを実行します。
	 * 
	 * @param connection
	 * @param sqlParameters
	 * @throws SQLException
	 */
	protected void doExecute(Connection connection,
			SqlParameterCollection sqlParameters) throws SQLException {
		PreparedStatement statement = null;
		try {
			statement = createStatement(connection, sqlParameters, null);
			handlePreparedStatement(statement);
		} finally {
			close(statement, sqlParameters);
		}
	}

	protected void handlePreparedStatement(PreparedStatement statement)
			throws SQLException {
		ExResultSet resultSet = null;
		try {
			if (executeStatement(statement)) {
				resultSet = new ExResultSet(statement.getResultSet());
				handleResultSet(resultSet);
			} else {
				int updateCount = statement.getUpdateCount();
				if (updateCount != -1) {
					handleUpdate(statement, updateCount);
					handleGeneratedKeys(statement);
				}
			}
			handleMoreResults(statement);
		} finally {
			DbUtils.close(resultSet);
		}
	}

	protected boolean executeStatement(PreparedStatement statement)
			throws SQLException {
		return statement.execute();
	}

	protected void handleSqlException(SQLException e) {
		throw new RuntimeException(e);
	}

	protected void handleMoreResults(PreparedStatement statement)
			throws SQLException {
		boolean moreResults = statement.getMoreResults();
		int updateCount = statement.getUpdateCount();
		while (moreResults) {
			if (updateCount != -1) {
				handleUpdate(statement, updateCount);
				handleGeneratedKeys(statement);
			} else {
				ExResultSet resultSet = null;
				try {
					resultSet = new ExResultSet(statement.getResultSet());
					handleResultSet(resultSet);
				} finally {
					close(resultSet);
				}
			}
			moreResults = statement.getMoreResults();
			updateCount = statement.getUpdateCount();
		}
	}

	protected void handleResultSet(ExResultSet resultSet) throws SQLException {
		int i = 0;
		long start = System.currentTimeMillis();
		while (resultSet.next()) {
			handleResultSetNext(resultSet);
		}
		long end = System.currentTimeMillis();
		fetchSizeResult = fetchSizeResult + i;
		totalFetchProcessTime = totalFetchProcessTime + (end - start);
	}

	protected void handleResultSetNext(ExResultSet resultSet) throws SQLException {
	}

	protected void handleUpdate(PreparedStatement statement, long updateCount)
			throws SQLException {
		this.updateCount = this.updateCount + updateCount;
	}

	/**
	 * @return the updateCount
	 */
	public long getUpdateCount() {
		return updateCount;
	}

	/**
	 * 生成されたキーを扱います
	 * 
	 * @param statement
	 * @throws SQLException
	 */
	protected void handleGeneratedKeys(PreparedStatement statement)
			throws SQLException {
		if (generatedKeyHandler == null) {
			return;
		}
		ResultSet rs = null;
		try {
			rs = statement.getGeneratedKeys();
			ResultSetMetaData metaData = rs.getMetaData();
			long rowNo = 0;
			while (rs.next()) {
				for (int i = 1; i <= metaData.getColumnCount(); i++) {
					this.generatedKeyHandler.handle(rowNo,
							new GeneratedKeyInfo(metaData, rs, i));
				}
				rowNo++;
			}
		} finally {
			close(rs);
		}
	}

	/**
	 * PreparedStatementの作成
	 * 
	 * @param connection
	 * @param sqlParameters
	 * @param dialect
	 */
	protected PreparedStatement createStatement(Connection connection,
			SqlParameterCollection sqlParameters, Integer limit) throws SQLException {
		PreparedStatement statement = getStatement(connection, sqlParameters, limit);
		setBind(statement, sqlParameters);
		return statement;
	}

	/**
	 * PreparedStatementの作成
	 * 
	 * @param connection
	 * @param context
	 */
	protected StatementSqlParametersHolder createStatement(Connection connection,
			Object context, Integer limit) throws SQLException {
		SqlParameterCollection sqlParameters = createSqlParameterCollection(context);
		PreparedStatement preparedStatement= createStatement(connection, sqlParameters, limit);
		return new StatementSqlParametersHolder(preparedStatement, sqlParameters);
	}

	protected SqlParameterCollection createSqlParameterCollection(Object context){
		return node.eval(context);
	}
	
	static class StatementSqlParametersHolder{
		StatementSqlParametersHolder(PreparedStatement preparedStatement, SqlParameterCollection sqlParameters){
			this.preparedStatement=preparedStatement;
			this.sqlParameters=sqlParameters;
		}
		private final PreparedStatement preparedStatement;
		private final SqlParameterCollection sqlParameters;
		/**
		 * @return the preparedStatement
		 */
		public PreparedStatement getPreparedStatement() {
			return preparedStatement;
		}

		/**
		 * @return the sqlParameters
		 */
		public SqlParameterCollection getSqlParameters() {
			return sqlParameters;
		}
	}
	
	protected PreparedStatement getStatement(Connection connection,
			SqlParameterCollection sqlParameters, Integer limit) throws SQLException {
		PreparedStatement statement = null;
		if (generatedKeyHandler != null) {
			statement = connection.prepareStatement(sqlParameters.getSql(),
					Statement.RETURN_GENERATED_KEYS);
		} else {
			if (sqlParameters.getResultSetType() != null
					|| sqlParameters.getResultSetHoldability() != null
					|| sqlParameters.getResultSetConcurrency() != null) {
				statement = connection
						.prepareStatement(
								sqlParameters.getSql(),
								(sqlParameters.getResultSetType() != null ? sqlParameters
										.getResultSetType() : ResultSetType
										.getDefault()).getValue(),
								(sqlParameters.getResultSetConcurrency() != null ? sqlParameters
										.getResultSetConcurrency()
										: ResultSetConcurrency.getDefault())
										.getValue(),
								(sqlParameters.getResultSetHoldability() != null ? sqlParameters
										.getResultSetHoldability()
										: ResultSetHoldability.getDefault())
										.getValue());
			} else {
				statement = connection.prepareStatement(sqlParameters.getSql());
			}
		}
		if (sqlParameters.getFetchSize() != null) {
			statement.setFetchSize(sqlParameters.getFetchSize());
		} else{
			if (limit!=null&&limit.intValue()>0){
				if (limit.intValue()<1024){
					statement.setFetchSize(limit);
				} else{
					statement.setFetchSize(1024);
				}
			} else{
				statement.setFetchSize(256);
			}
		}
		return statement;
	}

	/**
	 * バインド変数の設定
	 * 
	 * @param statement
	 * @param sqlParameters
	 * @throws SQLException
	 */
	protected List<BindParameter> setBind(PreparedStatement statement,
			SqlParameterCollection sqlParameters) throws SQLException {
		if (queryTimeout != null) {
			statement.setQueryTimeout(queryTimeout.intValue());
		}
		List<BindParameter> list = sqlParameters.getBindParameters();
		int size = list.size();
		for (int i = 0; i < size; i++) {
			BindParameter bindParameter = list.get(i);
			setParameter(statement, this.getDialect(), bindParameter, i + 1);
		}
		return list;
	}

	protected void setParameter(PreparedStatement statement, Dialect dialect,
			BindParameter bindParameter, int index) throws SQLException {
		DataType type = bindParameter.getType();
		Object value=bindParameter.getValue();
		if (dialect != null && bindParameter.getType() != null) {
			DbDataType<?> dbDataType = dialect.getDbDataTypes().getDbType(type);
			dbDataType.getJdbcTypeHandler().setObject(statement, index,
					value);
		} else {
			if (value instanceof Enum){
				statement.setObject(index, Converters.getDefault().convertString(value));
			} else if (value instanceof java.sql.Date){
				statement.setDate(index, (java.sql.Date)value);
			} else if (value instanceof java.sql.Time){
				statement.setTime(index, (java.sql.Time)value);
			} else if (value instanceof Date){
				statement.setTimestamp(index, Converters.getDefault().convertObject(value, Timestamp.class));
			} else if (value instanceof InputStream){
				statement.setBinaryStream(index, (InputStream)value);
			} else{
				statement.setObject(index, value);
			}
		}
	}

	/**
	 * @param queryTimeout
	 *            the queryTimeout to set
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public <T extends JdbcHandler> T setQueryTimeout(Integer queryTimeout) {
		this.queryTimeout = queryTimeout;
		return (T) this;
	}

	/**
	 * @return the dialect
	 */
	public Dialect getDialect() {
		return dialect;
	}

	/**
	 * @param dialect
	 *            the dialect to set
	 */
	public void setDialect(Dialect dialect) {
		this.dialect = dialect;
	}

	/**
	 * @return the fetchSizeResult
	 */
	public long getFetchSizeResult() {
		return fetchSizeResult;
	}

	/**
	 * @return the totalFetchProcessTime
	 */
	public long getTotalFetchProcessTime() {
		return totalFetchProcessTime;
	}

}
