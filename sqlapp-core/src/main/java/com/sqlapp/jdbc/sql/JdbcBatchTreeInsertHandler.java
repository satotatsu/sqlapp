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
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.jdbc.sql.JdbcBatchIterateHander.ValueHolder;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

public class JdbcBatchTreeInsertHandler implements AutoCloseable {

	private int rootBatchSize = 500;

	private boolean initialized = false;
	private final Dialect dialect;
	private final SqlFactoryRegistry sqlFactoryRegistry;
	private final Connection connection;

	private Function<Object, Object> valueConverter = o -> o;
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

	public JdbcBatchTreeInsertHandler(Connection connection, TableRelationTreeHolder tableRelationTreeHolder) {
		this.connection = connection;
		this.dialect = DialectResolver.getInstance().getDialect(connection);
		this.tableRelationTreeHolder = tableRelationTreeHolder;
		this.sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
	}

	private Function<Table, SqlType> sqlType = t -> SqlType.INSERT;

	public void setNewRowInitializer(Consumer<Row> newRowInitializer) {
		this.newRowInitializer = newRowInitializer;
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
			if (tableRelation.getTable().getRows().size() >= this.getRootBatchSize()) {
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
			List<SqlNode> sqlNodes = sqlFactoryRegistry.createSqlNodes(tableRelation.getTable(), sqlType.apply(table));
			SqlNode sqlNode = CommonUtils.first(sqlNodes);
			StatementHolder statementHolder = new StatementHolder(sqlNode);
			tableRelation.addStatementHolder(statementHolder);
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
		final ColumnCollection cols = table.getColumns();
		for (Row row : table.getRows()) {
			ParametersContext context = new ParametersContext();
			for (Column column : cols) {
				context.put(column.getName(), row.get(column));
			}
			final ValueHolder valueHolder = new ValueHolder(row, this.valueConverter.apply(context));
			values.add(valueHolder);
		}
		if (root) {
			beforeRootBatchHandler.accept(this.batchUpdateCounter, table);
		}
		handleStatementHolder(tableRelation, values);
		if (root) {
			this.batchUpdateCounter++;
			afterRootBatchHandler.accept(this.batchUpdateCounter, table);
		}
		values.clear();
		setRowValueToChildren(tableRelation);
		for (TableRelation childTableRelation : tableRelation.getChildren()) {
			handleAsBatch(childTableRelation, false);// 再帰的に子供をバッチ後進
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
					childRow.put(column, childRow.getParentRow().get(parentColumn));
				});
			}
		}
	}

	private void handleStatementHolder(final TableRelation tableRelation, final List<ValueHolder> values)
			throws SQLException {
		int size = values.size();
		if (size == 0) {
			return;
		}
		final SqlType sqlType = this.sqlType.apply(tableRelation.getTable());
		StatementHolder holder = tableRelation.getStatementHolder(sqlType);
		SqlParameterCollection sqlParameters = null;
		PreparedStatement statement = null;
		final String sql = holder.getSqlNode().getSql();
		for (ValueHolder obj : values) {
			if (holder.getSqlParameters(sql, size) == null) {
				sqlParameters = holder.getSqlNode().eval(obj.converted());
				if (tableRelation.isIdentity()) {
					sqlParameters.setGeneratedKey(GeneratedKey.RETURN_GENERATED_KEYS);
				}
				statement = JdbcHandlerUtils.getStatement(connection, sqlParameters);
				holder.setSqlParameters(sql, size, sqlParameters, statement);
				JdbcHandlerUtils.setBind(statement, dialect, sqlParameters);
			} else {
				sqlParameters = holder.getSqlParameters(sql, size);
				statement = holder.getPreparedStatement(sql, size);
				holder.getSqlNode().reEval(obj.converted(), sqlParameters);
				JdbcHandlerUtils.setBind(statement, dialect, sqlParameters);
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
		if (!keys.isEmpty()) {
			Column column = null;
			int cnt = 0;
			for (int i = 0; i < ret.length; i++) {
				ValueHolder valueHolder = values.get(i);
				Row row = (Row) valueHolder.value();
				if (ret[i] > 0) {
					GeneratedKeyInfo generatedKeyInfo = keys.get(cnt++);
					if (column == null) {
						column = row.getTable().getColumns().get(generatedKeyInfo.getColumnLabel());
					}
					row.put(column, generatedKeyInfo.getValue());
				}
			}
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
	}
}
