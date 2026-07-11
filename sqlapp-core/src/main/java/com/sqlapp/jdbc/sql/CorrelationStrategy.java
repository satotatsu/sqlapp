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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnSelectionStrategy;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CorrelationRowNotFoundException;
import com.sqlapp.jdbc.sql.node.Node;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DbUtils;

public enum CorrelationStrategy {
	/** No Database */
	BY_INDEX {
		@Override
		public boolean isReturnIndex() {
			return true;
		}

		@Override
		public void handleStatementResult(PreparedStatement statement, SqlParameterCollection sqlParameters)
				throws SQLException {
			if (statement.execute()) {
				try (ResultSet rs = statement.getGeneratedKeys()) {
					setResultSet(rs, sqlParameters.getTable());
				}
			}
		}

		@Override
		public PreparedStatement createPreparedStatement(final Connection connection,
				final SqlParameterCollection sqlParameters) throws SQLException {
			Table table = sqlParameters.getTable();
			if (table == null) {
				return super.createPreparedStatement(connection, sqlParameters);
			}
			String[] array = table.getColumns().stream().map(c -> c.getName()).toArray(i -> new String[i]);
			PreparedStatement statement = connection.prepareStatement(sqlParameters.getSql(), array);
			return statement;
		}

		@Override
		protected void setResultSet(ResultSet resultSet, Table table) throws SQLException {
			ResultSetMetaData metaData = resultSet.getMetaData();
			int count = metaData.getColumnCount();
			int rowNo = 0;
			while (resultSet.next()) {
				Row row = table.getRows().get(rowNo++);
				for (int i = 1; i <= count; i++) {
					String name = metaData.getColumnLabel(i);
					row.put(name, resultSet.getObject(i));
				}
			}
		}
	},
	/** FOR SQL Server */
	BY_SOURCE_ROWID {
		@Override
		public boolean isReturnSourceRowid() {
			return true;
		}

		@Override
		protected void setResultSet(ResultSet resultSet, Table table) throws SQLException {
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
	/** FOR DB2 */
	BY_KEY {
		@Override
		protected void setResultSet(final ResultSet resultSet, final Table table) throws SQLException {
			final ResultSetMetaData metaData = resultSet.getMetaData();
			int count = metaData.getColumnCount();
			final Set<Integer> rowNums = getRowNoSet(table.getRows());
			final Set<String> resultSetColumnNames = DbUtils.getColumnNames(metaData);
			final Set<Set<Column>> columnsSetTmp = ColumnSelectionStrategy.PRIMARY_KEY_AND_UNIQUE_KEYS_AND_NOT_NULL_UNIQUE_INDEXES
					.getKeyColumnsSet(table);
			final Set<Set<Column>> ukColumnsSet = filterColumnsSet(columnsSetTmp, resultSetColumnNames);
			final Set<Column> pkColumns = ColumnSelectionStrategy.PRIMARY_KEY.getKeyColumns(table);
			while (resultSet.next()) {
				final Row compareRow = table.newRow();
				for (String columnName : resultSetColumnNames) {
					compareRow.put(columnName, resultSet.getObject(columnName));
				}
				Row row = null;
				boolean find = false;
				for (final Set<Column> columns : ukColumnsSet) {
					row = find(compareRow, table, columns, resultSetColumnNames, rowNums);
					if (row != null) {
						find = true;
						break;
					}
				}
				if (!find) {
					row = find(compareRow, table, pkColumns, resultSetColumnNames, rowNums);
					if (row == null) {
						throw new CorrelationRowNotFoundException(table, compareRow);
					}
				}
				if (row != null) {
					for (int i = 1; i <= count; i++) {
						String name = metaData.getColumnLabel(i);
						row.put(name, resultSet.getObject(i));
					}
				}
			}
		}
	},
	BY_TEMP_TABLE {
	},;

	public boolean isReturnIndex() {
		return false;
	}

	public boolean isReturnSourceRowid() {
		return false;
	}

	public void handleStatementResult(PreparedStatement statement, SqlParameterCollection sqlParameters)
			throws SQLException {
		try (ResultSet rs = statement.executeQuery()) {
			setResultSet(rs, sqlParameters.getTable());
		}
	}

	protected void setResultSet(ResultSet resultSet, Table table) throws SQLException {

	}

	public PreparedStatement createPreparedStatement(final Connection connection,
			final SqlParameterCollection sqlParameters) throws SQLException {
		final PreparedStatement statement = connection.prepareStatement(sqlParameters.getSql(),
				ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT);
		if (sqlParameters.getFetchSize() != null) {
			statement.setFetchSize(sqlParameters.getFetchSize());
		}
		return statement;
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

	public static Row find(Row compareRow, Table table, Set<Column> columns, Set<String> resultSetColumnNames,
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

	public static Set<Integer> getRowNoSet(List<Row> rows) {
		Set<Integer> set = CommonUtils.linkedSet();
		for (int i = 0; i < rows.size(); i++) {
			set.add(i);
		}
		return set;
	}
}
