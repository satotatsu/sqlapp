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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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

	// Scoped per thread and restored for reentrant execution from row callbacks.
	private final ThreadLocal<QueryCreation> queryCreation = new ThreadLocal<>();

	private static final class QueryCreation {
		final Object context;
		final SqlParameterCollection parameters;
		boolean closed;

		QueryCreation(Object context, SqlParameterCollection parameters) {
			this.context = context;
			this.parameters = parameters;
		}
	}

	@Override
	protected SqlParameterCollection createSqlParameterCollection(Object context) {
		QueryCreation creation = queryCreation.get();
		return creation != null && creation.context == context ? creation.parameters
				: super.createSqlParameterCollection(context);
	}

	@Override
	protected void close(PreparedStatement statement, SqlParameterCollection parameters) {
		QueryCreation creation = queryCreation.get();
		if (creation != null && creation.parameters == parameters) {
			if (creation.closed) {
				super.close(statement, null);
				return;
			}
			creation.closed = true;
		}
		super.close(statement, parameters);
	}

	public JdbcQueryHandler(final SqlNode node, final ResultSetNextHandler resultSetNextHandler) {
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
	public void execute(final Connection connection, final ParametersContext parametersContext, final Integer limit,
			final Integer offset) throws SQLException {
		try {
			executeQueryCallback(connection, parametersContext, limit, offset);
		} catch (final SQLException e) {
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
	public void execute(final Connection connection, final SqlParameterCollection sqlParameters, final Integer limit,
			final Integer offset) throws SQLException {
		try {
			executeQueryCallback(connection, sqlParameters, limit, offset);
		} catch (final SQLException e) {
			handleSqlException(e);
		}
	}

	/**
	 * <code>java.sql.PreparedStatement#executeQuery()</code>を実行した結果を扱います。
	 * <code>handleResultSet(ResultSet resultSet)</code>もしくは<code>handleResultSetNext(ResultSet resultSet)</code>
	 * を継承して使用します。
	 * 
	 * @param connection
	 * @param sqlParameters
	 * @param limit
	 * @param offset
	 * @throws SQLException
	 */
	protected void executeQueryCallback(final Connection connection, final SqlParameterCollection sqlParameters,
			final Integer limit, final Integer offset) throws SQLException {
		PreparedStatement statement = null;
		try {
			boolean nativeOffset = hasNativeParameter(sqlParameters, ParameterDefinition.OFFSET_KEY_PARANETER_NAME);
			boolean nativeLimit = hasNativeParameter(sqlParameters, ParameterDefinition.ROW_KEY_PARANETER_NAME);
			if (offset != null && !nativeOffset) {
				statement = createStatementScrollInsensitive(connection, sqlParameters);
			} else {
				statement = createStatement(connection, sqlParameters, limit);
			}
			executeQueryCallback(connection, statement, nativeLimit ? null : limit, nativeOffset ? null : offset);
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
	protected StatementSqlParametersHolder createStatementScrollInsensitive(final Connection connection,
			final ParametersContext parametersContext) throws SQLException {
		final SqlParameterCollection sqlParameters = createSqlParameterCollection(parametersContext);
		try {
			final PreparedStatement preparedStatement = createStatementScrollInsensitive(connection, sqlParameters);
			return new StatementSqlParametersHolder(preparedStatement, sqlParameters);
		} catch (SQLException | RuntimeException | Error failure) {
			close(null, sqlParameters);
			throw failure;
		}
	}

	/**
	 * スクロール可能なPreparedStatementを作成します
	 * 
	 * @param connection
	 * @param sqlParameters
	 */
	protected PreparedStatement createStatementScrollInsensitive(final Connection connection,
			final SqlParameterCollection sqlParameters) throws SQLException {
		final PreparedStatement statement = connection.prepareStatement(sqlParameters.getSql(),
				ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
		try {
			setBind(statement, sqlParameters);
			return statement;
		} catch (SQLException | RuntimeException | Error failure) {
			close(statement, null);
			throw failure;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public JdbcQueryHandler execute(final Connection connection, final ParametersContext parametersContext) {
		try {
			executeQueryCallback(connection, parametersContext, null, null);
			return this;
		} catch (final SQLException e) {
			handleSqlException(e);
			return this;
		}
	}

	public JdbcQueryHandler execute(final Connection connection) {
		execute(connection, new ParametersContext());
		return this;
	}

	/**
	 * <code>java.sql.PreparedStatement#executeQuery()</code>を実行した結果を扱います。
	 * <code>handleResultSet(ResultSet resultSet)</code>もしくは<code>handleResultSetNext(ResultSet resultSet)</code>
	 * を継承して使用します。
	 * 
	 * @param connection
	 * @param parametersContext
	 * @param limit
	 * @param offset
	 * @throws SQLException
	 */
	protected void executeQueryCallback(final Connection connection, final ParametersContext parametersContext,
			final Integer limit, final Integer offset) throws SQLException {
		// Seed a copy before expansion so includes can bind explicit paging values.
		// Do not add reserved keys to the caller's context for client-side paging.
		ParametersContext evaluationContext = parametersContext.clone();
		if (limit != null) {
			evaluationContext.put(ParameterDefinition.ROW_KEY_PARANETER_NAME, limit);
		}
		if (offset != null) {
			evaluationContext.put(ParameterDefinition.OFFSET_KEY_PARANETER_NAME, offset);
		}
		SqlParameterCollection parameters = createSqlParameterCollection(evaluationContext);
		if (limit != null && hasNativeParameter(parameters, ParameterDefinition.ROW_KEY_PARANETER_NAME)) {
			parametersContext.put(ParameterDefinition.ROW_KEY_PARANETER_NAME, limit);
		}
		if (offset != null && hasNativeParameter(parameters, ParameterDefinition.OFFSET_KEY_PARANETER_NAME)) {
			parametersContext.put(ParameterDefinition.OFFSET_KEY_PARANETER_NAME, offset);
		}
		QueryCreation previous = queryCreation.get();
		QueryCreation creation = new QueryCreation(parametersContext, parameters);
		queryCreation.set(creation);
		StatementSqlParametersHolder holder = null;
		try {
			if (offset != null && !hasNativeParameter(parameters, ParameterDefinition.OFFSET_KEY_PARANETER_NAME)) {
				holder = createStatementScrollInsensitive(connection, parametersContext);
			} else {
				holder = createStatement(connection, parametersContext, limit);
			}
			SqlParameterCollection actual = holder.getSqlParameters();
			executeQueryCallback(connection, holder.getPreparedStatement(),
					hasNativeParameter(actual, ParameterDefinition.ROW_KEY_PARANETER_NAME) ? null : limit,
					hasNativeParameter(actual, ParameterDefinition.OFFSET_KEY_PARANETER_NAME) ? null : offset);
		} finally {
			try {
				close(holder);
				if (!creation.closed) {
					closeUnusedEvaluation(parameters, holder == null ? null : holder.getSqlParameters());
				}
			} finally {
				if (previous == null) {
					queryCreation.remove();
				} else {
					queryCreation.set(previous);
				}
			}
		}
	}

	// A legacy factory override may evaluate its own parameter collection. Close
	// evaluation-only resources without closing values also owned by that holder.
	private void closeUnusedEvaluation(SqlParameterCollection evaluated, SqlParameterCollection used) {
		if (used == null) {
			close(null, evaluated);
			return;
		}
		Set<Object> shared = Collections.newSetFromMap(new IdentityHashMap<>());
		used.getBindParameters().stream().flatMap(BindParameterHolder::stream)
				.forEach(parameter -> shared.add(parameter.getValue()));
		shared.add(used.getInputStream());
		shared.add(used.getOutputStream());
		SqlParameterCollection unused = evaluated.clone();
		unused.getBindParameters().stream().flatMap(BindParameterHolder::stream).forEach(parameter -> {
			if (shared.contains(parameter.getValue())) {
				parameter.setValue(null);
			}
		});
		if (shared.contains(unused.getInputStream())) unused.setInputStream(null);
		if (shared.contains(unused.getOutputStream())) unused.setOutputStream(null);
		close(null, unused);
	}

	private boolean hasNativeParameter(SqlParameterCollection parameters, String name) {
		if (parameters.hasEvaluatedParameter(name)) {
			return true;
		}
		return parameters.getBindParameters().stream().flatMap(BindParameterHolder::stream)
				.anyMatch(parameter -> name.equals(parameter.getName()));
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
	protected void executeQueryCallback(final Connection connection, final PreparedStatement statement,
			final Integer limit, final Integer offset) throws SQLException {
		ExResultSet resultSet = null;
		try {
			if (fetchSize != null) {
				statement.setFetchSize(fetchSize.intValue());
			}
			resultSet = new ExResultSet(statement.executeQuery());
			if (offset != null) {
				resultSet.relative(offset.intValue());
			}
			if (limit != null) {
				handleResultSet(resultSet, limit.intValue());
			} else {
				handleResultSet(resultSet);
			}
		} finally {
			close(resultSet);
		}
	}

	protected void handleResultSet(final ExResultSet resultSet, final int limit) throws SQLException {
		long count = 0;
		long start = System.currentTimeMillis();
		try {
			while (count < limit && resultSet.next()) {
				handleResultSetNext(resultSet);
				count++;
			}
		} finally {
			recordFetchResult(count, System.currentTimeMillis() - start);
		}
	}

	@Override
	protected void handleResultSetNext(final ExResultSet resultSet) throws SQLException {
		resultSetNextHandler.handleResultSetNext(resultSet);
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
	public void setFetchSize(final Integer fetchSize) {
		this.fetchSize = fetchSize;
	}
}
