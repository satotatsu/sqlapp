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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class JdbcQueryHandler extends JdbcHandler {
	/**
	 * JDBCフェッチサイズ
	 */
	private Integer fetchSize = null;

	private ResultSetNextHandler resultSetNextHandler = null;

	public JdbcQueryHandler(SqlNode node, ResultSetNextHandler resultSetNextHandler) {
		super(node);
		this.resultSetNextHandler = resultSetNextHandler;
	}

	/**
	 * limit,offset指定をしてクエリを実行します
	 * 
	 * @param connection
	 * @param parametersContext
	 * @param limit
	 * @param offset
	 * @throws SQLException
	 */
	public void execute(Connection connection,
			ParametersContext parametersContext, Integer limit, Integer offset)
			throws SQLException {
		try {
			executeQueryCallback(connection, parametersContext, limit, offset);
		} catch (SQLException e) {
			handleSqlException(e);
		}
	}

	/**
	 * limit,offset指定をしてクエリを実行します
	 * 
	 * @param connection
	 * @param sqlParameters
	 * @param limit
	 * @param offset
	 * @throws SQLException
	 */
	public void execute(Connection connection,
			SqlParameterCollection sqlParameters, Integer limit, Integer offset)
			throws SQLException {
		try {
			executeQueryCallback(connection, sqlParameters, limit, offset);
		} catch (SQLException e) {
			handleSqlException(e);
		}
	}

	/**
	 * <code>java.sql.PreparedStatement#executeQuery()</code>を実行した結果を扱います。
	 * <code>handleResultSet(ResultSet resultSet)<code/>もしくは<code>handleResultSetNext(ResultSet resultSet)
	 * を継承して使用します。
	 * 
	 * @param connection
	 * @param parametersContext
	 * @param limit
	 * @param offset
	 * @param dialect
	 * @throws SQLException
	 */
	protected void executeQueryCallback(Connection connection,
			SqlParameterCollection sqlParameters, Integer limit, Integer offset)
			throws SQLException {
		PreparedStatement statement = null;
		try {
			if (offset != null) {
				statement = createStatementScrollInsensitive(connection,
						sqlParameters);
			} else {
				statement = createStatement(connection, sqlParameters, limit);
			}
			executeQueryCallback(connection, statement, limit, offset);
		} finally {
			close(statement, sqlParameters);
		}
	}

	/**
	 * スクロール可能なPreparedStatementを作成します
	 * 
	 * @param connection
	 * @param parametersContext
	 */
	protected StatementSqlParametersHolder createStatementScrollInsensitive(
			Connection connection, ParametersContext parametersContext)
			throws SQLException {
		SqlParameterCollection sqlParameters = getNode().eval(parametersContext);
		PreparedStatement preparedStatement=createStatementScrollInsensitive(connection, sqlParameters);
		return new StatementSqlParametersHolder(preparedStatement, sqlParameters);
	}

	/**
	 * スクロール可能なPreparedStatementを作成します
	 * 
	 * @param connection
	 * @param parametersContext
	 */
	protected PreparedStatement createStatementScrollInsensitive(
			Connection connection, SqlParameterCollection sqlParameters)
			throws SQLException {
		PreparedStatement statement = connection.prepareStatement(
				sqlParameters.getSql(), ResultSet.TYPE_SCROLL_INSENSITIVE,
				ResultSet.CONCUR_READ_ONLY);
		setBind(statement, sqlParameters);
		return statement;
	}

	@SuppressWarnings("unchecked")
	@Override
	public JdbcQueryHandler execute(Connection connection,
			ParametersContext parametersContext) {
		try {
			executeQueryCallback(connection, parametersContext, null, null);
			return this;
		} catch (SQLException e) {
			handleSqlException(e);
			return this;
		}
	}

	public JdbcQueryHandler execute(Connection connection) {
		execute(connection, new ParametersContext());
		return this;
	}

	/**
	 * <code>java.sql.PreparedStatement#executeQuery()</code>を実行した結果を扱います。
	 * <code>handleResultSet(ResultSet resultSet)<code/>もしくは<code>handleResultSetNext(ResultSet resultSet)
	 * を継承して使用します。
	 * 
	 * @param connection
	 * @param parametersContext
	 * @param limit
	 * @param offset
	 * @param dialect
	 * @throws SQLException
	 */
	protected void executeQueryCallback(Connection connection,
			ParametersContext parametersContext, Integer limit, Integer offset)
			throws SQLException {
		StatementSqlParametersHolder statementSqlParametersHolder = null;
		try {
			if (offset != null) {
				ParameterDefinition def=this.getNode().getOffsetParameter();
				if (def==null){
					statementSqlParametersHolder = createStatementScrollInsensitive(connection,
							parametersContext);
				} else{
					parametersContext.put(def.getName(), offset);
					statementSqlParametersHolder = createStatement(connection, parametersContext, limit);
				}
			} else {
				statementSqlParametersHolder = createStatement(connection, parametersContext, limit);
			}
			executeQueryCallback(connection, statementSqlParametersHolder.getPreparedStatement(), limit, offset);
		} finally {
			close(statementSqlParametersHolder);
		}
	}

	/**
	 * <code>java.sql.PreparedStatement#executeQuery()</code>を実行した結果を扱います。
	 * <code>handleResultSet(ResultSet resultSet)</code>もしくは
	 * <code>handleResultSetNext(ResultSet resultSet)</code> を継承して使用します。
	 * 
	 * @param connection
	 * @param statement
	 * @param limit
	 * @param offset
	 * @throws SQLException
	 */
	protected void executeQueryCallback(Connection connection,
			PreparedStatement statement, Integer limit, Integer offset)
			throws SQLException {
		ExResultSet resultSet = null;
		try {
			if (fetchSize != null) {
				statement.setFetchSize(fetchSize.intValue());
			}
			resultSet = new ExResultSet(statement.executeQuery());
			ParameterDefinition offsetParam=this.getNode().getOffsetParameter();
			if (offset != null&&offsetParam==null) {
				resultSet.relative(offset.intValue());
			}
			ParameterDefinition limitParam=this.getNode().getRowParameter();
			if (limit != null&&limitParam==null) {
				handleResultSet(resultSet, limit.intValue());
			} else {
				handleResultSet(resultSet);
			}
		} finally {
			close(resultSet);
		}
	}

	protected void handleResultSet(ExResultSet resultSet, int limit)
			throws SQLException {
		int i = 0;
		while (resultSet.next() && i < limit) {
			handleResultSetNext(resultSet);
			i++;
		}
	}

	protected void handleResultSetNext(ExResultSet resultSet) throws SQLException {
		resultSetNextHandler.handleResultSetNext(resultSet);
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
}
