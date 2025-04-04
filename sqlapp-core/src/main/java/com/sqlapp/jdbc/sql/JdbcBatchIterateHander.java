package com.sqlapp.jdbc.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

/**
 * IterableなデータでBatch更新をするためのクラス
 */
public class JdbcBatchIterateHander {

	private Collection<SqlNode> sqlNodes;

	private int batchSize = 50;

	private long commitSize = Integer.MAX_VALUE;

	private final Iterable<?> itr;

	private GeneratedKeyHandler generatedKeyHandler;

	private Consumer<BatchExecResult> batchUpdateResultHandler;

	private Consumer<ExecResult> updateResultHandler;

	private CommitHandler commitHandler = conn -> conn.commit();

	private CommitHandler rollbackHandler;

	public void setCommitHandler(CommitHandler commitHandler) {
		this.commitHandler = commitHandler;
	}

	public CommitHandler getRollbackHandler() {
		return rollbackHandler;
	}

	public void setRollbackHandler(CommitHandler rollbackHandler) {
		this.rollbackHandler = rollbackHandler;
	}

	@FunctionalInterface
	public static interface CommitHandler {
		void apply(Connection connection) throws SQLException;
	}

	@FunctionalInterface
	public static interface UpdateResult {
		void apply(SqlNode sqlNode, long index, int updateCount);
	}

	public GeneratedKeyHandler getGeneratedKeyHandler() {
		return generatedKeyHandler;
	}

	public void setGeneratedKeyHandler(GeneratedKeyHandler generatedKeyHandler) {
		this.generatedKeyHandler = generatedKeyHandler;
	}

	public void setBatchUpdateResultHandler(Consumer<BatchExecResult> batchUpdateResultHandler) {
		this.batchUpdateResultHandler = batchUpdateResultHandler;
	}

	public void setUpdateResultHandler(Consumer<ExecResult> updateResultHandler) {
		this.updateResultHandler = updateResultHandler;
	}

	public JdbcBatchIterateHander(SqlNode sqlNode, int batchSize, Iterable<?> itr) {
		this.sqlNodes = List.of(sqlNode);
		this.batchSize = batchSize;
		this.itr = itr;
	}

	public JdbcBatchIterateHander(Collection<SqlNode> sqlNodes, int batchSize, long commitSize, Iterable<?> itr) {
		this.sqlNodes = sqlNodes;
		this.batchSize = batchSize;
		this.commitSize = commitSize;
		this.itr = itr;
	}

	private static class StatementHolder {
		public final SqlNode sqlNode;
		public PreparedStatement statement;
		public SqlParameterCollection sqlParameters;
		ExecResult execResult;
		BatchExecResult batchExecResult;

		StatementHolder(SqlNode sqlNode) {
			this.sqlNode = sqlNode;
		}

		public void close() {
			try {
				statement.close();
			} catch (SQLException e) {
			}
		}
	}

	public static abstract class AbstractExecResult {
		public long getCounter() {
			return counter;
		}

		protected void setCounter(long counter) {
			this.counter = counter;
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

		protected void setEnd() {
			endTimeMillis = System.currentTimeMillis();
			this.end = LocalDateTime.now();
		}

		public SqlNode getSqlNode() {
			return sqlNode;
		}

		public PreparedStatement getStatement() {
			return statement;
		}

		private final SqlNode sqlNode;
		private final PreparedStatement statement;
		private LocalDateTime start = LocalDateTime.now();
		private LocalDateTime end;
		private long counter;
		private long startTimeMillis = System.currentTimeMillis();
		private long endTimeMillis;

		AbstractExecResult(StatementHolder holder) {
			this.sqlNode = holder.sqlNode;
			this.statement = holder.statement;
		}
	}

	public static class ExecResult extends AbstractExecResult {
		protected void setEnd(long counter, long result) {
			this.result = result;
			this.setCounter(counter);
			super.setEnd();
		}

		public long getResult() {
			return result;
		}

		private long result;

		ExecResult(StatementHolder holder) {
			super(holder);
		}
	}

	public static class BatchExecResult extends AbstractExecResult {
		protected void setEnd(long counter, int[] result, List<GeneratedKeyInfo> generatedKeys) {
			this.setCounter(counter);
			this.result = result;
			this.generatedKeys = generatedKeys;
			super.setEnd();
		}

		public int[] getResult() {
			return result;
		}

