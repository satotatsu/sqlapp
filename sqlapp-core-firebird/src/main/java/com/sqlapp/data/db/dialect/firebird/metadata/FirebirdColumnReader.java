/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
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
		return result;
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
		if (nullFlag == 1) {
			obj.setNullable(true);
		} else {
			obj.setNullable(false);
		}
		String checkCondition = getString(rs, "CHECK_CONDITION");
		String computed = trim(getString(rs, "COMPUTED_SOURCE"));
		int lowerBound = rs.getInt("LOWER_BOUND");
		int upperBound = rs.getInt("UPPER_BOUND");
		if (upperBound > 0) {
			obj.setArrayDimension(1);
			obj.setArrayDimensionLowerBound(lowerBound);
			obj.setArrayDimensionUpperBound(upperBound);
		}
		obj.setFormula(computed);
		FirebirdUtils.setDefaultConstraint(obj, trim(defaultSource));
		setCheckConstraint(obj, tableName, trim(checkCondition));
		FirebirdUtils.setDbType(obj, type, subType, length, precision, scale, segmentLength);
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
		CheckConstraint constraint = new CheckConstraint("CHECK_" + tableName + column.getName(), replace, column);
		column.setCheckConstraint(constraint);
	}

}
