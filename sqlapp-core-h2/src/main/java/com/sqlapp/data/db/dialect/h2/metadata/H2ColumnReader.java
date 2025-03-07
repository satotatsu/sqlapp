/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;

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

/**
 * HSQLのカラム作成クラス
 * 
 * @author satoh
 * 
 */
public class H2ColumnReader extends ColumnReader {

	public H2ColumnReader(Dialect dialect) {
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
				String table_name = getString(rs, TABLE_NAME);
				String column_name = getString(rs, COLUMN_NAME);
				Column column = new Column(column_name);
				column.setCatalogName(getString(rs, TABLE_CATALOG));
				column.setSchemaName(getString(rs, TABLE_SCHEMA));
				column.setTableName(table_name);
				column.setNullable(rs.getInt("NULLABLE") == 1);
				column.setDefaultValue(getString(rs, "COLUMN_DEFAULT"));
				String productDataType = getString(rs, "TYPE_NAME");
				column.setLength(rs.getInt("CHARACTER_MAXIMUM_LENGTH"));
				column.setOctetLength(rs.getInt("CHARACTER_OCTET_LENGTH"));
				column.setScale(rs.getInt("NUMERIC_SCALE"));
				column.setDataTypeName(productDataType);
				column.setSequenceName(getString(rs, "SEQUENCE_NAME"));
				String collationName = getString(rs, "COLLATION_NAME");
				if (!"OFF".equalsIgnoreCase(collationName)) {
					column.setCollation(collationName);
				}
				String ccString = getString(rs, "CHECK_CONSTRAINT");
				if (!isEmpty(ccString)) {
					CheckConstraint cc = new CheckConstraint(table_name + "_"
							+ table_name + " CHECK", ccString, column);
					column.setCheckConstraint(cc);
				}
				column.setRemarks(getString(rs, REMARKS));
				result.add(column);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns.sql");
	}
}
