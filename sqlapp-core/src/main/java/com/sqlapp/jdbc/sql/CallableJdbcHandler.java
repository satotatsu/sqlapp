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

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.sql.node.SqlNode;
/**
 * Nodeをラップして、JDBCを扱うためのクラス
 * @author satoh
 *
 */
public class CallableJdbcHandler extends JdbcHandler{

	public CallableJdbcHandler(SqlNode node){
		super(node, null);
	}

	public CallableJdbcHandler(SqlNode node, GeneratedKeyHandler generatedKeyHandler){
		super(node, generatedKeyHandler);
	}

	/**
	 * 複数の結果の取得もしくは結果が<code>java.sql.ResultSet</code>を返すか不明の場合にこのメソッドでSQLを実行します。
	 * @param connection
	 * @param context
	 * @throws SQLException
	 */
	protected void doExecute(Connection connection
    		, Object context) throws SQLException{
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
     * @param parametersContext
     */
	@Override
	protected StatementSqlParametersHolder createStatement(Connection connection
    		, Object context, Integer limit) throws SQLException{
        SqlParameterCollection sqlParameters = getNode().eval(context);
        CallableStatement statement= createStatement(connection, sqlParameters, limit);
		return new StatementSqlParametersHolder(statement, sqlParameters);
    }

    /**
     * CallableStatementを作成します
     * @param connection
     * @param sqlParameters
     */
	@Override
	protected CallableStatement createStatement(Connection connection
    		, SqlParameterCollection sqlParameters, Integer limit) throws SQLException{
        CallableStatement statement = getStatement(connection, sqlParameters, limit);
		setBind(statement, sqlParameters);
		return statement;
    }

	@Override
	protected CallableStatement getStatement(Connection connection, SqlParameterCollection sqlParameters, Integer limit) throws SQLException{
		CallableStatement statement = connection.prepareCall(sqlParameters.getSql());
		return statement;
	}

	/**
	 * パラメタのCallableStatementへの設定
	 * @param statement
	 * @param sqlParameters
	 * @throws SQLException
	 */
	protected void setBind(CallableStatement statement
			, SqlParameterCollection sqlParameters) throws SQLException{
		List<BindParameter> list=sqlParameters.getBindParameters();
		int size=list.size();
		for(int i=0;i<size;i++){
			BindParameter bindParameter=list.get(i);
			if (ParameterDirection.Output.equals(bindParameter.getDirection())){
				registerOutParameter(statement, sqlParameters.getDialect(), bindParameter, i+1);
			}else{
				setParameter(statement, this.getDialect(), bindParameter, i+1);
			}
		}
	}

	protected void registerOutParameter(CallableStatement statement, Dialect dialect, BindParameter bindParameter, int index) throws SQLException{
		DataType type=bindParameter.getType();
		if (bindParameter.getType()!=null){
			if (dialect!=null){
				DbDataType<?> dbDataType=dialect.getDbDataTypes().getDbType(type);
				statement.registerOutParameter(index, type.getJdbcType(), dbDataType.getTypeName());
			} else{
				statement.registerOutParameter(index, type.getJdbcType());
			}
		} else{
			statement.registerOutParameter(index, type.getJdbcType());
		}
	}
}
