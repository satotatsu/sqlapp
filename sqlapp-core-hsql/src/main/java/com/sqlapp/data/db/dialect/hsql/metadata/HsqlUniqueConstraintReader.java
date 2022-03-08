/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.hsql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.QuadKeyMap;

/**
 * HSQLのユニーク制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class HsqlUniqueConstraintReader extends UniqueConstraintReader {

	public HsqlUniqueConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final QuadKeyMap<String, String, String, String, UniqueConstraint> map = CommonUtils.quadKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalogName = getString(rs, TABLE_CATALOG);
				String schemaName = getString(rs, TABLE_SCHEMA);
				String tableName = getString(rs, TABLE_NAME);
				String constraint_name = getString(rs, CONSTRAINT_NAME);
				// String expression=getString(rs, "filter_condition");
				boolean primary = !"unique".equalsIgnoreCase(getString(rs,
						"constraint_type"));
				UniqueConstraint c = map.get(catalogName, schemaName, tableName,
						constraint_name);
				if (c == null) {
					c = new UniqueConstraint(constraint_name, primary);
					c.setCatalogName(catalogName);
					c.setSchemaName(schemaName);
					c.setTableName(tableName);
					map.put(catalogName, schemaName, tableName, constraint_name, c);
				}
				Column column = new Column(getString(rs, COLUMN_NAME));
				Order order = Order.parse(getString(rs, "asc_or_desc"));
				column.setTableName(tableName);
				c.getColumns().add(column, order);
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("uniqueConstraints.sql");
	}

}
