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

package com.sqlapp.data.db.sql;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;
import com.sqlapp.util.function.TriConsumer;

import lombok.Getter;

@Getter
public class SqlSignature {
	private final Table table;
	private final ColumnsHolder full;
	private final ColumnsHolder primaryKey;
	private final ColumnsHolder uniqueKey;
	private final ColumnsHolder notNullUniqueIndex;
	private ColumnSelectionStrategy columnSelectionStrategy = ColumnSelectionStrategy.PRIMARY_KEY_OR_UNIQUE_KEY_OR_NOT_NULL_UNIQUE_INDEX;
	private ColumnsHolder selectedColumnsHolder;
	private List<Row> rows;

	static {

	}

	public SqlSignature(Table table, List<Row> rows) {
		this.table = table;
		this.rows = rows;
		Set<Column> foreignKeyColumns = ColumnAnalyzer.FOREIGN_KEYS.getKeyColumns(table, rows);
		this.full = new ColumnsHolder(ColumnAnalyzer.FULL, ColumnAnalyzer.FULL.getKeyColumns(table, rows),
				ColumnAnalyzer.FULL.getKeyColumnsList(table, rows), foreignKeyColumns, rows);
		this.primaryKey = new ColumnsHolder(ColumnAnalyzer.PRIMARY_KEY,
				ColumnAnalyzer.PRIMARY_KEY.getKeyColumns(table, rows),
				ColumnAnalyzer.PRIMARY_KEY.getKeyColumnsList(table, rows), foreignKeyColumns, rows);
		this.uniqueKey = new ColumnsHolder(ColumnAnalyzer.UNIQUE_KEY,
				ColumnAnalyzer.UNIQUE_KEY.getKeyColumns(table, rows),
				ColumnAnalyzer.UNIQUE_KEYS.getKeyColumnsList(table, rows), foreignKeyColumns, rows);
		this.notNullUniqueIndex = new ColumnsHolder(ColumnAnalyzer.NOT_NULL_UNIQUE_INDEX,
				ColumnAnalyzer.NOT_NULL_UNIQUE_INDEX.getKeyColumns(table, rows),
				ColumnAnalyzer.NOT_NULL_UNIQUE_INDEXES.getKeyColumnsList(table, rows), foreignKeyColumns, rows);
	}

	public void forEach(Consumer<ColumnsHolder> cons) {
		if (!this.primaryKey.isEmptyKey()) {
			cons.accept(this.primaryKey);
		}
		if (!this.uniqueKey.isEmptyKey()) {
			cons.accept(this.uniqueKey);
		}
		if (!this.notNullUniqueIndex.isEmptyKey()) {
			cons.accept(this.notNullUniqueIndex);
		}
	}

	public boolean hasUniqueKey() {
		return !uniqueKey.isEmptyKey();
	}

	public boolean hasPrimaryKey() {
		return !primaryKey.isEmptyKey();
	}

	public boolean hasNotNullUniqueIndex() {
		return !notNullUniqueIndex.isEmptyKey();
	}

	public void reCalculate() {
		this.primaryKey.reCalculate();
		this.uniqueKey.reCalculate();
		this.notNullUniqueIndex.reCalculate();
	}

	public void reCalculate(List<Row> rows) {
		this.rows = rows;
		this.primaryKey.reCalculate();
		this.uniqueKey.reCalculate();
		this.notNullUniqueIndex.reCalculate();
	}

	public void setColumnSelectionStrategy(ColumnSelectionStrategy columnSelectionStrategy) {
		this.columnSelectionStrategy = columnSelectionStrategy;
		if (columnSelectionStrategy != null) {
			reCalculate();
			this.selectedColumnsHolder = columnSelectionStrategy.get(this);
			if (this.selectedColumnsHolder == ColumnsHolder.EMPTY_COLUMN_HOLDER) {
				this.selectedColumnsHolder = columnSelectionStrategy.getWithoutCheck(this);
			}
		}
	}

	public ColumnsHolder getSelectedColumnsHolder() {
		return this.selectedColumnsHolder;
	}

	public void forEachKeyColumn(ColumnHolderConsumer consumer) {
		int i = 0;
		for (Column column : getSelectedColumnsHolder().getKeyColumns()) {
			consumer.accept(getSelectedColumnsHolder(), i, column);
			i++;
		}
	}

	static interface ColumnHolderConsumer extends TriConsumer<ColumnsHolder, Integer, Column> {

	}

	@Getter
	public static class ColumnsHolder {
		private final Set<Column> keyColumns;
		private Set<Column> notNullKeyColumns;
		private Set<Column> nullKeyColumns;
		private final Set<Column> foreingKeyCommonColumns;
		private Set<Column> nullForeingKeyCommonColumns;
		private final List<Set<Column>> allKeyColumnsList;
		private final Set<Column> allKeyColumns;
		private final ColumnAnalyzer columnAnalyzer;
		private final List<Row> rows;

		public static final ColumnsHolder EMPTY_COLUMN_HOLDER = new ColumnsHolder();

