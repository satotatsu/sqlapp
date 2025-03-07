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

package com.sqlapp.data.db.dialect.jdbc.metadata;

import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

public class JdbcMetadataUtils {

	public static List<Table> getMetadata(Connection connection,
			String catalogName, String schemaName, String tableName,
			String[] tableTypes) throws SQLException {
		ResultSet resultSet = null;
		List<Table> result = list();
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			resultSet = databaseMetaData
					.getTables(emptyToNull(catalogName),
							emptyToNull(schemaName), emptyToNull(tableName),
							tableTypes);
			while (resultSet.next()) {
				String remarks = resultSet.getString("REMARKS");
				String tableType = resultSet.getString("TABLE_TYPE");
				String name = resultSet.getString("TABLE_NAME");
				Table table;
				if ("VIEW".equals(tableType)) {
					table = new View(name);
				} else {
					table = new Table(name);
				}
				table.setCatalogName(resultSet.getString("TABLE_CAT"));
				table.setSchemaName(resultSet.getString("TABLE_SCHEM"));
				table.setRemarks(remarks);
				result.add(table);
			}
			return result;
		} finally {
			close(resultSet);
		}
	}

	/**
	 * ResultSetのメタデータのカラム情報を読み込みます
	 * 
	 * @param resultSet
	 * @param dialect
	 *            DB方言
	 * @throws SQLException
	 */
	public static List<Column> getColumnMetadata(ResultSet resultSet,
			Dialect dialect) throws SQLException {
		ResultSetMetaData metaData = resultSet.getMetaData();
		int colCount = metaData.getColumnCount();
		List<Column> result = list(colCount);
		for (int i = 1; i <= colCount; i++) {
			String colName = metaData.getColumnName(i);
			int sqlType = metaData.getColumnType(i);
			String productDataType = metaData.getColumnTypeName(i);
			long precision = metaData.getPrecision(i);
			int nullable = metaData.isNullable(i);
			int scale = metaData.getScale(i);
			boolean allowDBNull = false;
			if (nullable != ResultSetMetaData.columnNullableUnknown) {
				if (nullable == ResultSetMetaData.columnNullable) {
					allowDBNull = true;
				}
			}
			boolean autoIncrement = metaData.isAutoIncrement(i);
			Column column = new Column(colName);
			dialect.setDbType(sqlType, productDataType, precision,
					scale, column);
			column.setNullable(allowDBNull);
			column.setIdentity(autoIncrement);
			result.add(column);
		}
		return result;
	}
}
