/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.function.TriFunction;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatementHolder implements Closeable {
	private final SqlNode sqlNode;
	private BatchExecResult batchExecResult;

	private final Map<Integer, ParameterAndStatementHolder> holders = CommonUtils.map();
	private TriFunction<Table, SqlType, String, String> sqlHandler = (t, sqlType, sql) -> {
		return sql;
	};

	public void setSqlParameters(int size, SqlParameterCollection sqlParameters, PreparedStatement statement) {
		final ParameterAndStatementHolder holder = new ParameterAndStatementHolder(sqlParameters, statement);
		holders.put(size, holder);
	}

	public SqlParameterCollection getSqlParameters(int size) {
		ParameterAndStatementHolder holder = holders.get(size);
		if (holder != null) {
			return holder.getSqlParameters();
		}
		return null;
	}

	public PreparedStatement getPreparedStatement(int size) {
		ParameterAndStatementHolder holder = holders.get(size);
		if (holder != null) {
			return holder.getStatement();
		}
		return null;
	}

	public StatementHolder(SqlNode sqlNode) {
		this.sqlNode = sqlNode;
	}

	@Override
	public void close() {
		for (ParameterAndStatementHolder holder : holders.values()) {
			holder.close();
		}
	}

	public PreparedStatement createStatement(Connection connection, int parameterCount, Object obj, boolean identity)
			throws SQLException {
		SqlParameterCollection sqlParameters = getSqlNode().eval(obj);
		if (sqlNode.getSqlType() == SqlType.INSERT) {
			if (identity) {
				sqlParameters.setGeneratedKey(GeneratedKey.RETURN_GENERATED_KEYS);
			}
		}
		sqlParameters.setSqlTyp(getSqlNode().getSqlType());
		sqlParameters.setSqlHandler(sqlHandler);
		PreparedStatement statement = sqlParameters.createStatement(connection);
		setSqlParameters(parameterCount, sqlParameters, statement);
		sqlParameters.setBind(statement);
		return statement;
	}

	public PreparedStatement getStatement(int parameterCount, Object obj) throws SQLException {
		SqlParameterCollection sqlParameters = getSqlParameters(parameterCount);
		PreparedStatement statement = getPreparedStatement(parameterCount);
		getSqlNode().reEval(obj, sqlParameters);
		sqlParameters.setBind(statement);
		return statement;
	}

	@Getter
	static class ParameterAndStatementHolder implements Closeable {
		final PreparedStatement statement;
		final SqlParameterCollection sqlParameters;

		ParameterAndStatementHolder(SqlParameterCollection sqlParameters, PreparedStatement statement) {
			this.sqlParameters = sqlParameters;
			this.statement = statement;
		}

		@Override
		public void close() {
			FileUtils.close(statement);
		}
	}
}
