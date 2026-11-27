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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Nodeをラップして、JDBCを扱うためのクラス
 * 
 * @author satoh
 *
 */
public class CallableJdbcHandler extends JdbcHandler {

	public CallableJdbcHandler(final SqlNode node) {
		super(node, null, (rs) -> {
		});
	}

	public CallableJdbcHandler(final SqlNode node, final GeneratedKeyHandler generatedKeyHandler) {
		super(node, generatedKeyHandler);
	}

	/**
	 * 複数の結果の取得もしくは結果が<code>java.sql.ResultSet</code>を返すか不明の場合にこのメソッドでSQLを実行します。
	 * 
	 * @param connection
	 * @param context
	 * @throws SQLException
	 */
	@Override
	protected void doExecute(final Connection connection, final Object context) throws SQLException {
		StatementSqlParametersHolder statementSqlParametersHolder = null;
		try {
			statementSqlParametersHolder = createStatement(connection, context, null);
			handlePreparedStatement(statementSqlParametersHolder.getPreparedStatement());
		} finally {
			close(statementSqlParametersHolder);
		}
	}

	/**
	 * CallableStatementを作成します
	 * 
	 * @param connection
	 * @param context
	 * @param limit
	 */
	@Override
	protected StatementSqlParametersHolder createStatement(final Connection connection, final Object context,
			final Integer limit) throws SQLException {
		final SqlParameterCollection sqlParameters = getNode().eval(context);
		try {
			final CallableStatement statement = createStatement(connection, sqlParameters, limit);
			return new StatementSqlParametersHolder(statement, sqlParameters);
		} catch (SQLException | RuntimeException | Error failure) {
			close(null, sqlParameters);
			throw failure;
		}
	}

	/**
	 * CallableStatementを作成します
	 * 
	 * @param connection
	 * @param sqlParameters
	 */
	@Override
	protected CallableStatement createStatement(final Connection connection, final SqlParameterCollection sqlParameters,
			final Integer limit) throws SQLException {
		final CallableStatement statement = getStatement(connection, sqlParameters, limit);
		try {
			setBind(statement, sqlParameters);
			return statement;
		} catch (SQLException | RuntimeException | Error failure) {
			close(statement, null);
			throw failure;
		}
	}

	@Override
	protected CallableStatement getStatement(final Connection connection, final SqlParameterCollection sqlParameters,
			final Integer limit) throws SQLException {
		final CallableStatement statement = connection.prepareCall(sqlParameters.getSql());
		return statement;
	}

	/**
	 * パラメタのCallableStatementへの設定
	 * 
	 * @param statement
	 * @param sqlParameters
	 * @throws SQLException
	 */
	protected void setBind(final CallableStatement statement, final SqlParameterCollection sqlParameters)
			throws SQLException {
		if (getQueryTimeout() != null) {
			statement.setQueryTimeout(getQueryTimeout());
		}
		final List<BindParameterHolder> list = sqlParameters.getBindParameters();
		final Dialect dialect = getDialect() != null ? getDialect() : sqlParameters.getDialect();
		int index = 1;
		for (BindParameterHolder holder : list) {
			for (BindParameter parameter : holder.stream().toList()) {
				if (parameter.getDirection() == ParameterDirection.Output
						|| parameter.getDirection() == ParameterDirection.Inout) {
					registerOutParameter(statement, dialect, parameter, index);
				}
				if (parameter.getDirection() != ParameterDirection.Output) {
					JdbcHandlerUtils.setParameters(statement, dialect, parameter, index);
				}
				index++;
			}
		}
	}

	@Override
	protected List<BindParameter> setBind(final PreparedStatement statement, final SqlParameterCollection sqlParameters)
			throws SQLException {
		setBind((CallableStatement) statement, sqlParameters);
		return sqlParameters.getBindParameters().stream().flatMap(BindParameterHolder::stream).toList();
	}

	protected void registerOutParameter(final CallableStatement statement, final Dialect dialect,
			final BindParameter bindParameter, final int index) throws SQLException {
		final DataType type = bindParameter.getDataType();
		if (type == null) {
			throw new IllegalArgumentException("Output parameter '" + bindParameter.getName() + "' requires a data type");
		}
		if (dialect != null) {
			final DbDataType<?> dbDataType = dialect.getDbDataTypes().getDbType(type);
			if (dbDataType != null) {
				statement.registerOutParameter(index, type.getJdbcType(), dbDataType.getTypeName());
				return;
			}
		}
		statement.registerOutParameter(index, type.getJdbcType());
	}
}
