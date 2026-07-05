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

package com.sqlapp.util;

import static com.sqlapp.util.DbUtils.close;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.Node;

/**
 * SQLを実行するためのクラス
 */
public class StatementUtils {

	private StatementUtils() {
	}

	/**
	 * クエリの実行
	 * 
	 * @param statement
	 */
	public static ResultSet executeQuery(PreparedStatement statement) {
		try {
			return statement.executeQuery();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * クエリの実行
	 * 
	 * @param statement
	 */
	public int executeUpdate(PreparedStatement statement) {
		try {
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * SELECT SQLが結果を持っているかを返すメソッド
	 * 
	 * @param statement
	 */
	public static boolean hasRecord(PreparedStatement statement) throws SQLException {
		ResultSet resultSet = null;
		try {
			resultSet = executeQuery(statement);
			statement.setFetchSize(1);
			return resultSet.next();
		} finally {
			close(resultSet);
		}
	}

	/**
	 * 結果を１個だけ返すSELECT文の実行
	 * 
	 * @param statement
	 */
	public static Object getSingleObject(PreparedStatement statement) throws SQLException {
		ResultSet resultSet = null;
		try {
			resultSet = executeQuery(statement);
			statement.setFetchSize(1);
			if (resultSet.next()) {
				return resultSet.getObject(1);
			}
			return null;
		} finally {
			close(resultSet);
		}
	}

	/**
	 * 結果を１個だけ返すSELECT文の実行
	 * 
	 * @param statement
	 */
	public static Object getSingleObject(CallableStatement statement) throws SQLException {
		ResultSet resultSet = null;
		try {
			resultSet = executeQuery(statement);
			statement.setFetchSize(1);
			if (resultSet.next()) {
				return resultSet.getObject(1);
			}
			return null;
		} finally {
			close(resultSet);
		}
	}

	/**
	 * 読み込み専用のPreparedStatementの作成
	 * 
	 * @param connection
	 * @param node
	 * @param parametersContext
	 */
	public static PreparedStatement createPreparedStatementReadonly(Connection connection, Node node,
			ParametersContext parametersContext) throws SQLException {
		SqlParameterCollection sqlParameters = node.eval(parametersContext);
		PreparedStatement statement = connection.prepareStatement(sqlParameters.getSql(), ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY);
		return statement;
	}

	/**
	 * PreparedStatementの作成
	 * 
	 * @param connection
	 * @param node
	 * @param parametersContext
	 */
	public static PreparedStatement createPreparedStatement(Connection connection, Node node,
			ParametersContext parametersContext) {
		SqlParameterCollection sqlParameters = node.eval(parametersContext);
		PreparedStatement statement;
		try {
			statement = connection.prepareStatement(sqlParameters.getSql());
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return statement;
	}

	/**
	 * 読み込み専用のCallableStatementの作成
	 * 
	 * @param connection
	 * @param node
	 * @param parametersContext
	 */
	public static CallableStatement createCallableStatementReadonly(Connection connection, Node node,
			ParametersContext parametersContext) {
		SqlParameterCollection sqlParameters = node.eval(parametersContext);
		CallableStatement statement;
		try {
			statement = connection.prepareCall(sqlParameters.getSql(), ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return statement;
	}

	/**
	 * CallableStatementの作成
	 * 
	 * @param connection
	 * @param node
	 * @param parametersContext
	 */
	public static CallableStatement createCallableStatement(Connection connection, Node node,
			ParametersContext parametersContext) throws SQLException {
		SqlParameterCollection sqlParameters = node.eval(parametersContext);
		CallableStatement statement = null;
		try {
			statement = connection.prepareCall(sqlParameters.getSql());
		} finally {
			close(statement);
		}
		return statement;
	}
}