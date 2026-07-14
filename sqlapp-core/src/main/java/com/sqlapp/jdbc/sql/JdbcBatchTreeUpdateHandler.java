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
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.data.schemas.function.TableFunction;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DbUtils;
import com.sqlapp.util.DoubleKeyMap;
import com.sqlapp.util.function.TriConsumer;
import com.sqlapp.util.function.TriFunction;

public class JdbcBatchTreeUpdateHandler implements AutoCloseable {

	private int rootBatchSize = 500;

	private boolean initialized = false;
	private Dialect dialect;
	private SqlFactoryRegistry sqlFactoryRegistry;
	private final Connection connection;
	private SQLConsumer<Connection> commitHandler = conn -> conn.commit();
	private final TableRelationTreeHolder tableRelationTreeHolder;
	private final CommitCountHolder commitCountHandler = new CommitCountHolder(Long.MAX_VALUE, this.commitHandler);

	private long batchUpdateCounter = 0;

	private TriConsumer<Long, Table, List<Row>> afterRootBatchHandler = (i, t, rows) -> {
	};

	private BiConsumer<Long, Row> afterCommitEveryRootsHandler = (i, t) -> {
	};

	private TriConsumer<Long, Table, List<Row>> beforeRootBatchHandler = (i, t, rows) -> {
	};

	private BiConsumer<Long, Row> beforeCommitEveryRootsHandler = (i, t) -> {
	};

	private TriFunction<Table, SqlType, String, String> sqlHandler = (t, sqlType, sql) -> {
		return sql;
	};

	private Consumer<Row> newRowInitializer = r -> {
	};

	private Function<Table, TableUpdateMode> tableUpdateMode = t -> TableUpdateMode.INSERT;

	public void setBeforeRootBatchHandler(TriConsumer<Long, Table, List<Row>> beforeRootBatchHandler) {
		this.beforeRootBatchHandler = beforeRootBatchHandler;
	}

