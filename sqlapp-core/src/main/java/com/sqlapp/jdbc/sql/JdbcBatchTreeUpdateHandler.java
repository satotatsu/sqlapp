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
import com.sqlapp.data.db.sql.SqlOperation;
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

public class JdbcBatchTreeUpdateHandler implements AutoCloseable {

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

	private BiConsumer<Long, Table> afterCommitEveryRootsHandler = (i, t) -> {
	};

	private Consumer<Row> newRowInitializer = r -> {
	};

	public void setAfterRootBatchHandler(BiConsumer<Long, Table> afterRootBatchHandler) {
		this.afterRootBatchHandler = afterRootBatchHandler;
	}

	public void setAfterCommitEveryRootsHandler(BiConsumer<Long, Table> afterCommitEveryRootsHandler) {
		this.afterCommitEveryRootsHandler = afterCommitEveryRootsHandler;

	}

	public JdbcBatchTreeUpdateHandler(Connection connection, TableRelationTreeHolder tableRelationTreeHolder) {
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
			List<SqlOperation> sqlOperations = sqlFactoryRegistry.createSql(tableRelation.getTable(),
					sqlType.apply(table));
			SqlOperation sqlOperation = CommonUtils.first(sqlOperations);
			SqlNode sqlNode = SqlParser.getInstance().parse(sqlOperation.getSqlText());
			StatementHolder statementHolder = new StatementHolder(sqlNode);
			tableRelation.setStatementHolder(statementHolder);
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
			if (commitCountHandler.commit(connection)) {
				afterCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(), table);
			}
			clearRows(tableRelation);
		}
	}

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
		StatementHolder holder = tableRelation.getStatementHolder();
		for (ValueHolder obj : values) {
			if (holder.getBatchExecResult() == null) {
				holder.setBatchExecResult(new BatchExecResult(holder, values.size()));
			}
			holder.getBatchExecResult().getValues().add(obj);
			if (holder.getSqlParameters() == null) {
				holder.setSqlParameters(holder.getSqlNode().eval(obj.converted()));
				if (tableRelation.isIdentity()) {
					holder.getSqlParameters().setGeneratedKey(GeneratedKey.RETURN_GENERATED_KEYS);
				}
				final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection,
						holder.getSqlParameters());
				holder.setStatement(statement);
				JdbcHandlerUtils.setBind(holder.getStatement(), dialect, holder.getSqlParameters());
			} else {
				holder.getSqlNode().reEval(obj.converted(), holder.getSqlParameters());
				JdbcHandlerUtils.setBind(holder.getStatement(), dialect, holder.getSqlParameters());
			}
			holder.getStatement().addBatch();
		}
		final int[] ret = holder.getStatement().executeBatch();
		final List<GeneratedKeyInfo> keys;
		if (holder.getSqlParameters().getGeneratedKey() == GeneratedKey.RETURN_GENERATED_KEYS) {
			keys = JdbcHandlerUtils.getGeneratedKeys(holder.getStatement(), dialect);
		} else {
			keys = Collections.emptyList();
		}
		holder.getStatement().clearBatch();
		if (!keys.isEmpty()) {
			Column column = null;
			for (int i = 0; i < ret.length; i++) {
				ValueHolder valueHolder = values.get(i);
				Row row = (Row) valueHolder.value();
				GeneratedKeyInfo generatedKeyInfo = keys.get(i);
				if (column == null) {
					column = row.getTable().getColumns().get(generatedKeyInfo.getColumnName());
				}
				row.put(column, generatedKeyInfo.getValue());
			}
		}
	}

	@Override
	public void close() throws SQLException {
		for (TableRelation rootTableRelation : tableRelationTreeHolder.getRootTableList()) {
			boolean hasData = rootTableRelation.getTable().getRows().size() > 0;
			executeUpdate(rootTableRelation);
			if (hasData) {
				if (commitCountHandler.finalCommit(connection)) {
					afterCommitEveryRootsHandler.accept(commitCountHandler.getCommitCount(),
							rootTableRelation.getTable());
				}
			}
		}
		for (TableRelation tableRelation : tableRelationTreeHolder) {
			if (tableRelation.getStatementHolder() != null) {
				if (tableRelation.getStatementHolder().getStatement() != null) {
					tableRelation.getStatementHolder().getStatement().close();
				}
			}
		}
	}
}
