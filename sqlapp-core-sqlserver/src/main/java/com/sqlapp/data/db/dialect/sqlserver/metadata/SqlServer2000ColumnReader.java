/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.eqIgnoreCase;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.notZero;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
/**
 * SQLServer2000のカラム読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2000ColumnReader extends ColumnReader {

	protected SqlServer2000ColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection,
			ParametersContext context,
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
		setColumnComments(connection, context, result);
		return result;
	}

	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns2000.sql");
	}

	/**
	 * SQLServer2000用のカラムコメント設定
	 * 
	 * @param connection
	 * @param columns
	 */
	protected void setColumnComments(Connection connection,
			ParametersContext context, final List<Column> columns) {
		String schemaName = null;
		String tableName = null;
		for (Column column : columns) {
			if (tableName == null) {
				schemaName = column.getSchemaName();
				tableName = column.getTableName();
				setColumnComments(connection, context, columns, schemaName,
						tableName);
			} else if (!eq(column.getSchemaName(), schemaName)
					|| !eq(column.getTableName(), tableName)) {
				schemaName = column.getSchemaName();
				tableName = column.getTableName();
				setColumnComments(connection, context, columns, schemaName,
						tableName);
			}
			schemaName = column.getSchemaName();
			tableName = column.getTableName();
		}
	}

	/**
	 * SQLServer2000用のカラムコメント設定
	 * 
	 * @param connection
	 * @param columns
	 */
	protected void setColumnComments(Connection connection,
			ParametersContext context, final List<Column> columns,
			String schemaName, String tableName) {
		SqlNode node = getSqlNodeCache().getString("columnComments2000.sql");
		context.put(TABLE_NAME, tableName);
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String columnName = getString(rs, "objname");
				String comment = getString(rs, "value");
				for (Column searchColumn : columns) {
					if (!eqIgnoreCase(columnName, searchColumn.getName())) {
						continue;
					}
					searchColumn.setRemarks(comment);
				}
			}
		});
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		String productDataType = getString(rs, "type_name");
		Long byteLength = getLong(rs, "max_length");
		Long max_length = SqlServerUtils.getMaxLength(productDataType,
				byteLength);
		Long precision = getLong(rs, "precision");
		Integer scale = getInteger(rs, "scale");
		Column obj = new Column(getString(rs, COLUMN_NAME));
		obj.setNullable(rs.getBoolean("is_nullable"));
		obj.setIdentity(rs.getBoolean("is_identity"));
		this.getDialect().setDbType(productDataType,
				notZero(max_length, precision), scale, obj);
		obj.setDefaultValue(unwrap(getString(rs, "default_definition"), '(', ')'));
		// column.setOctetLength(rs.getInt("CHAR_OCTET_LENGTH"));
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setTableName(getString(rs, TABLE_NAME));
		if (obj.isIdentity()) {
			obj.setIdentityStartValue(rs.getLong("ident_seed"));
			obj.setIdentityStep(rs.getLong("ident_increment"));
			obj.setIdentityLastValue(rs.getLong("ident_current"));
		}
		obj.setCollation(getString(rs, COLLATION_NAME));
		obj.setRemarks(getString(rs, "remarks"));
		return obj;
	}
}
