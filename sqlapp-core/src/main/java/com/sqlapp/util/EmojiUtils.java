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

package com.sqlapp.util;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.db.sql.ColumnAnalyzer;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

import lombok.Getter;
import lombok.Setter;

public final class EmojiUtils {

	public static String getPrimaryKey() {
		return "🔑";
	}

	public static String getPrimaryKeyFull() {
		return getPrimaryKey() + "PK";
	}

	public static String getUniqueKey() {
		return "🔑";
	}

	public static String getUniqueKeyFull() {
		return getUniqueKey() + "UK";
	}

	public static String getForeignKey() {
		return "🔗";
	}

	public static String getForeignKeyFull() {
		return getForeignKey() + "FK";
	}

	public static String getIndex() {
		return "⚡";
	}

	public static String getIndexFull() {
		return getIndexFull() + "Index";
	}

	public static String getCaluculated() {
		return "🧮";
	}

	public static String getCaluculatedFull() {
		return getCaluculated() + "Generated";
	}

	public static String getKeyFullset() {
		return getPrimaryKeyFull() + getUniqueKeyFull() + getForeignKeyFull() + getIndexFull();
	}

	public static String getValue(Column column, Set<Column> uniquKeyColumns, Set<Column> foreignKeyColumns,
			Set<Column> indexColumns) {
		if (column.isPrimaryKey()) {
			return getPrimaryKey();
		}
		if (uniquKeyColumns.contains(column)) {
			return getUniqueKey();
		}
		if (foreignKeyColumns.contains(column)) {
			return getForeignKey();
		}
		if (indexColumns.contains(column)) {
			return getIndex();
		}
		return null;
	}

	public static Map<Column, ColumnEmojiHolder> getEmojiInfo(Table table) {
		Set<Column> ukColumns = ColumnAnalyzer.UNIQUE_KEY.getKeyColumns(table, Collections.emptyList());
		Set<Column> fkColumns = ColumnAnalyzer.FOREIGN_KEY.getKeyColumns(table, Collections.emptyList());
		Set<Column> indexColumns = ColumnAnalyzer.INDEXES.getKeyColumns(table, Collections.emptyList());
		Map<Column, ColumnEmojiHolder> result = CommonUtils.linkedMap();
		for (Column column : table.getColumns()) {
			ColumnEmojiHolder holder = new ColumnEmojiHolder(column);
			if (column.isPrimaryKey()) {
				holder.setPrimaryKey(getPrimaryKey());
				holder.setPrimaryKeyFull(getPrimaryKeyFull());
			}
			if (column.isFormulaPersisted()) {
				holder.setCaluculated(getCaluculated());
				holder.setCaluculatedFull(getCaluculatedFull());
			}
			if (ukColumns.contains(column)) {
				holder.setUniqueKey(getUniqueKey());
				holder.setUniqueKeyFull(getUniqueKeyFull());
			}
			if (fkColumns.contains(column)) {
				holder.setForeignKey(getForeignKey());
				holder.setForeignKeyFull(getForeignKeyFull());
			}
			if (indexColumns.contains(column)) {
				holder.setIndex(getIndex());
				holder.setIndexFull(getIndexFull());
			}
		}
		return result;
	}

	@Getter
	@Setter
	public static class ColumnEmojiHolder {
		private final Column column;
		private String primaryKey;
		private String primaryKeyFull;
		private String uniqueKey;
		private String uniqueKeyFull;
		private String foreignKey;
		private String foreignKeyFull;
		private String index;
		private String indexFull;
		private String caluculated;
		private String caluculatedFull;

		public ColumnEmojiHolder(final Column column) {
			this.column = column;
		}

		public String getType() {
			if (primaryKey != null) {
				return primaryKey;
			}
			if (uniqueKey != null) {
				return uniqueKey;
			}
			if (index != null) {
				return index;
			}
			if (caluculated != null) {
				return caluculated;
			}
			return null;
		}

		public String getPrefix() {
			if (primaryKey != null) {
				return primaryKey;
			}
			if (uniqueKey != null) {
				return uniqueKey;
			}
			if (index != null) {
				return index;
			}
			return null;
		}

		public String getSuffix() {
			if (foreignKey != null) {
				return foreignKey;
			}
			return null;
		}
	}
}
