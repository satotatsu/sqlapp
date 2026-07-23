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
import com.sqlapp.data.db.sql.SqlSignature.ColumnsHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowOperation;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DbUtils;
import com.sqlapp.util.DoubleKeyMap;
import com.sqlapp.util.function.TriConsumer;
import com.sqlapp.util.function.TriFunction;

public class JdbcTreeDataSession implements AutoCloseable {

	private int rootBatchSize = 500;
	/** fetchSize */
	private int fetchSize = rootBatchSize;

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

	private SQLConsumer<PreparedStatement> preparedStatementBeforeExecuteHandler = statement -> {
	};

	private Function<Table, TableOperationMode> tableOperationMode = t -> TableOperationMode.INSERT;

	public void setPreparedStatementBeforeExecuteHandler(
			SQLConsumer<PreparedStatement> preparedStatementBeforeExecuteHandler) {
		this.preparedStatementBeforeExecuteHandler = preparedStatementBeforeExecuteHandler;
	}

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

	public void setTableOperationMode(Function<Table, TableOperationMode> tableOperationMode) {
		this.tableOperationMode = tableOperationMode;
	}

	public void setTableOperationMode(TableOperationMode tableOperationMode) {
		this.tableOperationMode = (t) -> tableOperationMode;
	}

	public void setSqlHandler(TriFunction<Table, SqlType, String, String> sqlHandler) {
		this.sqlHandler = sqlHandler;
	}

