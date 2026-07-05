/**
 * Copyright (C) 2026-2027 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.sql.ReturningColumnStrategy;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CorrelationRowNotFoundException;
import com.sqlapp.jdbc.sql.node.Node;
import com.sqlapp.util.CommonUtils;

public enum CorrelationStrategy {
	RETURN_SOURCE_ROWID {
		@Override
		public boolean isReturnSourceRowid() {
			return true;
		}

		@Override
		public void setResultSet(ResultSet resultSet, Table table) throws SQLException {
			ResultSetMetaData metaData = resultSet.getMetaData();
			int count = metaData.getColumnCount();
			while (resultSet.next()) {
				int rowNo = resultSet.getInt(Node.ROW_NO);
				Row row = table.getRows().get(rowNo);
				for (int i = 1; i <= count; i++) {
					String name = metaData.getColumnLabel(i);
					row.put(name, resultSet.getObject(i));
				}
			}
		}
	},
	MATCH_BY_UNIQUE_KEY_AND_PK {
		@Override
		public void setResultSet(ResultSet resultSet, Table table) throws SQLException {
			ResultSetMetaData metaData = resultSet.getMetaData();
			Set<Integer> rowNums = getRowNoSet(table);
			Set<String> resultSetColumnNames = getColumnNames(metaData);
			Set<Column> pkColumns = ReturningColumnStrategy.PRIMARY_KEY.getKeyColumns(table);
			Set<Set<Column>> columnsSetTmp = ReturningColumnStrategy.PRIMARY_AND_ALL_UNIQUE_KEYS_AND_ALL_INDEXES.getKeyColumnsSet(table);
			Set<Set<Column>> ukColumnsSet = filterColumnsSet(columnsSetTmp, resultSetColumnNames);
			while (resultSet.next()) {
				Row compareRow = table.newRow();
				for (String columnName : resultSetColumnNames) {
					compareRow.put(columnName, resultSet.getObject(columnName));
				}
				boolean find = false;
				for (Set<Column> columns : ukColumnsSet) {
					Row row = find(compareRow, table, columns, resultSetColumnNames, rowNums);
					if (row != null) {
						find = true;
						break;
					}
				}
				if (!find) {
					Row row = find(compareRow, table, pkColumns, resultSetColumnNames, rowNums);
					if (row == null) {
						throw new CorrelationRowNotFoundException(table, compareRow);
					}
				}
			}
		}
	},
	TEMP_TABLE {
	},;

	public boolean isReturnSourceRowid() {
		return false;
	}

	protected Set<Set<Column>> filterColumnsSet(Set<Set<Column>> columnsSet, Set<String> columnNames) {
		Set<Set<Column>> result = CommonUtils.linkedSet();
		for (Set<Column> columns : columnsSet) {
			if (matchColumns(columns, columnNames)) {
				result.add(columns);
			}
		}
		return result;
	}

	private boolean matchColumns(Set<Column> columns, Set<String> columnNames) {
		for (Column column : columns) {
			if (!columnNames.contains(column.getName())) {
				return false;
			}
		}
		return true;
	}

	protected Row find(Row compareRow, Table table, Set<Column> columns, Set<String> resultSetColumnNames,
			Set<Integer> rowNums) throws SQLException {
		Row row = null;
		int index = -1;
		boolean find = false;
		for (int rowIndex : rowNums) {
			index = rowIndex;
			row = table.getRows().get(rowIndex);
			find = true;
			for (Column column : columns) {
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

	protected Set<String> getColumnNames(ResultSetMetaData metaData) throws SQLException {
		Set<String> set = CommonUtils.linkedSet();
		int count = metaData.getColumnCount();
		for (int i = 1; i <= count; i++) {
			set.add(metaData.getColumnLabel(i));
		}
		return set;
	}

	public void setResultSet(ResultSet resultSet, Table table) throws SQLException {

	}

	protected Set<Integer> getRowNoSet(Table table) {
		Set<Integer> set = CommonUtils.linkedSet();
		for (int i = 0; i < table.getRows().size(); i++) {
			set.add(i);
		}
		return set;
	}
}
