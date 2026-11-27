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
import java.util.Objects;

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

	// Only the requested paging values are scoped; SQL is evaluated by the
	// selected legacy factory, after that factory has adjusted the context.
	private final ThreadLocal<PagingRequest> pagingRequest = new ThreadLocal<>();

	private static final class PagingRequest {
		final Object context;
		final Integer limit;
		final Integer offset;

		PagingRequest(Object context, Integer limit, Integer offset) {
			this.context = context;
			this.limit = limit;
			this.offset = offset;
		}
	}

	@Override
	protected PreparedStatement createStatement(Connection connection, SqlParameterCollection parameters, Integer limit)
			throws SQLException {
		PagingRequest request = pagingRequest.get();
		if (request != null && request.offset != null
				&& !hasNativeParameter(parameters, ParameterDefinition.OFFSET_KEY_PARANETER_NAME)) {
			return createStatementScrollInsensitive(connection, parameters);
		}
		return super.createStatement(connection, parameters, limit);
	}

	private void restoreRequest(PagingRequest previous) {
		if (previous == null) pagingRequest.remove();
		else pagingRequest.set(previous);
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
			PagingRequest previous = pagingRequest.get();
			pagingRequest.set(new PagingRequest(sqlParameters, limit, offset));
			try {
				if (offset != null && !nativeOffset) {
					statement = createStatementScrollInsensitive(connection, sqlParameters);
				} else {
					statement = createStatement(connection, sqlParameters, limit);
				}
			} finally {
				restoreRequest(previous);
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
			PagingRequest request = pagingRequest.get();
			final PreparedStatement preparedStatement = request != null && request.context == parametersContext
					? createStatement(connection, sqlParameters, request.limit)
					: createStatementScrollInsensitive(connection, sqlParameters);
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
		String rowName = ParameterDefinition.ROW_KEY_PARANETER_NAME;
		String offsetName = ParameterDefinition.OFFSET_KEY_PARANETER_NAME;
		boolean hadRow = parametersContext.containsKeyInternal(rowName);
		boolean hadOffset = parametersContext.containsKeyInternal(offsetName);
		Object previousRow = hadRow ? parametersContext.get(rowName) : null;
		Object previousOffset = hadOffset ? parametersContext.get(offsetName) : null;
		PagingRequest previous = pagingRequest.get();
		StatementSqlParametersHolder holder = null;
		try {
			pagingRequest.set(new PagingRequest(parametersContext, limit, offset));
			try {
				if (limit != null) parametersContext.put(rowName, limit);
				if (offset != null) parametersContext.put(offsetName, offset);
				// Preserve which context hook existing subclasses override. The
				// default factory selects the real statement type after evaluation.
				if (offset != null && getNode().getOffsetParameter() == null) {
					holder = createStatementScrollInsensitive(connection, parametersContext);
				} else {
					holder = createStatement(connection, parametersContext, limit);
				}
			} finally {
				restoreRequest(previous);
				SqlParameterCollection actual = holder == null ? null : holder.getSqlParameters();
				restoreUnusedPagingValue(parametersContext, rowName, limit, hadRow, previousRow,
						actual != null && hasNativeParameter(actual, rowName));
				restoreUnusedPagingValue(parametersContext, offsetName, offset, hadOffset, previousOffset,
						actual != null && hasNativeParameter(actual, offsetName));
			}
			SqlParameterCollection actual = holder.getSqlParameters();
			executeQueryCallback(connection, holder.getPreparedStatement(),
					hasNativeParameter(actual, rowName) ? null : limit,
					hasNativeParameter(actual, offsetName) ? null : offset);
		} finally {
			close(holder);
		}
	}

	private void restoreUnusedPagingValue(ParametersContext context, String name, Integer supplied,
			boolean wasPresent, Object previous, boolean nativePaging) {
		// Retain explicit writes made by a subclass; remove only our temporary seed.
		if (supplied != null && !nativePaging && Objects.equals(context.get(name), supplied)) {
			if (wasPresent) context.put(name, previous);
			else context.remove(name);
		}
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
