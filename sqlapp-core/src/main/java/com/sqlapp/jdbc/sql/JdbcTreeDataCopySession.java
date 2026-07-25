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
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

public class JdbcTreeDataCopySession implements AutoCloseable {

	private int rootBatchSize = 500;

	private JdbcTreeDataSession source;
	private JdbcTreeDataSession target;
	private BiPredicate<Column, Column> copyColumnPredicate = (sourceColumn, column) -> {
		return true;
	};

	private HoldCursorStrategy holdCursorStrategy = HoldCursorStrategy.DIALECT;

	public void setHoldCursorStrategy(HoldCursorStrategy holdCursorStrategy) {
		this.holdCursorStrategy = Objects.requireNonNull(holdCursorStrategy);
	}

	public void setCopyColumnPredicate(BiPredicate<Column, Column> copyColumnPredicate) {
		this.copyColumnPredicate = Objects.requireNonNull(copyColumnPredicate);
		this.columnMappingMap.clear();
	}

	private SQLConsumer<List<Row>> deleteSourceRowHandler = rows -> {
		final TableRelation tableRelation = source.getRootTableRelation();
		source.deleteByRows(tableRelation, rows);
	};

	/**
	 * Sets the handler invoked after a root batch has been successfully copied.
	 * <p>
	 * The handler is typically used to remove or mark the copied source rows so
	 * that they are not processed again.
	 * </p>
	 * <p>
	 * When {@link HoldCursorStrategy#REOPEN} is used, or when the root cursor is
	 * reopened after a commit, the handler must ensure that processed root rows are
	 * excluded from subsequent root selections.
	 * </p>
	 *
	 * @param deleteSourceRowHandler handler invoked for successfully copied source
	 *                               root rows
	 */
	public void setDeleteSourceRowHandler(SQLConsumer<List<Row>> deleteSourceRowHandler) {
		this.deleteSourceRowHandler = Objects.requireNonNull(deleteSourceRowHandler);
	}

	public void setRootBatchSize(int rootBatchSize) {
		if (rootBatchSize <= 0) {
			throw new IllegalArgumentException("rootBatchSize must be greater than zero.");
		}
		this.rootBatchSize = rootBatchSize;
		source.setRootBatchSize(rootBatchSize);
		target.setRootBatchSize(rootBatchSize);
	}

	/**
	 * Sets the number of completed root JDBC batches between commits.
	 *
	 * @param long root batch count
	 */
	public void setCommitEveryRootBatches(long commitEveryRootBatches) {
		if (commitEveryRootBatches <= 0) {
			throw new IllegalArgumentException("commitEveryRootBatches must be greater than zero.");
		}
		source.setCommitEveryRootBatches(Long.MAX_VALUE);
		target.setCommitEveryRootBatches(commitEveryRootBatches);
	}

	public JdbcTreeDataCopySession(JdbcTreeDataSession source, JdbcTreeDataSession target) {
		this.source = Objects.requireNonNull(source, "source");
		this.target = Objects.requireNonNull(target, "target");
		this.source = source;
		this.target = target;
		setRootBatchSize(this.rootBatchSize);
		final List<Row> sourceRows = CommonUtils.list();
		this.source.setAfterRootBatchHandler((i, t, rows) -> {
			sourceRows.addAll(rows);
		});
		this.target.setAfterRootBatchHandler((i, t, rows) -> {
			deleteSourceRows(sourceRows);
		});
		this.target.setAfterCommitEveryRootBatchesHandler((i, row) -> {
			if (!source.isSameConnection(target)) {
				source.commitForce();
			}
			rootCursorCommitted = true;
		});
	}

	private void deleteSourceRows(final List<Row> sourceRows) throws SQLException {
		if (sourceRows.isEmpty()) {
			return;
		}
		deleteSourceRowHandler.accept(sourceRows);
		sourceRows.clear();
	}

	public Row getRow(Table table) throws SQLException {
		return source.getRow(table);
	}

	private boolean rootCursorCommitted;

	public boolean next(Table table) throws SQLException {
		final TableRelation tableRelation = source.getTableRelation(table);
		if (!tableRelation.isRoot()) {
			return source.next(tableRelation);
		}
		if (rootCursorCommitted) {
			rootCursorCommitted = false;
			boolean useHoldableCursor = holdCursorStrategy.useHoldableRootCursor(source.getConnection(),
					source.getDialect());
			if (!useHoldableCursor) {
				source.reSelectRoot();
			}
		}
		return source.next(tableRelation);
	}

	public Row newRow(Table table) throws SQLException {
		return target.newRow(table);
	}

	public Row newCopy(Row sourceRow, Table table) throws SQLException {
		Table source = sourceRow.getTable();
		Row row = newRow(table);
		ColumnMapping columnMapping = columnMappingMap.get(source, table);
		if (columnMapping == null) {
			columnMapping = new ColumnMapping(source, table);
			columnMappingMap.put(source, table, columnMapping);
		}
		columnMapping.copyValue(sourceRow, row);
		return row;
	}

	private final DoubleKeyMap<Table, Table, ColumnMapping> columnMappingMap = CommonUtils.doubleKeyMap();

	class ColumnMapping {
		ColumnMapping(Table source, Table table) {
			for (int i = 0; i < source.getColumns().size(); i++) {
				Column sourceColumn = source.getColumns().get(i);
				Column column = table.getColumns().get(sourceColumn.getName());
				if (column != null) {
					if (copyColumnPredicate.test(sourceColumn, column)) {
						sourceColumns.add(sourceColumn);
						columns.add(column);
					}
				}
			}
		}

		private final List<Column> sourceColumns = CommonUtils.list();
		private final List<Column> columns = CommonUtils.list();

		public void copyValue(Row source, Row row) {
			for (int i = 0; i < columns.size(); i++) {
				Object obj = source.get(sourceColumns.get(i));
				row.put(columns.get(i), obj);
			}
		}
	}

	@Override
	public void close() throws SQLException {
		SQLException exception = null;

		try {
			source.close();
		} catch (SQLException e) {
			exception = e;
		}

		try {
			target.close();
		} catch (SQLException e) {
			if (exception == null) {
				exception = e;
			} else {
				exception.addSuppressed(e);
			}
		}

		if (exception != null) {
			throw exception;
		}
	}

	public static enum HoldCursorStrategy {
		HOLD {
			@Override
			public boolean useHoldableRootCursor(Connection connection, Dialect dialect) {
				return true;
			}
		},
		REOPEN {
			@Override
			public boolean useHoldableRootCursor(Connection connection, Dialect dialect) {
				return false;
			}
		},
		DIALECT {
			@Override
			public boolean useHoldableRootCursor(Connection connection, Dialect dialect) throws SQLException {
				return dialect.supportsHoldCursorsOverCommit(connection);
			}
		},;

		public abstract boolean useHoldableRootCursor(Connection connection, Dialect dialect) throws SQLException;
	}
}
