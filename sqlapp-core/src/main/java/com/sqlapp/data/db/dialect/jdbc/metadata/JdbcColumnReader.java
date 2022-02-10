/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.jdbc.metadata;

import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.CommonUtils;

/**
 * JDBCのカラム読み込みクラス
 * 
 * @author satoh
 * 
 */
public class JdbcColumnReader extends ColumnReader {

	public JdbcColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		ExResultSet rs = null;
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = new ExResultSet(databaseMetaData.getColumns(
					CommonUtils.coalesce(emptyToNull(this.getCatalogName(context)), emptyToNull(this.getCatalogName()))
					, emptyToNull(getSchemaName(context))
					, emptyToNull(getTableName(context))
					, emptyToNull(getColumnName(context))));
			List<Column> result = list();
			while (rs.next()) {
				Column column = createColumn(rs);
				result.add(column);
			}
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		String colName = getString(rs, COLUMN_NAME);
		int sqlType = rs.getInt("DATA_TYPE");
		String productDataType = getString(rs, "TYPE_NAME");
		Long precision = rs.getLongValue("COLUMN_SIZE");
		Integer scale = rs.getInteger("DECIMAL_DIGITS");
		int nullable = rs.getInt("NULLABLE");
		boolean allowDBNull = false;
		if (nullable != ResultSetMetaData.columnNullableUnknown) {
			if (nullable == ResultSetMetaData.columnNullable) {
				allowDBNull = true;
			}
		}
		String isAutoIncrement = getString(rs, "IS_AUTOINCREMENT");
		boolean autoIncrement = false;
		if ("YES".equalsIgnoreCase(isAutoIncrement)) {
			if (nullable == ResultSetMetaData.columnNullable) {
				autoIncrement = true;
			}
		}
		Column column = new Column(colName);
		this.getDialect().setDbType(sqlType, productDataType, precision,
				scale, column);
		column.setNullable(allowDBNull);
		column.setIdentity(autoIncrement);
		column.setCatalogName(getString(rs, "TABLE_CAT"));
		column.setSchemaName(getString(rs, "TABLE_SCHEM"));
		column.setTableName(getString(rs, TABLE_NAME));
		column.setDefaultValue(getString(rs, "COLUMN_DEF"));
		column.setOctetLength(rs.getInt("CHAR_OCTET_LENGTH"));
		return column;
	}
}
