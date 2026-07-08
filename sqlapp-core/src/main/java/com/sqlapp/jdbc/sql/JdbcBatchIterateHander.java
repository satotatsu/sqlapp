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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

/**
 * IterableなデータでBatch更新をするためのクラス
 */
public class JdbcBatchIterateHander {

	private Collection<SqlNode> sqlNodes;

	private int batchSize = 500;

	private long commitSize = Integer.MAX_VALUE;

	private Consumer<BatchExecResult> batchUpdateResultHandler;

	private SQLConsumer<Connection> commitHandler = conn -> conn.commit();

	private SQLConsumer<Connection> rollbackHandler;

	private Function<Object, Object> valueConverter = o -> o;

	public static record ValueHolder(Object value, Object converted) {
	}

	/**
	 * コミットハンドラーを設定します
	 * 
	 * @param commitHandler CommitHandler
	 */
	public void setCommitHandler(SQLConsumer<Connection> commitHandler) {
		this.commitHandler = commitHandler;
	}

	/**
	 * @param valueConverter the valueConverter to set
	 */
	public void setValueConverter(Function<Object, Object> valueConverter) {
		this.valueConverter = valueConverter;
	}

	/**
	 * ロールバックハンドラーを設定します
	 * 
	 * @param rollbackHandler CommitHandler
	 */
	public void setRollbackHandler(SQLConsumer<Connection> rollbackHandler) {
		this.rollbackHandler = rollbackHandler;
	}

	public void setBatchUpdateResultHandler(Consumer<BatchExecResult> batchUpdateResultHandler) {
		this.batchUpdateResultHandler = batchUpdateResultHandler;
	}

	public JdbcBatchIterateHander(SqlNode sqlNode, int batchSize) {
		this.sqlNodes = List.of(sqlNode);
		this.batchSize = batchSize;
	}

	public JdbcBatchIterateHander(Collection<SqlNode> sqlNodes, int batchSize, long commitSize) {
		this.sqlNodes = sqlNodes;
		this.batchSize = batchSize;
		this.commitSize = commitSize;
	}

	/**
	 * Iteratorの内容でバッチ実行します
	 * 
	 * @param connection Connection
	 * @throws SQLException
	 */
	public long execute(final Connection connection, final Iterable<?> itr) throws SQLException {
		Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final List<StatementHolder> holders = CommonUtils.list();
		try {
			for (SqlNode sqlNode : sqlNodes) {
				StatementHolder holder = new StatementHolder(sqlNode);
				holders.add(holder);
			}
			if (batchSize > 1) {
				return handleAsBatch(connection, holders, dialect, itr);
			} else {
				return handle(connection, holders, dialect, itr);
			}
		} catch (SQLException e) {
			if (rollbackHandler != null) {
				rollbackHandler.accept(connection);
			}
			throw e;
		} finally {
			for (final StatementHolder holder : holders) {
				holder.close();
			}
		}
	}

	private long handleAsBatch(final Connection connection, final List<StatementHolder> holders, final Dialect dialect,
			final Iterable<?> itr) throws SQLException {
		long i = 0;
		final List<ValueHolder> values = CommonUtils.list(this.batchSize);
		final CommitCountHolder commitCountHandler = new CommitCountHolder(this.commitSize, this.commitHandler);
		for (Object obj : itr) {
			final ValueHolder valueHolder = new ValueHolder(obj, this.valueConverter.apply(obj));
			values.add(valueHolder);
			if (values.size() >= this.batchSize) {
				for (final StatementHolder holder : holders) {
					handleStatementHolder(connection, i, dialect, holder, values);
				}
				commitCountHandler.commit(connection);
				values.clear();
			}
			i++;
		}
		if (values.size() > 0) {
			for (StatementHolder holder : holders) {
				handleStatementHolder(connection, i - 1, dialect, holder, values);
			}
			commitCountHandler.commit(connection);
		}
		commitCountHandler.finalCommit(connection);
		return i;
	}

