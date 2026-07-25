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

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

public class JdbcTreeDataCopySession implements AutoCloseable {

	private int rootBatchSize = 500;

	private JdbcTreeDataSession source;
	private JdbcTreeDataSession target;

	public void setRootBatchSize(int rootBatchSize) {
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
		source.setCommitEveryRootBatches(Long.MAX_VALUE);
		target.setCommitEveryRootBatches(commitEveryRootBatches);
	}

	public JdbcTreeDataCopySession(JdbcTreeDataSession source, JdbcTreeDataSession target) {
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
			if (!source.isSupportsResultSetHoldability()) {
				rootReselectRequired = true;
			}
		});
	}

	private void deleteSourceRows(final List<Row> sourceRows) throws SQLException {
		final TableRelation tableRelation = source.getRootTableRelation();
		source.deleteByRows(tableRelation, sourceRows);
		sourceRows.clear();
	}

	public Row getRow(Table table) throws SQLException {
		return source.getRow(table);
	}

	private boolean rootReselectRequired;

	public boolean next(Table table) throws SQLException {
		final TableRelation tableRelation = source.getTableRelation(table);
		if (!tableRelation.isRoot()) {
			return source.next(tableRelation);
		}
		if (rootReselectRequired) {
			source.reSelectRoot();
			rootReselectRequired = false;
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

	static class ColumnMapping {
		ColumnMapping(Table source, Table table) {
			for (int i = 0; i < source.getColumns().size(); i++) {
				Column sourceColumn = source.getColumns().get(i);
				Column column = table.getColumns().get(sourceColumn.getName());
				if (column != null) {
					sourceColumns.add(sourceColumn);
					columns.add(column);
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
}
