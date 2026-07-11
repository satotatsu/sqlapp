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

import java.util.Collections;
import java.util.Set;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnSelectionStrategy;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;

@Getter
public class SqlSignature {
	private ColumnsHolder primaryKey;
	private ColumnsHolder uniqueKey;
	private ColumnsHolder notNullUniqueIndex;

	public SqlSignature(Table table) {
		Set<Column> foreignKeyColumns = ColumnSelectionStrategy.FOREIGN_KEYS.getKeyColumns(table);
		this.primaryKey = new ColumnsHolder(table, ColumnSelectionStrategy.PRIMARY_KEY.getKeyColumns(table),
				ColumnSelectionStrategy.PRIMARY_KEY.getKeyColumnsSet(table), foreignKeyColumns);
		this.uniqueKey = new ColumnsHolder(table, ColumnSelectionStrategy.UNIQUE_KEY.getKeyColumns(table),
				ColumnSelectionStrategy.UNIQUE_KEYS.getKeyColumnsSet(table), foreignKeyColumns);
		this.notNullUniqueIndex = new ColumnsHolder(table,
				ColumnSelectionStrategy.NOT_NULL_UNIQUE_INDEX.getKeyColumns(table),
				ColumnSelectionStrategy.NOT_NULL_UNIQUE_INDEXES.getKeyColumnsSet(table), foreignKeyColumns);
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

	@Getter
	public static class ColumnsHolder {
		private final Set<Column> keyColumns;
		private final Set<Column> notNullKeyColumns;
		private final Set<Column> nullKeyColumns;
		private final Set<Column> foreingKeyCommonColumns;
		private final Set<Set<Column>> allKeyColumnsSet;
		private final Set<Column> allKeyColumns;

		ColumnsHolder(Table table, Set<Column> keyColumns, Set<Set<Column>> allKeyColumnsSet,
				Set<Column> foreignKeyColumns) {
			this.keyColumns = keyColumns;
			this.notNullKeyColumns = getNotNullColumns(table, keyColumns);
			this.nullKeyColumns = getNullColumns(table, keyColumns);
			this.allKeyColumnsSet = allKeyColumnsSet;
			this.allKeyColumns = toColumns(allKeyColumnsSet);
			this.foreingKeyCommonColumns = ColumnSelectionStrategy.and(keyColumns, foreignKeyColumns);
		}

		public boolean isEmptyKey() {
			return keyColumns.isEmpty();
		}

		public boolean hasMultiKey() {
			return keyColumns.size() != allKeyColumns.size();
		}

		private Set<Column> getNullColumns(Table table, Set<Column> columns) {
			if (table.getRows().isEmpty()) {
				return Collections.emptySet();
			}
			Set<Column> result = CommonUtils.linkedSet();
			Row row = table.getRows().get(0);
			for (Column column : columns) {
				if (row.get(column) == null) {
					result.add(column);
				}
			}
			return result;
		}

		private Set<Column> getNotNullColumns(Table table, Set<Column> columns) {
			if (table.getRows().isEmpty()) {
				return Collections.emptySet();
			}
			Set<Column> result = CommonUtils.linkedSet();
			Row row = table.getRows().get(0);
			for (Column column : columns) {
				if (row.get(column) != null) {
					result.add(column);
				}
			}
			return result;
		}

		private Set<Column> toColumns(Set<Set<Column>> allKeyColumns) {
			Set<Column> result = CommonUtils.set();
			for (Set<Column> columns : allKeyColumns) {
				result.addAll(columns);
			}
			return result;
		}
	}
}
