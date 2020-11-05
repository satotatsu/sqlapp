/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.QuadKeyMap;

/**
 * MySqlのユニーク制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class MySqlUniqueConstraintReader extends UniqueConstraintReader {

	public MySqlUniqueConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<UniqueConstraint> result = list();
		final QuadKeyMap<String, String, String, String, UniqueConstraint> map = CommonUtils
				.quadKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = getString(rs, "constraint_catalog");
				String schema_name = getString(rs, "constraint_schema");
				String table_name = getString(rs, TABLE_NAME);
				String constraint_name = getString(rs, CONSTRAINT_NAME);
				UniqueConstraint c = map.get(catalog_name, schema_name,
						table_name, constraint_name);
				if (c == null) {
					boolean primary = !"UNIQUE".equalsIgnoreCase(getString(rs,
							"constraint_type"));
					c = new UniqueConstraint(constraint_name, primary);
					c.setCatalogName(catalog_name);
					c.setSchemaName(schema_name);
					c.setTableName(table_name);
					// CONSTRAINT_TYPE
					result.add(c);
					map.put(catalog_name, schema_name, table_name,
							constraint_name, c);
				}
				Column column = new Column(getString(rs, COLUMN_NAME));
				column.setTableName(table_name);
				c.getColumns().add(column);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("uniqueConstraints.sql");
	}
}
