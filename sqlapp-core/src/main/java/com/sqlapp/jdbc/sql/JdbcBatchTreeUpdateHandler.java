/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.ColumnSelectionStrategy;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.jdbc.sql.JdbcBatchIterateHander.ValueHolder;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DbUtils;
import com.sqlapp.util.DoubleKeyMap;

public class JdbcBatchTreeUpdateHandler implements AutoCloseable {

	private int rootBatchSize = 500;

	private boolean initialized = false;
	private final Dialect dialect;
	private final SqlFactoryRegistry sqlFactoryRegistry;
	private final Connection connection;
	private SQLConsumer<Connection> commitHandler = conn -> conn.commit();
	private final TableRelationTreeHolder tableRelationTreeHolder;
	private final CommitCountHolder commitCountHandler = new CommitCountHolder(Long.MAX_VALUE, this.commitHandler);

	private long batchUpdateCounter = 0;

	private BiConsumer<Long, Table> afterRootBatchHandler = (i, t) -> {
	};

	private BiConsumer<Long, Row> afterCommitEveryRootsHandler = (i, t) -> {
	};

	private BiConsumer<Long, Table> beforeRootBatchHandler = (i, t) -> {
	};

	private BiConsumer<Long, Row> beforeCommitEveryRootsHandler = (i, t) -> {
	};

	private Consumer<Row> newRowInitializer = r -> {
	};

	private Function<Table, TableUpdateMode> tableUpdateMode = t -> TableUpdateMode.INSERT;

	public void setBeforeRootBatchHandler(BiConsumer<Long, Table> beforeRootBatchHandler) {
		this.beforeRootBatchHandler = beforeRootBatchHandler;
	}

	public void setAfterRootBatchHandler(BiConsumer<Long, Table> afterRootBatchHandler) {
		this.afterRootBatchHandler = afterRootBatchHandler;
	}

	public void setBeforeCommitEveryRootsHandler(BiConsumer<Long, Row> beforeCommitEveryRootsHandler) {
		this.beforeCommitEveryRootsHandler = beforeCommitEveryRootsHandler;
	}

	public void setAfterCommitEveryRootsHandler(BiConsumer<Long, Row> afterCommitEveryRootsHandler) {
		this.afterCommitEveryRootsHandler = afterCommitEveryRootsHandler;
	}

	public void setTableUpdateMode(Function<Table, TableUpdateMode> tableUpdateMode) {
		this.tableUpdateMode = tableUpdateMode;
	}

	public void setTableUpdateMode(TableUpdateMode tableUpdateMode) {
		this.tableUpdateMode = (t) -> tableUpdateMode;
	}

	public JdbcBatchTreeUpdateHandler(Connection connection, TableRelationTreeHolder tableRelationTreeHolder) {
		this.connection = connection;
		this.dialect = DialectResolver.getInstance().getDialect(connection);
		this.tableRelationTreeHolder = tableRelationTreeHolder;
		this.sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
	}

	public void setNewRowInitializer(Consumer<Row> newRowInitializer) {
		this.newRowInitializer = newRowInitializer;
	}

	public TableOptions getTableOptions() {
		return this.sqlFactoryRegistry.getTableOptions();
	}

	/**
	 * @return the rootBatchSize
	 */
	public int getRootBatchSize() {
		return rootBatchSize;
	}

	/**
	 * @param rootBatchSize the batchSize to set
	 */
	public void setRootBatchSize(int rootBatchSize) {
		this.rootBatchSize = rootBatchSize;
	}

	public void setCommitEveryRoots(long commitSize) {
		commitCountHandler.setCommitSize(commitSize);
	}

	public long getCommitEveryRoots() {
		return commitCountHandler.getCommitSize();
	}

	public Row newRow(Table table) throws SQLException {
		initialize();
		final TableRelation tableRelation = tableRelationTreeHolder.getTableRelation(table);
		if (tableRelation.isRoot()) {
			int tableRowSize = tableRelation.getTable().getRows().size();
			if (tableRowSize >= this.getRootBatchSize()) {
				executeUpdate(tableRelation);
			}
			final Row row = tableRelation.newRow();
			newRowInitializer.accept(row);
			return row;
		} else {
			final Row row = tableRelation.newRow();
			newRowInitializer.accept(row);
			return row;
		}
	}

	private void initialize() {
		if (initialized) {
			return;
		}
		doInitialize();
		initialized = true;
	}

	private void doInitialize() {
		for (TableRelation tableRelation : tableRelationTreeHolder) {
			Table table = tableRelation.getTable();
			TableUpdateMode type = tableUpdateMode.apply(table);
			for (SqlType sqlType : type.getSqlTypes()) {
				List<SqlNode> sqlNodes = sqlFactoryRegistry.createSqlNodes(tableRelation.getTable(), sqlType);
				for (SqlNode sqlNode : sqlNodes) {
					StatementHolder statementHolder = new StatementHolder(sqlNode);
					tableRelation.addStatementHolder(statementHolder);
				}
			}
		}
	}

	private void executeUpdate(final TableRelation tableRelation) throws SQLException {
		if (tableRelation.getTable().getRows().size() == 0) {
			return;
		}
		handleAsBatch(tableRelation, true);
	}

