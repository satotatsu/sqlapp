/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.spanner.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * Spanner Column Reader
 * 
 * @author satoh
 * 
 */
public class SpannerColumnReader extends ColumnReader {

	public SpannerColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Column> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Column obj = createColumn(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column obj = new Column(getString(rs, COLUMN_NAME));
		boolean nullable = toBoolean(getString(rs, "IS_NULLABLE"));
		String data_type = getString(rs, "SPANNER_TYPE");
		obj.setNullable(nullable);
		this.getDialect().setDbType(data_type,
				null, null, obj);
		return obj;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns.sql");
	}
}