	public JdbcTreeDataSession(Connection connection, List<Table> tables) {
		this.connection = connection;
		this.tableRelationTreeHolder = new TableRelationTreeHolder(tables);
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

	public int getFetchSize() {
		return fetchSize;
	}

	public void setFetchSize(int fetchSize) {
		this.fetchSize = fetchSize;
	}

	public void setCommitEveryRoots(long commitSize) {
		commitCountHandler.setCommitSize(commitSize);
	}

	public long getCommitEveryRoots() {
		return commitCountHandler.getCommitSize();
	}

	public void select(Table table, String sql, Object context) throws SQLException {
		final TableRelation tableRelation = tableRelationTreeHolder.getTableRelation(table);
		if (tableRelation.isRoot()) {
			final SqlNode sqlNode = SqlParser.getInstance().parse(dialect, SqlType.SELECT, sql);
			selectRoot(tableRelation, sqlNode, context);
		} else {
			final SqlNode sqlNode = SqlParser.getInstance().parse(dialect, SqlType.SELECT_BY_ROOT_ROWS, sql);
			tableRelation.setSelectSqlNode(sqlNode);
		}
	}

	private void selectRoot(final TableRelation tableRelation, final SqlNode sqlNode, Object context)
			throws SQLException {
		tableRelation.setSelectRegistered(true);
		final Table table = tableRelation.getTable();
		final SqlParameterCollection sqlParameters = sqlNode.eval(context, sqlParam -> {
			sqlParam.setTable(table);
		});
		final PreparedStatement statement = sqlParameters.createStatement(connection);
		sqlParameters.setBind(statement);
		tableRelation.setStatement(statement);
		statement.setFetchSize(this.getRootBatchSize());
		preparedStatementBeforeExecuteHandler.accept(statement);
		tableRelation.setResultSet(statement.executeQuery(), this.getRootBatchSize(), () -> {
			for (TableRelation tabRelation : tableRelationTreeHolder) {
				if (tabRelation.isRoot() || !tabRelation.isSelectRegistered()) {
					continue;
				}
				final TableRelation parentTableRelation = tabRelation.getParentTableRelation();
				final List<Row> rows = loadRowDataByRoot(tabRelation);
				final Set<Integer> rowNums = CorrelationStrategy.getRowNoSet(rows);
				setParentRow(tabRelation, rows, rowNums, parentTableRelation.getRows());
				tabRelation.setLoadedRows(rows);
			}
		}, () -> {
			if (tableRelation.isRoot()) {
				executeUpdate(tableRelation);
			}
		});
	}

	public void select(Table table, String sql) throws SQLException {
		select(table, sql, CommonUtils.map());
	}

	public void select(Table table) throws SQLException {
		final TableRelation tableRelation = tableRelationTreeHolder.getTableRelation(table);
		tableRelation.setSelectRegistered(true);
		if (tableRelation.isRoot()) {
			List<SqlNode> sqlNodes = sqlFactoryRegistry.createSqlNodes(tableRelation.getTable(), SqlType.SELECT_ROWS);
			for (final SqlNode sqlNode : sqlNodes) {
				registerStatementHolder(tableRelation, sqlNode);
			}
			final SqlNode sqlNode = sqlNodes.getFirst();
			selectRoot(tableRelation, sqlNode, CommonUtils.map());
		}
	}

	public boolean next(Table table) throws SQLException {
		final TableRelation tableRelation = tableRelationTreeHolder.getTableRelation(table);
		if (!tableRelation.isSelectRegistered()) {
			throw new IllegalStateException(
					"Table[name=" + table.getName() + "] has not been selected. Call select(table) before next().");
		}
		return tableRelation.next();
	}

	public Row getRow(Table table) throws SQLException {
		final TableRelation tableRelation = tableRelationTreeHolder.getTableRelation(table);
		return tableRelation.get();
	}

	private void setParentRow(final TableRelation tableRelation, final List<Row> rows, final Set<Integer> rowNums,
			final List<Row> parentRows) {
		for (Row parentRow : parentRows) {
			Set<Integer> matchNums = CommonUtils.set();
			for (Integer i : rowNums) {
				Row row = rows.get(i);
				if (tableRelation.matchParentKey(row, parentRow)) {
					row.setParentRow(parentRow);
					matchNums.add(i);
				}
			}
			rowNums.removeAll(matchNums);
		}
	}

	public Row newRow(Table table) throws SQLException {
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

	private void initialize(final TableRelation tableRelation, final SqlType sqlType) {
		StatementHolder statementHolder = tableRelation.getStatementHolder(sqlType);
		if (statementHolder == null) {
			List<SqlNode> sqlNodes = sqlFactoryRegistry.createSqlNodes(tableRelation.getTable(), sqlType);
			for (final SqlNode sqlNode : sqlNodes) {
				registerStatementHolder(tableRelation, sqlNode);
			}
			sqlNodes = sqlFactoryRegistry.createSqlNodes(tableRelation, sqlType);
			for (final SqlNode sqlNode : sqlNodes) {
				registerStatementHolder(tableRelation, sqlNode);
			}
		}
	}

	private StatementHolder registerStatementHolder(final TableRelation tableRelation, final SqlNode sqlNode) {
		StatementHolder statementHolder = tableRelation.getStatementHolder(sqlNode.getSqlType());
		if (statementHolder == null) {
			statementHolder = new StatementHolder(sqlNode);
			statementHolder.setSqlHandler(sqlHandler);
			tableRelation.addStatementHolder(statementHolder);
		}
		return statementHolder;
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
		boolean updated = handleStatementHolder(this, tableRelation, tableRelation.getRows());
		if (root) {
			this.batchUpdateCounter++;
			afterRootBatchHandler.accept(this.batchUpdateCounter, table, tableRelation.getRows());
		}
		setRowValueToChildren(tableRelation);
		for (TableRelation childTableRelation : tableRelation.getChildren()) {
			handleAsBatch(childTableRelation, false);// 再帰的に子供をBATCH UPDATE
		}
		if (root) {
			Row row = CommonUtils.last(tableRelation.getRows());
			if (updated) {
				if (commitCountHandler.isCommit()) {
					beforeCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), row);
				}
				if (commitCountHandler.commit(connection)) {
					afterCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), row);
				}
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
					final Row parentRow = childRow.getParentRow();
					final Object value = parentRow.get(parentColumn);
					childRow.put(column, value);
				});
			}
		}
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
		commitCountHandler.reset();
		lastRowMap.clear();
		batchUpdateCounter = 0;
	}

	public static enum TableOperationMode {
		NONE {
			@Override
			public RowOperation getRowOperation() {
				return RowOperation.DEFAULT;
			}
		},
		INSERT {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.INSERT };
			}

			@Override
			public RowOperation getRowOperation() {
				return RowOperation.INSERT;
			}
		},
		INSERT_IGNORE {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.SELECT_ROWS, SqlType.INSERT };
			}

			@Override
			public RowOperation getRowOperation() {
				return RowOperation.INSERT_IGNORE;
			}
		},
		UPDATE {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.UPDATE };
			}

			@Override
			public RowOperation getRowOperation() {
				return RowOperation.UPDATE;
			}
		},
		MERGE {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.UPDATE, SqlType.INSERT };
			}

			@Override
			public RowOperation getRowOperation() {
				return RowOperation.MERGE;
			}
		},
		DELETE {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.DELETE };
			}

			@Override
			public boolean isDelete() {
				return true;
			}

			@Override
			public RowOperation getRowOperation() {
				return RowOperation.DELETE;
			}
		},
		REPLACE {
			@Override
			public SqlType[] getSqlTypes() {
				return new SqlType[] { SqlType.DELETE_BY_ROOT_ROWS, SqlType.INSERT };
			}

			@Override
			public RowOperation getRowOperation() {
				return RowOperation.INSERT;
			}

			@Override
			public boolean isDelete() {
				return true;
			}
		},;

		public boolean isDelete() {
			return false;
		}

		public RowOperation getRowOperation() {
			return null;
		}

		public SqlType[] getSqlTypes() {
			return new SqlType[0];
		}
	}

	private static final int[] EMPTY_RESULT = new int[0];

	private int[] handleStatement(final TableRelation tableRelation, List<Row> rows, SqlType sqlType)
			throws SQLException {
		if (rows.isEmpty()) {
			return EMPTY_RESULT;
		}
		final Table table = tableRelation.getTable();
		final int[] ret = this.getTableOptions().useTableRowStrategy(t -> t == table ? rows : t.getRows(), () -> {
			final SqlSignature sqlSignature = tableRelation.getOrCreateSqlSignature(rows);
			sqlSignature.setColumnSelectionStrategy(
					sqlType.getColumnSelectionStrategy(tableRelation.getTable(), this.getTableOptions()));
			StatementHolder holder = tableRelation.getStatementHolder(sqlType);
			if (holder == null) {
				initialize(tableRelation, sqlType);
				holder = tableRelation.getStatementHolder(sqlType);
			}
			final int rowSize = 1;
			PreparedStatement statement = null;
			for (Row obj : rows) {
				if (holder.getStatement(sqlSignature, rowSize, obj) == null) {
					statement = holder.createStatement(connection, sqlSignature, rowSize, obj,
							tableRelation.isIdentity());
					statement.setFetchSize(fetchSize);
				} else {
					statement = holder.getStatement(sqlSignature, rowSize, obj);
				}
				statement.addBatch();
			}
			preparedStatementBeforeExecuteHandler.accept(statement);
			int[] result = statement.executeBatch();
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
					for (int i = 0; i < result.length; i++) {
						Row row = rows.get(i);
						if (result[i] > 0) {
							GeneratedKeyInfo generatedKeyInfo = keys.get(cnt++);
							if (column == null) {
								column = row.getTable().getColumns().get(generatedKeyInfo.getColumnLabel());
							}
							row.put(column, generatedKeyInfo.getValue());
						}
					}
				}
			}
			return result;
		});
		tableRelation.resetBatchCount();
		return ret;
	}

	private boolean handleStatementHolder(JdbcTreeDataSession handler, final TableRelation tableRelation,
			List<Row> rows) throws SQLException {
		final SqlSignature sqlSignature = tableRelation.getOrCreateSqlSignature(rows);
		List<Row> filteredRows = rows.stream()
				.filter(row -> row.getParentRow() == null || !row.getParentRow().isDelete()).toList();// 親が削除された行は除外
		if (!tableRelation.isRoot() && sqlSignature.hasEmptyParentKeys()) {
			filteredRows = this.loadParent(tableRelation, filteredRows);// 親を読み込んで存在しない行は除外
		}
		if (filteredRows.isEmpty()) {
			return false;
		}
		final TableOperationMode mode = tableOperationMode.apply(tableRelation.getTable());
		final List<Row> deleteRows = CommonUtils.list();
		final List<Row> insertRows = CommonUtils.list();
		final List<Row> insertIgnoreRows = CommonUtils.list();
		final List<Row> updateRows = CommonUtils.list();
		final List<Row> mergeRows = CommonUtils.list();
		final List<Row> unchangedOrDefault = CommonUtils.list();
		filteredRows.forEach(row -> {
			if (row.isDefault()) {
				tableRelation.setRowOperation(row, mode.getRowOperation());
			}
			if (row.isDelete()) {
				deleteRows.add(row);
			} else if (row.isInsert()) {
				insertRows.add(row);
			} else if (row.isInsertIgnore()) {
				insertIgnoreRows.add(row);
			} else if (row.isUpdate()) {
				updateRows.add(row);
			} else if (row.isMerge()) {
				mergeRows.add(row);
			}
			if (row.isUnchanged() || row.isDefault()) {
				unchangedOrDefault.add(row);
			}
		});
		if (mode == TableOperationMode.REPLACE) {
			long ret = deleteByRootRows(tableRelation, rows);
			deleteRows.clear();
			insertRows.addAll(insertIgnoreRows);
			insertIgnoreRows.clear();
			handleStatement(tableRelation, insertRows, SqlType.INSERT);
			handleStatement(tableRelation, updateRows, SqlType.UPDATE);
			handleMerge(tableRelation, mergeRows);
			return true;
		} else {
			deleteByRows(tableRelation, deleteRows);
			handleStatement(tableRelation, insertRows, SqlType.INSERT);
			handleInsertIgnore(tableRelation, insertIgnoreRows);
			handleStatement(tableRelation, updateRows, SqlType.UPDATE);
			handleMerge(tableRelation, mergeRows);
			return unchangedOrDefault.isEmpty();
		}
	}

	public List<Row> handleDeleteStatementHolder(final TableRelation tableRelation, List<Row> rows)
			throws SQLException {
		if (tableRelation.isRoot()) {
			handleStatement(tableRelation, rows, SqlType.DELETE);
			return rows;
		} else {
			deleteByRootRows(tableRelation, rows);
			return rows;
		}
	}

	private void handleMerge(final TableRelation tableRelation, List<Row> rows) throws SQLException {
		int[] ret = handleStatement(tableRelation, rows, SqlType.UPDATE);
		List<Row> targetRows = CommonUtils.list();
		for (int i = 0; i < ret.length; i++) {
			if (ret[i] == 0) {
				targetRows.add(rows.get(i));
			}
		}
		handleStatement(tableRelation, targetRows, SqlType.INSERT);
	}

	public List<Row> handleInsertIgnore(final TableRelation tableRelation, List<Row> rows) throws SQLException {
		if (rows.isEmpty()) {
			return Collections.emptyList();
		}
		List<Row> filteredRows;
		if (tableRelation.isRoot()) {
			filteredRows = rows;
		} else {
			filteredRows = loadParent(tableRelation, rows);
		}
		List<Row> targetRows = getNotExistsRows(tableRelation, filteredRows);
		handleStatement(tableRelation, targetRows, SqlType.INSERT);
		return targetRows;
	}

	private long deleteByRootRows(final TableRelation tableRelation, List<Row> rows) throws SQLException {
		final TableRelation rootTableRelation = tableRelation.getRootTableRelation();
		if (rootTableRelation.isRoot()) {
			return 0;
		}
		List<Row> rootRows = rootTableRelation.getRows();
		long update = this.getTableOptions()
				.useTableRowStrategy(t -> t == rootTableRelation.getTable() ? rootRows : t.getRows(), () -> {
					SqlType sqlType = SqlType.DELETE_BY_ROOT_ROWS;
					StatementHolder holder = tableRelation.getStatementHolder(sqlType);
					if (holder == null) {
						initialize(tableRelation, sqlType);
						holder = tableRelation.getStatementHolder(sqlType);
					}
					final SqlSignature parentSqlSignature = rootTableRelation.getOrCreateSqlSignature(rootRows);
					PreparedStatement statement = null;
					if (holder.getStatement(parentSqlSignature, rootRows) == null) {
						statement = holder.createStatement(connection, parentSqlSignature, rootRows.size(), rootRows,
								false, param -> {
									param.setTableRelation(tableRelation);
								});
					} else {
						statement = holder.getStatement(parentSqlSignature, rootRows);
					}
					preparedStatementBeforeExecuteHandler.accept(statement);
					long ret = statement.executeLargeUpdate();
					return ret;
				});
		return update;
	}

	private long deleteByRows(final TableRelation tableRelation, List<Row> rows) throws SQLException {
		if (rows.isEmpty()) {
			return 0;
		}
		long update = this.getTableOptions()
				.useTableRowStrategy(t -> t == tableRelation.getTable() ? rows : t.getRows(), () -> {
					SqlType sqlType = SqlType.DELETE;
					StatementHolder holder = tableRelation.getStatementHolder(sqlType);
					if (holder == null) {
						initialize(tableRelation, sqlType);
						holder = tableRelation.getStatementHolder(sqlType);
					}
					final SqlSignature parentSqlSignature = tableRelation.getOrCreateSqlSignature(rows);
					PreparedStatement statement = null;
					if (holder.getStatement(parentSqlSignature, rows) == null) {
						statement = holder.createStatement(connection, parentSqlSignature, rows.size(), rows, false);
					} else {
						statement = holder.getStatement(parentSqlSignature, rows);
					}
					long ret = statement.executeLargeUpdate();
					return ret;
				});
		return update;
	}

	private List<Row> loadParent(final TableRelation tableRelation, List<Row> rows) throws SQLException {
		final TableRelation parentTableRelation = tableRelation.getParentTableRelation();
		if (parentTableRelation == null) {
			return rows;
		}
		final SqlSignature sqlSignature = tableRelation.getOrCreateSqlSignature(rows);
		if (!sqlSignature.getPrimaryKey().hasKeyFullValues() || !sqlSignature.getUniqueKey().hasKeyFullValues()) {
			SqlSignature parentSqlSignature = parentTableRelation.getSqlSignature();
			if (!parentSqlSignature.getPrimaryKey().hasKeyFullValues()
					|| !sqlSignature.getUniqueKey().hasKeyFullValues()) {
				List<Row> parentRows = loadParentRowDatas(parentTableRelation);
				if (parentRows.isEmpty()) {
					return Collections.emptyList();// 存在しない行で更新をしようとした場合
				}
			}
			ColumnsHolder columnsHolder;
			if (!parentSqlSignature.getPrimaryKey().hasKeyFullValues()) {
				columnsHolder = parentSqlSignature.getPrimaryKey();
			} else {
				columnsHolder = parentSqlSignature.getUniqueKey();
			}
			List<Row> result = CommonUtils.list(rows.size());
			for (Row row : rows) {
				boolean hasNull = false;
				for (Column column : columnsHolder.getNullForeingKeyCommonColumns()) {
					if (row.get(column) == null) {
						hasNull = true;
						break;
					}
				}
				if (!hasNull) {
					result.add(row);
				}
			}
			return result;
		}
		return rows;
	}

	private List<Row> loadRowDataByRoot(final TableRelation tableRelation) throws SQLException {
		SqlType sqlType = SqlType.SELECT_BY_ROOT_ROWS;
		final Table table = tableRelation.getTable();
		final TableRelation rootTableRelation = tableRelation.getRootTableRelation();
		final Table rootTable = rootTableRelation.getTable();
		final List<Row> rootRows = rootTableRelation.getRows();
		final List<Row> rows = this.getTableOptions().useTableRowStrategy(t -> t == rootTable ? rootRows : t.getRows(),
				() -> {
					final ColumnSelectionStrategy columnSelectionStrategy = this.getTableOptions()
							.getLoadDataKeyColumnsMatchingStrategy().apply(rootTable);
					final SqlSignature sqlSignature = rootTableRelation.getOrCreateSqlSignature(rootRows);
					ColumnsHolder columnsHolder = columnSelectionStrategy.get(sqlSignature);
					if (columnsHolder.getKeyColumns().isEmpty()) {
						return Collections.emptyList();// 親レコードが既に存在せずにキーの補填が出来ない場合
					}
					StatementHolder holder = tableRelation.getStatementHolder(sqlType);
					if (holder == null) {
						if (tableRelation.getSelectSqlNode() != null) {
							holder = registerStatementHolder(rootTableRelation, tableRelation.getSelectSqlNode());
						} else {
							initialize(tableRelation, sqlType);
							holder = tableRelation.getStatementHolder(sqlType);
						}
					}
					PreparedStatement statement = null;
					if (holder.getStatement(sqlSignature, rootRows) == null) {
						statement = holder.createStatement(connection, sqlSignature, rootRows.size(), rootRows, false,
								params -> {
									params.setTableRelation(tableRelation);
								});
						statement.setFetchSize(fetchSize);
					} else {
						statement = holder.getStatement(sqlSignature, rootRows);
					}
					List<Row> resultSetRows = CommonUtils.list(rootRows.size());
					preparedStatementBeforeExecuteHandler.accept(statement);
					try (ResultSet resultSet = statement.executeQuery()) {
						final ResultSetMetaData metaData = resultSet.getMetaData();
						final Set<String> resultSetColumnNames = DbUtils.getColumnNames(metaData);
						while (resultSet.next()) {
							final Row row = table.newRow();
							for (String columnName : resultSetColumnNames) {
								row.put(columnName, resultSet.getObject(columnName));
							}
							resultSetRows.add(row);
						}
					}
					return resultSetRows;
				});
		return rows;
	}

	private List<Row> loadParentRowDatas(final TableRelation tableRelation) throws SQLException {
		SqlType sqlType = SqlType.SELECT_ROWS;
		final Table table = tableRelation.getTable();
		final List<Row> rows = tableRelation.getRows();
		List<Row> parentRows = this.getTableOptions().useTableRowStrategy(t -> t == table ? rows : t.getRows(), () -> {
			final ColumnSelectionStrategy columnSelectionStrategy = this.getTableOptions()
					.getLoadDataKeyColumnsMatchingStrategy().apply(table);
			final SqlSignature sqlSignature = tableRelation.getOrCreateSqlSignature(rows);
			ColumnsHolder columnsHolder = columnSelectionStrategy.get(sqlSignature);
			if (columnsHolder.getKeyColumns().isEmpty()) {
				return Collections.emptyList();// 親レコードが既に存在せずにキーの補填が出来ない場合
			}
			StatementHolder holder = tableRelation.getStatementHolder(sqlType);
			if (holder == null) {
				initialize(tableRelation, sqlType);
				holder = tableRelation.getStatementHolder(sqlType);
			}
			final Set<Integer> rowNums = CorrelationStrategy.getRowNoSet(rows);
			PreparedStatement statement = null;
			if (holder.getStatement(sqlSignature, rows) == null) {
				statement = holder.createStatement(connection, sqlSignature, rows.size(), rows, false, params -> {
					params.setTableRelation(tableRelation);
				});
				statement.setFetchSize(fetchSize);
			} else {
				statement = holder.getStatement(sqlSignature, rows);
			}
			boolean hasResult = false;
			List<Row> resultSetRows = CommonUtils.list(rows.size());
			preparedStatementBeforeExecuteHandler.accept(statement);
			try (ResultSet resultSet = statement.executeQuery()) {
				final ResultSetMetaData metaData = resultSet.getMetaData();
				final Set<String> resultSetColumnNames = DbUtils.getColumnNames(metaData);
				final List<Column> resultSetColumns = resultSetColumnNames.stream()
						.map(name -> table.getColumns().get(name)).toList();
				while (resultSet.next()) {
					hasResult = true;
					final Row compareRow = table.newRow();
					for (String columnName : resultSetColumnNames) {
						compareRow.put(columnName, resultSet.getObject(columnName));
					}
					Row row = columnSelectionStrategy.find(sqlSignature, compareRow, rows, rowNums);
					if (row != null) {
						for (Column column : resultSetColumns) {
							row.put(column, compareRow.get(column));
						}
						resultSetRows.add(row);
					}
				}
			}
			if (hasResult) {
				setRowValueToChildren(tableRelation);
			}
			return resultSetRows;
		});
		return parentRows;
	}

	private List<Row> getNotExistsRows(final TableRelation tableRelation, List<Row> rows) throws SQLException {
		SqlType sqlType = SqlType.SELECT_ROWS;
		final Table table = tableRelation.getTable();
		final List<Row> list = CommonUtils.list();
		final Set<Integer> rowNums = CorrelationStrategy.getRowNoSet(rows);
		this.getTableOptions().useTableRowStrategy(t -> t == table ? rows : t.getRows(), () -> {
			ColumnSelectionStrategy columnSelectionStrategy = this.getTableOptions()
					.getUpdateKeyColumnsMatchingStrategy().apply(table);
			final SqlSignature sqlSignature = tableRelation.getOrCreateSqlSignature(rows);
			sqlSignature.setColumnSelectionStrategy(columnSelectionStrategy);
			PreparedStatement statement = null;
			StatementHolder holder = tableRelation.getStatementHolder(sqlType);
			if (holder == null) {
				initialize(tableRelation, sqlType);
				holder = tableRelation.getStatementHolder(sqlType);
			}
			if (holder.getStatement(sqlSignature, rows) == null) {
				statement = holder.createStatement(connection, sqlSignature, rows.size(), rows, false, params -> {
					params.setTableRelation(tableRelation);
				});
				statement.setFetchSize(fetchSize);
			} else {
				statement = holder.getStatement(sqlSignature, rows);
			}
			preparedStatementBeforeExecuteHandler.accept(statement);
			try (ResultSet resultSet = statement.executeQuery()) {
				final ResultSetMetaData metaData = resultSet.getMetaData();
				final Set<String> resultSetColumnNames = DbUtils.getColumnNames(metaData);
				while (resultSet.next()) {
					final Row compareRow = table.newRow();
					for (String columnName : resultSetColumnNames) {
						compareRow.put(columnName, resultSet.getObject(columnName));
					}
					columnSelectionStrategy.find(sqlSignature, compareRow, rows, rowNums);
				}
			}
			return null;
		});
		for (int i : rowNums) {
			list.add(rows.get(i));
		}
		return list;
	}

}
