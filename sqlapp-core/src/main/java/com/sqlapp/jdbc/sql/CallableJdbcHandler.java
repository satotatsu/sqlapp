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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.sql.node.SqlNode;
/**
 * Nodeをラップして、JDBCを扱うためのクラス
 * @author satoh
 *
 */
public class CallableJdbcHandler extends JdbcHandler{

	public CallableJdbcHandler(final SqlNode node){
		super(node, null);
	}

	public CallableJdbcHandler(final SqlNode node, final GeneratedKeyHandler generatedKeyHandler){
		super(node, generatedKeyHandler);
	}

	/**
	 * 複数の結果の取得もしくは結果が<code>java.sql.ResultSet</code>を返すか不明の場合にこのメソッドでSQLを実行します。
	 * @param connection
	 * @param context
	 * @throws SQLException
	 */
	@Override
	protected void doExecute(final Connection connection
    		, final Object context) throws SQLException{
		StatementSqlParametersHolder statementSqlParametersHolder=null;
		try {
			statementSqlParametersHolder=createStatement(connection, context, null);
			handlePreparedStatement(statementSqlParametersHolder.getPreparedStatement());
		} finally{
			close(statementSqlParametersHolder);
		}
	}

    /**
     * CallableStatementを作成します
     * @param connection
     * @param context
     * @param limit
     */
	@Override
	protected StatementSqlParametersHolder createStatement(final Connection connection
    		, final Object context, final Integer limit) throws SQLException{
        final SqlParameterCollection sqlParameters = getNode().eval(context);
        final CallableStatement statement= createStatement(connection, sqlParameters, limit);
		return new StatementSqlParametersHolder(statement, sqlParameters);
    }

    /**
     * CallableStatementを作成します
     * @param connection
     * @param sqlParameters
     */
	@Override
	protected CallableStatement createStatement(final Connection connection
    		, final SqlParameterCollection sqlParameters, final Integer limit) throws SQLException{
        final CallableStatement statement = getStatement(connection, sqlParameters, limit);
		setBind(statement, sqlParameters);
		return statement;
    }

	@Override
	protected CallableStatement getStatement(final Connection connection, final SqlParameterCollection sqlParameters, final Integer limit) throws SQLException{
		final CallableStatement statement = connection.prepareCall(sqlParameters.getSql());
		return statement;
	}

	/**
	 * パラメタのCallableStatementへの設定
	 * @param statement
	 * @param sqlParameters
	 * @throws SQLException
	 */
	protected void setBind(final CallableStatement statement
			, final SqlParameterCollection sqlParameters) throws SQLException{
		final List<BindParameter> list=sqlParameters.getBindParameters();
		final int size=list.size();
		for(int i=0;i<size;i++){
			final BindParameter bindParameter=list.get(i);
			if (ParameterDirection.Output.equals(bindParameter.getDirection())){
				registerOutParameter(statement, sqlParameters.getDialect(), bindParameter, i+1);
			}else{
				setParameter(statement, this.getDialect(), bindParameter, i+1);
			}
		}
	}

	protected void registerOutParameter(final CallableStatement statement, final Dialect dialect, final BindParameter bindParameter, final int index) throws SQLException{
		final DataType type=bindParameter.getType();
		if (bindParameter.getType()!=null){
			if (dialect!=null){
				final DbDataType<?> dbDataType=dialect.getDbDataTypes().getDbType(type);
				statement.registerOutParameter(index, type.getJdbcType(), dbDataType.getTypeName());
			} else{
				statement.registerOutParameter(index, type.getJdbcType());
			}
		} else{
			statement.registerOutParameter(index, type.getJdbcType());
		}
	}
}
