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
import java.time.LocalDateTime;
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

	private static class StatementHolder {
		private final SqlNode sqlNode;
		private PreparedStatement statement;
		private SqlParameterCollection sqlParameters;
		private BatchExecResult batchExecResult;

		StatementHolder(SqlNode sqlNode) {
			this.sqlNode = sqlNode;
		}

		public void close() {
			try {
				if (statement != null) {
					statement.close();
				}
			} catch (SQLException e) {
			}
		}
	}

	public static class BatchExecResult {
		private final SqlNode sqlNode;
		private final PreparedStatement statement;
		private LocalDateTime start = LocalDateTime.now();
		private LocalDateTime end;
		private long lastRowIndex;
		private long startTimeMillis = System.currentTimeMillis();
		private long endTimeMillis;

		private int[] result;
		private List<ValueHolder> values;
		private List<GeneratedKeyInfo> generatedKeys;

		private BatchExecResult(StatementHolder holder, int batchSize) {
			this.sqlNode = holder.sqlNode;
			this.statement = holder.statement;
			values = CommonUtils.list(batchSize);
		}

		private void setEnd(long lastRowIndex, int[] result, List<GeneratedKeyInfo> generatedKeys) {
			this.setLastRowIndex(lastRowIndex);
			this.result = result;
			this.generatedKeys = generatedKeys;
			this.endTimeMillis = System.currentTimeMillis();
			this.end = LocalDateTime.now();
		}

		public int[] getResult() {
			return result;
		}

		public List<GeneratedKeyInfo> getGeneratedKeys() {
			return generatedKeys;
		}

		public List<ValueHolder> getValues() {
			return values;
		}

		public void setValues(List<ValueHolder> values) {
			this.values = values;
		}

		public long getLastRowIndex() {
			return lastRowIndex;
		}

		private void setLastRowIndex(long counter) {
			this.lastRowIndex = counter;
		}

		public void setStart(LocalDateTime start) {
			this.start = start;
		}

		public long getMillis() {
			return endTimeMillis - startTimeMillis;
		}

		public LocalDateTime getStart() {
			return start;
		}

		public LocalDateTime getEnd() {
			return end;
		}

		public SqlNode getSqlNode() {
			return sqlNode;
		}

		public PreparedStatement getStatement() {
			return statement;
		}
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
		for (ValueHolder obj : values) {
			if (holder.batchExecResult == null) {
				holder.batchExecResult = new BatchExecResult(holder, batchSize);
			}
			holder.batchExecResult.getValues().add(obj);
			if (holder.sqlParameters == null) {
				holder.sqlParameters = holder.sqlNode.eval(obj.converted());
				final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection, holder.sqlParameters);
				holder.statement = statement;
				JdbcHandlerUtils.setBind(holder.statement, dialect, holder.sqlParameters);
			} else {
				holder.sqlNode.reEval(obj.converted, holder.sqlParameters);
				JdbcHandlerUtils.setBind(holder.statement, dialect, holder.sqlParameters);
			}
			holder.statement.addBatch();
		}
		final int[] ret = holder.statement.executeBatch();
		final List<GeneratedKeyInfo> keys;
		if (holder.sqlParameters.getGeneratedKey() == GeneratedKey.RETURN_GENERATED_KEYS) {
			keys = JdbcHandlerUtils.getGeneratedKeys(holder.statement, dialect);
		} else {
			keys = Collections.emptyList();
		}
		holder.statement.clearBatch();
		holder.batchExecResult.setEnd(index, ret, keys);
		handleBatchResult(holder);
		holder.batchExecResult = new BatchExecResult(holder, batchSize);
	}

	static class CommitCountHolder {
		private long commitHandleCount;
		private long commitCount;
		private final long commitSize;
		private final SQLConsumer<Connection> commitHandler;

		CommitCountHolder(final long commitSize, SQLConsumer<Connection> commitHandler) {
			this.commitSize = commitSize;
			this.commitHandler = commitHandler;
			commitHandleCount = 0;
			commitCount = 0;
		}

		public long getCommitCount() {
			return commitCount;
		}

		public void countUp() {
			commitHandleCount++;
		}

		public void commit(final Connection connection) throws SQLException {
			if (commitHandleCount + 1 >= commitSize) {
				commitInternal(connection);
				commitHandleCount = 0;
				commitCount++;
				return;
			}
			countUp();
		}

		private void commitInternal(final Connection connection) throws SQLException {
			if (commitHandler != null) {
				commitHandler.accept(connection);
			}
		}

		public void finalCommit(final Connection connection) throws SQLException {
			if (commitCount > 0 && commitHandleCount > 0) {
				commitInternal(connection);
				commitHandleCount = 0;
				commitCount++;
				return;
			}
		}

	}

	private long handle(final Connection connection, final List<StatementHolder> holders, final Dialect dialect,
			final Iterable<?> itr) throws SQLException {
		long i = 0;
		final CommitCountHolder commitCountHandler = new CommitCountHolder(this.commitSize, this.commitHandler);
		for (final Object obj : itr) {
			final ValueHolder valueHolder = new ValueHolder(obj, this.valueConverter.apply(obj));
			for (final StatementHolder holder : holders) {
				if (holder.sqlParameters == null) {
					holder.sqlParameters = holder.sqlNode.eval(valueHolder.converted());
					final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection, holder.sqlParameters);
					holder.statement = statement;
				} else {
					holder.sqlParameters = holder.sqlNode.eval(obj);
				}
				holder.batchExecResult = new BatchExecResult(holder, batchSize);
				holder.batchExecResult.getValues().add(valueHolder);
				JdbcHandlerUtils.setBind(holder.statement, dialect, holder.sqlParameters);
				int ret = holder.statement.executeUpdate();
				int[] retArray = new int[1];
				retArray[0] = ret;
				final List<GeneratedKeyInfo> keys;
				if (holder.sqlParameters.getGeneratedKey() == GeneratedKey.RETURN_GENERATED_KEYS) {
					keys = JdbcHandlerUtils.getGeneratedKeys(holder.statement, dialect);
				} else {
					keys = Collections.emptyList();
				}
				holder.batchExecResult.setEnd(i, retArray, keys);
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
		batchUpdateResultHandler.accept(holder.batchExecResult);
	}
}
