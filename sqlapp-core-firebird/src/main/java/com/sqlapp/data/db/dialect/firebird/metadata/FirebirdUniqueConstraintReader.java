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

import static com.sqlapp.util.CommonUtils.tripleKeyMap;

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
import com.sqlapp.util.TripleKeyMap;

/**
 * Firebirdのユニーク制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class FirebirdUniqueConstraintReader extends UniqueConstraintReader {

	public FirebirdUniqueConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, UniqueConstraint> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = null;
				String schema_name = null;
				String table_name = getString(rs, TABLE_NAME);
				String constraint_name = getString(rs, CONSTRAINT_NAME);
				// String expression=getString(rs, "SEARCH_CONDITION");
				boolean primary = !"unique".equalsIgnoreCase(getString(rs, "CONSTRAINT_TYPE"));
				UniqueConstraint c = map.get(catalog_name, schema_name, constraint_name);
				if (c == null) {
					c = new UniqueConstraint(constraint_name, primary);
					c.setCatalogName(catalog_name);
					c.setSchemaName(schema_name);
					c.setTableName(table_name);
					c.setEnable(!rs.getBoolean("INDEX_INACTIVE"));
					String index_name = getString(rs, INDEX_NAME);
					map.put(catalog_name, schema_name, constraint_name, c);
				}
				Column column = new Column(getString(rs, COLUMN_NAME));
				Order order = null;
				boolean isDesc = rs.getBoolean("IS_DESC");
				if (isDesc) {
					order = Order.Desc;
				} else {
					order = Order.Asc;
				}
				column.setTableName(table_name);
				c.getColumns().add(column, order);
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("uniqueConstraints.sql");
	}

}