	private void handleAsBatch(final TableRelation tableRelation, boolean root) throws SQLException {
		final List<ValueHolder> values = CommonUtils.list(this.getRootBatchSize());
		Table table = tableRelation.getTable();
		if (root) {
			beforeRootBatchHandler.accept(this.batchUpdateCounter, table);
		}
		TableUpdateMode type = tableUpdateMode.apply(table);
		type.handleStatementHolder(connection, dialect, tableRelation, table.getRows(), this.getTableOptions());
		if (root) {
			this.batchUpdateCounter++;
			afterRootBatchHandler.accept(this.batchUpdateCounter, table);
		}
		values.clear();
		setRowValueToChildren(tableRelation);
		for (TableRelation childTableRelation : tableRelation.getChildren()) {
			handleAsBatch(childTableRelation, false);// 再帰的に子供をBATCH UPDATE
		}
		if (root) {
			Row row = CommonUtils.last(table.getRows());
			if (commitCountHandler.isCommit()) {
				beforeCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), row);
			}
			if (commitCountHandler.commit(connection)) {
				afterCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), row);
			}
			lastRowMap.put(table.getSchemaName(), table.getName(), row);
			clearRows(tableRelation);
		}
	}

	private final DoubleKeyMap<String, String, Row> lastRowMap = CommonUtils.doubleKeyMap();

	private void clearRows(final TableRelation tableRelation) {
		tableRelation.getTable().getRows().clear();
		for (TableRelation child : tableRelation.getChildren()) {
			clearRows(child);
		}
	}

	private void setRowValueToChildren(final TableRelation tableRelation) {
		for (TableRelation childTableRelation : tableRelation.getChildren()) {
			Table childTable = childTableRelation.getTable();
			for (Row childRow : childTable.getRows()) {
				childTableRelation.forEach((i, column, parentColumn) -> {
					Object value = childRow.getParentRow().get(parentColumn);
					childRow.put(column, value);
				});
			}
		}
	}

	private void handleStatementHolderForRows(final TableRelation tableRelation, SqlType sqlType) throws SQLException {
		StatementHolder holder = tableRelation.getStatementHolder(sqlType);
		Table table = tableRelation.getTable();
		int size = table.getRows().size();
		SqlParameterCollection sqlParameters = null;
		PreparedStatement statement = null;
		if (holder.getSqlParameters(size) == null) {
			sqlParameters = holder.getSqlNode().eval(table.getRows());
			sqlParameters.setFetchSize(table.getRows().size());
			statement = dialect.getCorrelationStrategy().createPreparedStatement(connection, sqlParameters);
			JdbcHandlerUtils.setStatementParameters(sqlParameters, statement);
			holder.setSqlParameters(size, sqlParameters, statement);
			sqlParameters.setBind(statement);
		} else {
			sqlParameters = holder.getSqlParameters(size);
			statement = holder.getPreparedStatement(size);
			holder.getSqlNode().reEval(table.getRows(), sqlParameters);
			sqlParameters.setBind(statement);
		}
		if (holder.getBatchExecResult() == null) {
			holder.setBatchExecResult(new BatchExecResult(holder.getSqlNode(), statement, table.getRows().size()));
		}
		if (statement.execute()) {
			sqlParameters.getDialect().getCorrelationStrategy().handleStatementResult(statement, sqlParameters);
		} else {
			holder.getBatchExecResult().setEnd(size, new int[] { statement.getUpdateCount() }, Collections.emptyList());
		}
		tableRelation.resetBatchCount();
	}

	@Override
	public void close() throws SQLException {
		int size = tableRelationTreeHolder.getRootTableList().size();
		for (int i = 0; i < size; i++) {
			TableRelation rootTableRelation = tableRelationTreeHolder.getRootTableList().get(i);
			if (rootTableRelation.getTable().getRows().size() > 0) {
				executeUpdate(rootTableRelation);
			}
			Table table = rootTableRelation.getTable();
			if (i == (size - 1)) {
				// ルートが複数ある場合は最後のRootでfinal commit
				Row row = lastRowMap.get(table.getSchemaName(), table.getName());
				if (commitCountHandler.isFinalCommit()) {
					beforeCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), row);
				}
				if (commitCountHandler.finalCommit(connection)) {
					afterCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), row);
				}
			}
		}
		for (TableRelation tableRelation : tableRelationTreeHolder) {
			tableRelation.close();
		}
		initialized = false;
		commitCountHandler.reset();
		lastRowMap.clear();
		batchUpdateCounter = 0;
	}

	public static enum TableUpdateMode {
		INSERT {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.INSERT };
			}
		},
		INSERT_NOT_EXISTS {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.SELECT_ROWS, SqlType.INSERT };
			}

			@Override
			public void handleStatementHolder(final Connection connection, final Dialect dialect,
					final TableRelation tableRelation, List<Row> rows, TableOptions tableOptions) throws SQLException {
				List<Row> targetRows = getNotExistsRows(connection, dialect, tableRelation, rows, tableOptions);
				handleStatementHolder(connection, dialect, tableRelation, targetRows, tableOptions, SqlType.UPDATE);
			}
		},
		UPDATE {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.UPDATE };
			}
		},
		MERGE {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.UPDATE, SqlType.INSERT };
			}

			@Override
			public void handleStatementHolder(final Connection connection, final Dialect dialect,
					final TableRelation tableRelation, List<Row> rows, TableOptions tableOptions) throws SQLException {
				int[] ret = handleStatementHolder(connection, dialect, tableRelation, rows, tableOptions,
						SqlType.UPDATE);
				List<Row> targetRows = CommonUtils.list();
				for (int i = 0; i < ret.length; i++) {
					if (ret[i] == 0) {
						targetRows.add(rows.get(i));
					}
				}
				handleStatementHolder(connection, dialect, tableRelation, targetRows, tableOptions, SqlType.INSERT);
			}
		},
		DELETE {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.DELETE };
			}
		},;

		public SqlType[] getSqlTypes() {
			return new SqlType[0];
		}

		public void handleStatementHolder(final Connection connection, final Dialect dialect,
				final TableRelation tableRelation, List<Row> rows, TableOptions tableOptions) throws SQLException {
			SqlType[] sqlTypes = getSqlTypes();
			for (SqlType sqlType : sqlTypes) {
				handleStatementHolder(connection, dialect, tableRelation, rows, tableOptions, sqlType);
			}
		}

		protected List<Row> getNotExistsRows(final Connection connection, final Dialect dialect,
				final TableRelation tableRelation, List<Row> rows, TableOptions tableOptions) throws SQLException {
			StatementHolder holder = tableRelation.getStatementHolder(SqlType.SELECT_ROWS);
			Table table = tableRelation.getTable();
			final Set<Integer> rowNums = CorrelationStrategy.getRowNoSet(table.getRows());
			ColumnSelectionStrategy columnSelectionStrategy = tableOptions.getUpdateKeyColumnsMatchingStrategy()
					.apply(table);
			Set<Set<Column>> columnsSet = columnSelectionStrategy.getKeyColumnsSet(table);
			int parameterCount = table.getRows().size();
			PreparedStatement statement = null;
			if (holder.getSqlParameters(parameterCount) == null) {
				statement = holder.createStatement(connection, parameterCount, table.getRows(), false);
			} else {
				statement = holder.getStatement(parameterCount, table.getRows());
			}
			List<Row> list = CommonUtils.list();
			try (ResultSet resultSet = statement.executeQuery()) {
				final ResultSetMetaData metaData = resultSet.getMetaData();
				final Set<String> resultSetColumnNames = DbUtils.getColumnNames(metaData);
				while (resultSet.next()) {
					final Row compareRow = table.newRow();
					for (String columnName : resultSetColumnNames) {
						compareRow.put(columnName, resultSet.getObject(columnName));
					}
					boolean find = false;
					Row row = null;
					for (final Set<Column> columns : columnsSet) {
						row = find(compareRow, table, columns, resultSetColumnNames, rowNums);
						if (row != null) {
							find = true;
							break;
						}
					}
					if (!find) {
						list.add(row);
					}
				}
			}
			return list;
		}

		private Row find(Row compareRow, Table table, Set<Column> columns, Set<String> resultSetColumnNames,
				Set<Integer> rowNums) throws SQLException {
			return CorrelationStrategy.find(compareRow, table, columns, resultSetColumnNames, rowNums);
		}

		private static final int[] EMPTY_RESULT = new int[0];

		protected int[] handleStatementHolder(final Connection connection, final Dialect dialect,
				final TableRelation tableRelation, List<Row> rows, TableOptions tableOptions, SqlType sqlType)
				throws SQLException {
			if (rows.size() == 0) {
				return EMPTY_RESULT;
			}
			StatementHolder holder = tableRelation.getStatementHolder(sqlType);
			int parameterCount = 0;
			PreparedStatement statement = null;
			for (Row obj : rows) {
				if (holder.getSqlParameters(parameterCount) == null) {
					statement = holder.createStatement(connection, parameterCount, obj, tableRelation.isIdentity());
				} else {
					statement = holder.getStatement(parameterCount, obj);
				}
				statement.addBatch();
			}
			final int[] ret = statement.executeBatch();
			final List<GeneratedKeyInfo> keys;
			if (sqlType == SqlType.INSERT && tableRelation.isIdentity()) {
				keys = JdbcHandlerUtils.getGeneratedKeys(statement, dialect);
			} else {
				keys = Collections.emptyList();
			}
			statement.clearBatch();
			if (sqlType == SqlType.INSERT) {
				if (!keys.isEmpty()) {
					Column column = null;
					int cnt = 0;
					for (int i = 0; i < ret.length; i++) {
						Row row = rows.get(i);
						if (ret[i] > 0) {
							GeneratedKeyInfo generatedKeyInfo = keys.get(cnt++);
							if (column == null) {
								column = row.getTable().getColumns().get(generatedKeyInfo.getColumnLabel());
							}
							row.put(column, generatedKeyInfo.getValue());
						}
					}
				}
			}
			tableRelation.resetBatchCount();
			return ret;
		}
	}
}
