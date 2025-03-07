/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.metadata;

import static com.sqlapp.util.CommonUtils.list;

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
 * SAP HANA Column Reader
 * 
 * @author satoh
 * 
 */
public class SapHanaColumnReader extends ColumnReader {

	protected SapHanaColumnReader(Dialect dialect) {
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
		return result;
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		long max_length = rs.getLong("LENGTH");
		Integer scale = getInteger(rs, "SCALE");
		String productDataType = getString(rs, "DATA_TYPE_NAME");
		boolean allowDBNull = "TRUE".equalsIgnoreCase(getString(rs,
				"IS_NULLABLE"));
		Column obj = new Column(getString(rs, COLUMN_NAME));
		obj.setNullable(allowDBNull);
		this.getDialect().setDbType(productDataType, max_length, scale, obj);
		obj.setTableName(getString(rs, TABLE_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setDefaultValue(getString(rs, "DEFAULT_VALUE"));
		obj.setRemarks(getString(rs, "COMMENTS"));
		return obj;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns.sql");
	}
}
