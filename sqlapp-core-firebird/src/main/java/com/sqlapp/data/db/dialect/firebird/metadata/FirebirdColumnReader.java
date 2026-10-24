/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.abs;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class FirebirdColumnReader extends ColumnReader {

	protected FirebirdColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Column> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Column column = createColumn(rs);
				result.add(column);
			}
		});
		readArrayBounds(connection, context, result);
		return result;
	}

	private void readArrayBounds(Connection connection, ParametersContext context, List<Column> columns) {
		var arrays = new java.util.HashMap<String, Column>();
		for (Column column : columns) {
			if (column.getArrayDimension() > 0)
				arrays.put(column.getTableName() + "\0" + column.getName(), column);
		}
		if (arrays.isEmpty())
			return;
		var bounds = new java.util.HashMap<String, java.util.List<String>>();
		execute(connection, getSqlNodeCache(FirebirdColumnReader.class).getString("columnDimensions.sql"), context,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						String key = trim(getString(rs, TABLE_NAME)) + "\0" + trim(getString(rs, COLUMN_NAME));
						Column column = arrays.get(key);
						if (column == null)
							return;
						var dimensions = bounds.computeIfAbsent(key, k -> new java.util.ArrayList<>());
						if (rs.getInt("DIMENSION_NUMBER") != dimensions.size())
							throw new SQLException("Non-contiguous Firebird array dimensions for " + column.getName());
						int lower = rs.getInt("LOWER_BOUND");
						int upper = rs.getInt("UPPER_BOUND");
						if (dimensions.isEmpty())
							column.setArrayDimensionLowerBound(lower).setArrayDimensionUpperBound(upper);
						dimensions.add(lower + ":" + upper);
					}
				});
		arrays.forEach((key, column) -> {
			var dimensions = bounds.get(key);
			if (dimensions == null || dimensions.size() != column.getArrayDimension())
				throw new IllegalStateException("Incomplete Firebird array dimensions for " + column.getName());
			column.getSpecifics().put(com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder.ARRAY_BOUNDS,
					String.join(",", dimensions));
		});
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		String tableName = trim(getString(rs, TABLE_NAME));
		String defaultSource = trim(getString(rs, "DEFAULT_SOURCE"));
		Column obj = new Column(trim(getString(rs, COLUMN_NAME)));
		obj.setTableName(tableName);
		int segmentLength = rs.getInt("SEGMENT_LENGTH");
		int length = rs.getInt("FIELD_LENGTH");
		int precision = rs.getInt("FIELD_PRECISION");
		int scale = abs(rs.getInt("FIELD_SCALE"));
		short nullFlag = rs.getShort("NULL_FLAG");
		int type = rs.getInt("FIELD_TYPE");
		int subType = rs.getInt("FIELD_SUB_TYPE");
		obj.setNullable(nullFlag != 1 && rs.getShort("SOURCE_NULL_FLAG") != 1);
		obj.setRemarks(getString(rs, "REMARKS"));
		String checkCondition = getString(rs, "CHECK_CONDITION");
		String computed = trim(getString(rs, "COMPUTED_SOURCE"));
		obj.setArrayDimension(rs.getInt("ARRAY_DIMENSIONS"));
		obj.setCharacterSet(trim(getString(rs, CHARACTER_SET_NAME)));
		obj.setCollation(trim(getString(rs, COLLATION_NAME)));
		obj.setFormula(computed);
		FirebirdUtils.setDefaultConstraint(obj, trim(defaultSource));
		setCheckConstraint(obj, tableName, trim(checkCondition));
		FirebirdUtils.setDbType(obj, type, subType, length, precision, scale, segmentLength);
		if (type == 14 || type == 37) {
			Integer characterLength = getInteger(rs, "CHAR_LEN");
			if (characterLength != null) {
				obj.setLength(characterLength);
			}
		}
		return obj;

	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns.sql");
	}

	/**
	 * Firebirdのチェック条件を一般的な式に変換して設定
	 * 
	 * @param column
	 * @param tableName
	 * @param condition
	 */
	private void setCheckConstraint(Column column, String tableName, String condition) {
		if (isEmpty(condition)) {
			return;
		}
		String val = FirebirdUtils.convertCheckConstraint(condition);
		String replace = val.replaceAll("VALUE", column.getName());
		CheckConstraint constraint = new CheckConstraint("CHECK_" + tableName + column.getName(), replace);
		column.setCheckConstraint(constraint);
	}

}
