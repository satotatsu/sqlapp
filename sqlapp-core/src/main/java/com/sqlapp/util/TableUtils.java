/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.set;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

/**
 * DataTableの操作を便利にするためのラッパーユーティリティ
 */
public class TableUtils {
	private TableUtils(Table table) {
	}

	/**
	 * 指定した名前のカラムを除いたカラムの一覧を取得するメソッド
	 * 
	 * @param table
	 * @param exceptColumnNames
	 */
	public static List<String> getColumnNames(Table table,
			String... exceptColumnNames) {
		List<String> result = list();
		Set<String> excepts = set();
		for (String exceptColumnName : exceptColumnNames) {
			excepts.add(exceptColumnName);
		}
		for (Column column : table.getColumns()) {
			if (!result.contains(column.getName())) {
				if (!excepts.contains(column.getName())) {
					result.add(column.getName());
				}
			}
		}
		return result;
	}

	/**
	 * 指定した名前のカラムを取得するメソッド
	 * 
	 * @param table
	 *            対象のDataTable
	 * @param columnNames
	 *            カラム名
	 */
	public static List<Column> getColumns(Table table, String... columnNames) {
		List<Column> result = list();
		if (columnNames == null) {
			return result;
		}
		int size = columnNames.length;
		for (int i = 0; i < size; i++) {
			String columnName = columnNames[i];
			if (table.getColumns().contains(columnName)) {
				result.add(table.getColumns().get(columnName));
			}
		}
		return result;
	}

	/**
	 * 指定した名前のカラムを取得するメソッド
	 * 
	 * @param table
	 *            対象のDataTable
	 * @param columnNames
	 *            カラム名
	 */
	public static List<Column> getColumns(Table table, List<String> columnNames) {
		List<Column> result = list();
		if (columnNames == null) {
			return result;
		}
		int size = columnNames.size();
		for (int i = 0; i < size; i++) {
			String columnName = columnNames.get(i);
			if (table.getColumns().contains(columnName)) {
				result.add(table.getColumns().get(columnName));
			}
		}
		return result;
	}

	/**
	 * AutoIncrementカラムの取得
	 * 
	 * @param table
	 *            対象のデータテーブル
	 * @return AutoIncrementカラムのリスト(通常1個)
	 */
	public static List<Column> getAutoIncrementColumn(Table table) {
		List<Column> columns = filterColumns(table, new Predicate<Column>() {
			@Override
			public boolean test(Column column) {
				return column.isIdentity();
			}
		});
		return columns;
	}

	/**
	 * filterで指定されたカラムの取得
	 * 
	 * @param table
	 * @param filter
	 */
	public static List<Column> filterColumns(Table table, Predicate<Column> filter) {
		List<Column> result = list();
		ColumnCollection columns = table.getColumns();
		int size = columns.size();
		for (int i = 0; i < size; i++) {
			Column column = columns.get(i);
			if (filter.test(column)) {
				result.add(column);
			}
		}
		return result;
	}

	/**
	 * 行データのTrim処理
	 * 
	 */
	public static void trim(Table table) {
		for (Row row : table.getRows()) {
			for (Column column : table.getColumns()) {
				Object obj = row.get(column);
				if (obj != null && obj instanceof String) {
					row.put(column, ((String) obj).trim());
				}
			}
		}
	}
}