		ColumnsHolder() {
			this.columnAnalyzer = ColumnAnalyzer.FULL;
			this.keyColumns = Collections.emptySet();
			this.notNullKeyColumns = Collections.emptySet();
			this.nullKeyColumns = Collections.emptySet();
			this.allKeyColumnsList = Collections.emptyList();
			this.allKeyColumns = Collections.emptySet();
			this.foreingKeyCommonColumns = Collections.emptySet();
			this.nullForeingKeyCommonColumns = Collections.emptySet();
			this.rows = Collections.emptyList();
		}

		ColumnsHolder(ColumnAnalyzer columnAnalyzer, Set<Column> keyColumns, List<Set<Column>> allKeyColumnsList,
				Set<Column> foreignKeyColumns, List<Row> rows) {
			this.columnAnalyzer = columnAnalyzer;
			this.rows = rows;
			this.keyColumns = keyColumns;
			this.notNullKeyColumns = getNotNullColumns(rows, keyColumns);
			this.nullKeyColumns = getNullColumns(rows, keyColumns);
			this.allKeyColumnsList = allKeyColumnsList;
			this.allKeyColumns = toColumns(allKeyColumnsList);
			this.foreingKeyCommonColumns = ColumnAnalyzer.and(keyColumns, foreignKeyColumns);
			this.nullForeingKeyCommonColumns = getNullColumns(rows, foreingKeyCommonColumns);
		}

		protected void reCalculate() {
			this.notNullKeyColumns = getNotNullColumns(rows, keyColumns);
			this.nullKeyColumns = getNullColumns(rows, keyColumns);
			this.nullForeingKeyCommonColumns = getNullColumns(rows, foreingKeyCommonColumns);
		}

		public void forEachKeyColumn(BiConsumer<Integer, Column> consumer) {
			int i = 0;
			for (Column column : getKeyColumns()) {
				consumer.accept(i, column);
				i++;
			}
		}

		public boolean isPrimaryKey() {
			return columnAnalyzer == ColumnAnalyzer.PRIMARY_KEY;
		}

		public boolean isUniqueKey() {
			return columnAnalyzer == ColumnAnalyzer.UNIQUE_KEY;
		}

		public boolean isNotNullUniqueIndex() {
			return columnAnalyzer == ColumnAnalyzer.NOT_NULL_UNIQUE_INDEX;
		}

		public boolean isEmptyKey() {
			return keyColumns.isEmpty();
		}

		public boolean hasKeyFullValues() {
			return nullKeyColumns.size() == 0;
		}

		public boolean hasNullForeingKeyColumns() {
			return !nullForeingKeyCommonColumns.isEmpty();
		}

		public boolean hasMultiKey() {
			return keyColumns.size() != allKeyColumns.size();
		}

		private Set<Column> getNullColumns(List<Row> rows, Set<Column> columns) {
			if (rows.isEmpty()) {
				return Collections.emptySet();
			}
			Set<Column> result = CommonUtils.linkedSet();
			Row row = rows.get(0);
			for (Column column : columns) {
				if (row.get(column) == null) {
					result.add(column);
				}
			}
			return result;
		}

		private Set<Column> getNotNullColumns(List<Row> rows, Set<Column> columns) {
			if (rows.isEmpty()) {
				return Collections.emptySet();
			}
			Set<Column> result = CommonUtils.linkedSet();
			Row row = rows.get(0);
			for (Column column : columns) {
				if (row.get(column) != null) {
					result.add(column);
				}
			}
			return result;
		}

		private Set<Column> toColumns(Collection<Set<Column>> allKeyColumns) {
			Set<Column> result = CommonUtils.set();
			for (Set<Column> columns : allKeyColumns) {
				result.addAll(columns);
			}
			return result;
		}

		public Row findAndCopy(Row compareRow, List<Row> rows, Set<String> resultSetColumnNames, Set<Integer> rowNums) {
			Row row = null;
			int index = -1;
			boolean find = false;
			for (int rowIndex : rowNums) {
				index = rowIndex;
				row = rows.get(rowIndex);
				find = true;
				for (Column column : keyColumns) {
					if (!Objects.equals(row.get(column), compareRow.get(column))) {
						find = false;
						break;
					}
				}
				if (find) {
					break;
				}
			}
			if (find) {
				for (String name : resultSetColumnNames) {
					row.put(name, compareRow.get(name));
				}
				rowNums.remove(index);
				return row;
			}
			return null;
		}

		public Row find(Row compareRow, List<Row> rows, Set<Integer> rowNums) {
			return findAndCopy(compareRow, rows, Collections.emptySet(), rowNums);
		}

		@Override
		public String toString() {
			ToStringBuilder builder = new ToStringBuilder();
			builder.add("columnAnalyzer", columnAnalyzer);
			builder.addColumnNames("keyCocolumns", getKeyColumns());
			builder.addColumnNames("nullKeyColumns", getNullKeyColumns());
			builder.addColumnNames("nullForeingKeyCommonColumns", getNullForeingKeyCommonColumns());
			return builder.toString();
		}
	}

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder();
		builder.add("table", table.getName());
		builder.add("primaryKey", primaryKey);
		builder.add("uniqueKey", uniqueKey);
		builder.add("selectedColumnsHolder", selectedColumnsHolder);
		builder.add("columnSelectionStrategy", columnSelectionStrategy);
		return builder.toString();
	}

}