	public void setAfterRootBatchHandler(TriConsumer<Long, Table, List<Row>> afterRootBatchHandler) {
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

	public void setSqlHandler(TriFunction<Table, SqlType, String, String> sqlHandler) {
		this.sqlHandler = sqlHandler;
	}

	public JdbcBatchTreeUpdateHandler(Connection connection, TableRelationTreeHolder tableRelationTreeHolder) {
		this.connection = connection;
		this.tableRelationTreeHolder = tableRelationTreeHolder;
		this.dialect = DialectResolver.getInstance().getDialect(connection);
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
			int tableRowSize = tableRelation.getRows().size();
			if (tableRowSize >= this.getRootBatchSize()) {
				executeUpdate(tableRelation);
			}
			tableRowSize = tableRelation.getRows().size();
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
					statementHolder.setSqlHandler(sqlHandler);
					tableRelation.addStatementHolder(statementHolder);
				}
			}
		}
	}

	private void executeUpdate(final TableRelation tableRelation) throws SQLException {
		if (tableRelation.getRows().size() == 0) {
			return;
		}
		handleAsBatch(tableRelation, true);
	}

	private boolean rootFinish = true;

	private void handleAsBatch(final TableRelation tableRelation, boolean root) throws SQLException {
		Table table = tableRelation.getTable();
		if (root) {
			beforeRootBatchHandler.accept(this.batchUpdateCounter, table, tableRelation.getRows());
			rootFinish = false;
		}
		TableUpdateMode type = tableUpdateMode.apply(table);
		List<Row> rows = type.handleStatementHolder(this, tableRelation, tableRelation.getRows());
		if (root) {
			this.batchUpdateCounter++;
			afterRootBatchHandler.accept(this.batchUpdateCounter, table, rows);
		}
		setRowValueToChildren(tableRelation);
		for (TableRelation childTableRelation : tableRelation.getChildren()) {
			handleAsBatch(childTableRelation, false);// 再帰的に子供をBATCH UPDATE
		}
		if (root) {
			Row row = CommonUtils.last(tableRelation.getRows());
			if (commitCountHandler.isCommit()) {
				beforeCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), row);
			}
			if (commitCountHandler.commit(connection)) {
				afterCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), row);
			}
			lastRowMap.put(table.getSchemaName(), table.getName(), row);
			clearRows(tableRelation);
			rootFinish = true;
		}
	}

	private final DoubleKeyMap<String, String, Row> lastRowMap = CommonUtils.doubleKeyMap();

	private void clearRows(final TableRelation tableRelation) {
		tableRelation.getRows().clear();
		for (TableRelation child : tableRelation.getChildren()) {
			clearRows(child);
		}
	}

	private void setRowValueToChildren(final TableRelation tableRelation) {
		for (TableRelation childTableRelation : tableRelation.getChildren()) {
			for (Row childRow : childTableRelation.getRows()) {
				childTableRelation.forEach((i, column, parentColumn) -> {
					Object value = childRow.getParentRow().get(parentColumn);
					childRow.put(column, value);
				});
			}
		}
	}

	private void handleStatementHolderForRows(final TableRelation tableRelation, List<Row> rows, SqlType sqlType)
			throws SQLException {
		StatementHolder holder = tableRelation.getStatementHolder(sqlType);
		Table table = tableRelation.getTable();
		int rowSize = rows.size();
		SqlParameterCollection sqlParameters = null;
		PreparedStatement statement = null;
		final Set<Integer> rowNums = CorrelationStrategy.getRowNoSet(rows);
		final ColumnSelectionStrategy columnSelectionStrategy = this.getTableOptions()
				.getUpdateKeyColumnsMatchingStrategy().apply(table);
		SqlSignature sqlSignature = tableRelation.getSqlSignature();
		if (sqlSignature == null) {
			sqlSignature = tableRelation.createSqlSignature(rows);
		}
		int columnSize = sqlSignature.getSelectedColumnsHolder().getKeyColumns().size();
		if (holder.getStatement(sqlSignature, rowSize, rows) == null) {
			sqlParameters = holder.getSqlNode().eval(rows);
			sqlParameters.setFetchSize(rowSize);
			sqlParameters.setSqlSignature(sqlSignature);
			statement = dialect.getCorrelationStrategy().createPreparedStatement(connection, sqlParameters);
			JdbcHandlerUtils.setStatementParameters(sqlParameters, statement);
			holder.setSqlParameters(columnSize, rowSize, sqlParameters, statement);
			sqlParameters.setBind(statement);
		} else {
			statement = holder.getStatement(sqlSignature, rows);
		}
		if (holder.getBatchExecResult() == null) {
			holder.setBatchExecResult(new BatchExecResult(holder.getSqlNode(), statement, rows.size()));
		}
		if (statement.execute()) {
			sqlParameters.getDialect().getCorrelationStrategy().handleStatementResult(statement, sqlParameters);
		} else {
			holder.getBatchExecResult().setEnd(rowSize, new int[] { statement.getUpdateCount() },
					Collections.emptyList());
		}
		tableRelation.resetBatchCount();
	}

	@Override
	public void close() throws SQLException {
		if (rootFinish) {
			int size = tableRelationTreeHolder.getRootTableList().size();
			boolean executeUpdate = false;
			for (int i = 0; i < size; i++) {
				TableRelation rootTableRelation = tableRelationTreeHolder.getRootTableList().get(i);
				if (rootTableRelation.getRows().size() > 0) {
					executeUpdate(rootTableRelation);
					executeUpdate = true;
				}
				Table table = rootTableRelation.getTable();
				if (i == (size - 1)) {
					// ルートが複数ある場合は最後のRootでfinal commit
					Row row = lastRowMap.get(table.getSchemaName(), table.getName());
					if (executeUpdate && commitCountHandler.isFinalCommit()) {
						beforeCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), row);
					}
					if (executeUpdate && commitCountHandler.finalCommit(connection)) {
						afterCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), row);
					}
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
			public List<Row> handleStatementHolder(JdbcBatchTreeUpdateHandler handler,
					final TableRelation tableRelation, List<Row> rows) throws SQLException {
				List<Row> targetRows = handler.getNotExistsRows(tableRelation, rows);
				handler.handleStatementHolder(tableRelation, targetRows, SqlType.INSERT);
				return targetRows;
			}
		},
		UPDATE {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.UPDATE };
			}

			@Override
			public List<Row> handleStatementHolder(JdbcBatchTreeUpdateHandler handler,
					final TableRelation tableRelation, List<Row> rows) throws SQLException {
				SqlSignature sqlSignature = tableRelation.getSqlSignature();
				if (sqlSignature == null) {
					sqlSignature = tableRelation.createSqlSignature(rows);
				}
				sqlSignature.forEach(columnsHolder -> {
					if (!columnsHolder.getNullForeingKeyCommonColumns().isEmpty()) {
						// TODO
					}
				});
				return handler.handleStatementHolder(tableRelation, rows, this.getSqlTypes());
			}
		},
		MERGE {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.UPDATE, SqlType.INSERT };
			}

			@Override
			public List<Row> handleStatementHolder(JdbcBatchTreeUpdateHandler handler,
					final TableRelation tableRelation, List<Row> rows) throws SQLException {
				int[] ret = handler.handleStatementHolder(tableRelation, rows, SqlType.UPDATE);
				List<Row> targetRows = CommonUtils.list();
				for (int i = 0; i < ret.length; i++) {
					if (ret[i] == 0) {
						targetRows.add(rows.get(i));
					}
				}
				handler.handleStatementHolder(tableRelation, targetRows, SqlType.INSERT);
				return rows;
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

		public List<Row> handleStatementHolder(JdbcBatchTreeUpdateHandler handler, final TableRelation tableRelation,
				List<Row> rows) throws SQLException {
			SqlType[] sqlTypes = getSqlTypes();
			for (SqlType sqlType : sqlTypes) {
				handler.handleStatementHolder(tableRelation, rows, sqlType);
			}
			return rows;
		}
	}

	public List<Row> handleStatementHolder(final TableRelation tableRelation, List<Row> rows, SqlType[] sqlTypes)
			throws SQLException {
		for (SqlType sqlType : sqlTypes) {
			handleStatementHolder(tableRelation, rows, sqlType);
		}
		return rows;
	}

	private static final int[] EMPTY_RESULT = new int[0];

	protected int[] handleStatementHolder(final TableRelation tableRelation, List<Row> rows, SqlType sqlType)
			throws SQLException {
		if (rows.size() == 0) {
			return EMPTY_RESULT;
		}
		StatementHolder holder = tableRelation.getStatementHolder(sqlType);
		SqlSignature sqlSignature = tableRelation.getSqlSignature();
		if (sqlSignature == null) {
			sqlSignature = tableRelation.createSqlSignature(rows);
			sqlSignature.setColumnSelectionStrategy(
					sqlType.getColumnSelectionStrategy(tableRelation.getTable(), this.getTableOptions()));
		}
		final int rowSize = 1;
		PreparedStatement statement = null;
		for (Row obj : rows) {
			if (holder.getStatement(sqlSignature, rowSize, obj) == null) {
				statement = holder.createStatement(connection, sqlSignature, rowSize, obj, tableRelation.isIdentity());
			} else {
				statement = holder.getStatement(sqlSignature, rowSize, obj);
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

	protected List<Row> getNotExistsRows(final TableRelation tableRelation, List<Row> rows) throws SQLException {
		StatementHolder holder = tableRelation.getStatementHolder(SqlType.SELECT_ROWS);
		Table table = tableRelation.getTable();
		final Set<Integer> rowNums = CorrelationStrategy.getRowNoSet(rows);
		ColumnSelectionStrategy columnSelectionStrategy = this.getTableOptions().getUpdateKeyColumnsMatchingStrategy()
				.apply(table);
		SqlSignature sqlSignature = tableRelation.getSqlSignature();
		if (sqlSignature == null) {
			sqlSignature = tableRelation.createSqlSignature(rows);
		}
		PreparedStatement statement = null;
		if (holder.getStatement(sqlSignature, rows) == null) {
			final TableFunction<List<Row>> strategy = this.getTableOptions().getTableRowsStrategy();
			this.getTableOptions().setTableRowsStrategy(t -> {
				if (t == table) {
					return rows;
				} else {
					return strategy.apply(table);
				}
			});
			statement = holder.createStatement(connection, sqlSignature, rows, false);
			this.getTableOptions().setTableRowsStrategy(strategy);
		} else {
			statement = holder.getStatement(sqlSignature, rows);
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
				Row row = columnSelectionStrategy.find(sqlSignature, compareRow, rows, rowNums);
				if (row != null) {
					break;
				}
			}
		}
		for (int i : rowNums) {
			list.add(rows.get(i));
		}
		return list;
	}

}