		public List<GeneratedKeyInfo> getGeneratedKeys() {
			return generatedKeys;
		}

		private int[] result;

		private List<Object> values;

		private List<GeneratedKeyInfo> generatedKeys;

		public List<Object> getValues() {
			return values;
		}

		public void setValues(List<Object> values) {
			this.values = values;
		}

		BatchExecResult(StatementHolder holder, int batchSize) {
			super(holder);
			values = CommonUtils.list(batchSize);
		}
	}

	/**
	 * Iteratorの内容でバッチ実行します
	 * 
	 * @param connection Connection
	 * @throws SQLException
	 */
	public void execute(final Connection connection) throws SQLException {
		Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final List<StatementHolder> holders = CommonUtils.list();
		try {
			for (SqlNode sqlNode : sqlNodes) {
				StatementHolder holder = new StatementHolder(sqlNode);
				holders.add(holder);
			}
			if (batchSize > 1) {
				handleAsBatch(connection, holders, dialect);
			} else {
				handle(connection, holders, dialect);
			}
		} catch (SQLException e) {
			for (final StatementHolder holder : holders) {
				holder.close();
			}
			if (rollbackHandler != null) {
				rollbackHandler.apply(connection);
			}
			throw e;
		}
	}

	private void handleAsBatch(final Connection connection, final List<StatementHolder> holders, final Dialect dialect)
			throws SQLException {
		long i = 0;
		long commitCount = 0;
		final List<Object> values = CommonUtils.list(this.batchSize);
		for (Object obj : itr) {
			values.add(obj);
			if (values.size() >= this.batchSize) {
				for (final StatementHolder holder : holders) {
					commitCount = handleStatementHolder(connection, i, commitCount, dialect, holder, values, false);
				}
				values.clear();
			}
			i++;
		}
		if (values.size() > 0) {
			for (StatementHolder holder : holders) {
				commitCount = handleStatementHolder(connection, i, commitCount, dialect, holder, values, true);
			}
		}
	}

	private long handleStatementHolder(final Connection connection, long index, long commitCount, final Dialect dialect,
			final StatementHolder holder, final List<Object> values, boolean forceCommit) throws SQLException {
		for (Object obj : values) {
			if (holder.batchExecResult == null) {
				holder.batchExecResult = new BatchExecResult(holder, batchSize);
			}
			holder.batchExecResult.getValues().add(obj);
			if (holder.sqlParameters == null) {
				holder.sqlParameters = holder.sqlNode.eval(obj);
				final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection, holder.sqlParameters);
				holder.statement = statement;
				JdbcHandlerUtils.setBind(holder.statement, dialect, holder.sqlParameters);
			} else {
				holder.sqlNode.reEval(obj, holder.sqlParameters);
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
		if (forceCommit) {
			commit(connection);
			return 0;
		} else {
			return commit(connection, commitCount);
		}
	}

	private long commit(final Connection connection, final long queryCount) throws SQLException {
		if (queryCount + 1 >= commitSize) {
			commit(connection);
			return 0;
		}
		return queryCount + 1;
	}

	private void commit(final Connection connection) throws SQLException {
		if (commitHandler != null) {
			commitHandler.apply(connection);
		}
	}

	private void handle(final Connection connection, final List<StatementHolder> holders, final Dialect dialect)
			throws SQLException {
		long queryCount = 0;
		long i = 0;
		for (final Object obj : itr) {
			for (final StatementHolder holder : holders) {
				if (holder.sqlParameters == null) {
					holder.sqlParameters = holder.sqlNode.eval(obj);
					final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection, holder.sqlParameters);
					holder.statement = statement;
				} else {
					holder.sqlParameters = holder.sqlNode.eval(obj);
				}
				holder.execResult = new ExecResult(holder);
				JdbcHandlerUtils.setBind(holder.statement, dialect, holder.sqlParameters);
				long ret = holder.statement.executeLargeUpdate();
				JdbcHandlerUtils.handleGeneratedKeys(holder.statement, generatedKeyHandler, dialect);
				queryCount = commit(connection, queryCount);
				holder.execResult.setEnd(i, ret);
				if (updateResultHandler != null) {
					updateResultHandler.accept(holder.execResult);
				}
			}
			i++;
		}
	}

	private void handleBatchResult(StatementHolder holder) throws SQLException {
		if (batchUpdateResultHandler == null) {
			return;
		}
		batchUpdateResultHandler.accept(holder.batchExecResult);
	}
}
