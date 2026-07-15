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
import java.util.List;

import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.function.TriFunction;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatementHolder implements Closeable {
	private final SqlNode sqlNode;
	private BatchExecResult batchExecResult;

	private final DoubleKeyMap<Integer, Integer, ParameterAndStatementHolder> holders = CommonUtils.doubleKeyMap();
	private TriFunction<Table, SqlType, String, String> sqlHandler = (t, sqlType, sql) -> {
		return sql;
	};

	public void setSqlParameters(int columnSize, int rowSize, SqlParameterCollection sqlParameters,
			PreparedStatement statement) {
		final ParameterAndStatementHolder holder = new ParameterAndStatementHolder(sqlParameters, statement);
		holders.put(columnSize, rowSize, holder);
	}

	public SqlParameterCollection getSqlParameters(int columnSize, List<?> rows) {
		ParameterAndStatementHolder holder = holders.get(columnSize, rows.size());
		if (holder != null) {
			return holder.getSqlParameters();
		}
		return null;
	}

	public SqlParameterCollection getSqlParameters(int columnSize, int rowSize) {
		ParameterAndStatementHolder holder = holders.get(columnSize, rowSize);
		if (holder != null) {
			return holder.getSqlParameters();
		}
		return null;
	}

	public PreparedStatement getPreparedStatement(int columnSize, int rowSize) {
		ParameterAndStatementHolder holder = holders.get(columnSize, rowSize);
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

	public PreparedStatement createStatement(Connection connection, SqlSignature sqlSignature, List<?> rows,
			boolean identity) throws SQLException {
		return createStatement(connection, sqlSignature, rows.size(), rows, identity);
	}

	public PreparedStatement createStatement(Connection connection, SqlSignature sqlSignature, int rowSize, Object obj,
			boolean identity) throws SQLException {
		SqlParameterCollection sqlParameters = getSqlNode().eval(obj, params -> {
			params.setSqlSignature(sqlSignature);
			params.setSqlType(getSqlNode().getSqlType());
			params.setSqlHandler(sqlHandler);
		});
		if (sqlNode.getSqlType() == SqlType.INSERT) {
			if (identity) {
				sqlParameters.setGeneratedKey(GeneratedKey.RETURN_GENERATED_KEYS);
			}
		}
		final int columnSize = caluculateParameterCount(sqlSignature);
		final PreparedStatement statement = sqlParameters.createStatement(connection);
		setSqlParameters(columnSize, rowSize, sqlParameters, statement);
		sqlParameters.setBind(statement);
		return statement;
	}

	public PreparedStatement createStatement(Connection connection, int columnSize, int rowSize, Object obj,
			boolean identity) throws SQLException {
		SqlParameterCollection sqlParameters = getSqlNode().eval(obj);
		sqlParameters.setSqlType(getSqlNode().getSqlType());
		sqlParameters.setSqlHandler(sqlHandler);
		if (sqlNode.getSqlType() == SqlType.INSERT) {
			if (identity) {
				sqlParameters.setGeneratedKey(GeneratedKey.RETURN_GENERATED_KEYS);
			}
		}
		final PreparedStatement statement = sqlParameters.createStatement(connection);
		setSqlParameters(columnSize, rowSize, sqlParameters, statement);
		sqlParameters.setBind(statement);
		return statement;
	}

	private int caluculateParameterCount(SqlSignature sqlSignature) {
		if (sqlSignature.getSelectedColumnsHolder() != null) {
			return sqlSignature.getSelectedColumnsHolder().getKeyColumns().size();
		}
		return 1;
	}

	public PreparedStatement getStatement(int columnSize, int rowSize, Object obj) throws SQLException {
		final SqlParameterCollection sqlParameters = getSqlParameters(columnSize, rowSize);
		if (sqlParameters == null) {
			return null;
		}
		final PreparedStatement statement = getPreparedStatement(columnSize, rowSize);
		getSqlNode().reEval(obj, sqlParameters);
		sqlParameters.setBind(statement);
		return statement;
	}

	public PreparedStatement getStatement(SqlSignature sqlSignature, List<?> rows) throws SQLException {
		final int columnSize = caluculateParameterCount(sqlSignature);
		return getStatement(columnSize, rows.size(), rows);
	}

	public PreparedStatement getStatement(SqlSignature sqlSignature, int rowSize, Object obj) throws SQLException {
		final int columnSize = caluculateParameterCount(sqlSignature);
		return getStatement(columnSize, rowSize, obj);
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