	private void handleStatementHolder(final Connection connection, long index, final Dialect dialect,
			final StatementHolder holder, final List<ValueHolder> values) throws SQLException {
		SqlParameterCollection sqlParameters = null;
		PreparedStatement statement = null;
		int size = values.size();
		for (ValueHolder obj : values) {
			if (holder.getSqlParameters(size) == null) {
				sqlParameters = holder.getSqlNode().eval(obj.converted);
				statement = sqlParameters.createStatement(connection);
				holder.setSqlParameters(size, sqlParameters, statement);
				sqlParameters.setBind(statement);
			} else {
				sqlParameters = holder.getSqlParameters(size);
				statement = holder.getPreparedStatement(size);
				holder.getSqlNode().reEval(obj.converted, sqlParameters);
				sqlParameters.setBind(statement);
			}
			if (holder.getBatchExecResult() == null) {
				holder.setBatchExecResult(new BatchExecResult(holder.getSqlNode(), statement, size));
			}
			holder.getBatchExecResult().getValues().add(obj);
			statement.addBatch();
		}
		final int[] ret = statement.executeBatch();
		final List<GeneratedKeyInfo> keys;
		if (sqlParameters.getGeneratedKey() == GeneratedKey.RETURN_GENERATED_KEYS) {
			keys = JdbcHandlerUtils.getGeneratedKeys(statement, dialect);
		} else {
			keys = Collections.emptyList();
		}
		statement.clearBatch();
		holder.getBatchExecResult().setEnd(index, ret, keys);
		handleBatchResult(holder);
		holder.setBatchExecResult(new BatchExecResult(holder.getSqlNode(), statement, values.size()));
	}

	private long handle(final Connection connection, final List<StatementHolder> holders, final Dialect dialect,
			final Iterable<?> itr) throws SQLException {
		long i = 0;
		final CommitCountHolder commitCountHandler = new CommitCountHolder(this.commitSize, this.commitHandler);
		SqlParameterCollection sqlParameters = null;
		PreparedStatement statement = null;
		int size = 1;
		for (final Object obj : itr) {
			final ValueHolder valueHolder = new ValueHolder(obj, this.valueConverter.apply(obj));
			for (final StatementHolder holder : holders) {
				if (holder.getSqlParameters(size) == null) {
					sqlParameters = holder.getSqlNode().eval(valueHolder.converted());
					statement = JdbcHandlerUtils.getStatement(connection, sqlParameters);
					holder.setSqlParameters(size, sqlParameters, statement);
				} else {
					sqlParameters = holder.getSqlParameters(size);
					statement = holder.getPreparedStatement(size);
					holder.getSqlNode().reEval(valueHolder.converted(), sqlParameters);
				}
				holder.setBatchExecResult(new BatchExecResult(holder.getSqlNode(), statement, batchSize));
				holder.getBatchExecResult().getValues().add(valueHolder);
				JdbcHandlerUtils.setBind(statement, dialect, sqlParameters);
				int ret = statement.executeUpdate();
				int[] retArray = new int[1];
				retArray[0] = ret;
				final List<GeneratedKeyInfo> keys;
				if (sqlParameters.getGeneratedKey() == GeneratedKey.RETURN_GENERATED_KEYS) {
					keys = JdbcHandlerUtils.getGeneratedKeys(statement, dialect);
				} else {
					keys = Collections.emptyList();
				}
				holder.getBatchExecResult().setEnd(i, retArray, keys);
				handleBatchResult(holder);
				commitCountHandler.commit(connection);
			}
			i++;
		}
		commitCountHandler.finalCommit(connection);
		return i;
	}

	private void handleBatchResult(StatementHolder holder) throws SQLException {
		if (batchUpdateResultHandler == null) {
			return;
		}
		batchUpdateResultHandler.accept(holder.getBatchExecResult());
	}
}
