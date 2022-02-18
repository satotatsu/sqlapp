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

import static com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2005IndexReader.STATISTICS_NORECOMPUTE;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * SqlServer2005のユニーク制約読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2005UniqueConstraintReader extends
		SqlServer2000UniqueConstraintReader {

	protected SqlServer2005UniqueConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, UniqueConstraint> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = getString(rs, CATALOG_NAME);
				String schema_name = getString(rs, SCHEMA_NAME);
				String name = getString(rs, CONSTRAINT_NAME);
				UniqueConstraint obj = map.get(catalog_name, schema_name, name);
				if (obj == null) {
					obj = createUniqueConstraint(rs);
					map.put(catalog_name, schema_name, name, obj);
				}
				String columnName = getString(rs, COLUMN_NAME);
				boolean includedColumn = rs.getInt("is_included_column") == 1;
				if (rs.getInt("is_descending_key") == 1) {
					obj.getColumns().add(new Column(columnName), Order.Desc)
							.setIncludedColumn(includedColumn);
				} else {
					obj.getColumns().add(new Column(columnName), Order.Asc)
							.setIncludedColumn(includedColumn);
				}
			}
		});
		return map.toList();
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("uniqueConstraints2005.sql");
	}

	protected UniqueConstraint createUniqueConstraint(ExResultSet rs)
			throws SQLException {
		UniqueConstraint obj = super.createUniqueConstraint(rs);
		setSpecifics(rs, STATISTICS_NORECOMPUTE, obj);
		return obj;
	}
}
