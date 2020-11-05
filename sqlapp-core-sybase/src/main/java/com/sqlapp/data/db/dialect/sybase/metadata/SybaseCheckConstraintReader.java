/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static com.sqlapp.data.db.dialect.sybase.metadata.SybaseUtils.replaceNames;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * SqlServerのチェック制約読み込み
 * 
 * @author satoh
 * 
 */
public class SybaseCheckConstraintReader extends CheckConstraintReader {

	protected SybaseCheckConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, CheckConstraint> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = getString(rs, CATALOG_NAME);
				String schema_name = getString(rs, SCHEMA_NAME);
				String constraint_name = getString(rs, CONSTRAINT_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				CheckConstraint c = map.get(catalog_name, schema_name,
						constraint_name);
				if (c == null) {
					c = createCheckConstraint(rs);
					map.put(catalog_name, schema_name, constraint_name, c);
				} else {
					String definition = replaceNames(c.getExpression(),
							columnName);
					c.setExpression(definition);
				}
				Column column = new Column(columnName);
				column.setTableName(c.getTableName());
				c.addColumns(column);
			}
		});
		List<CheckConstraint> list = map.toList();
		return list;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("checkConstraints.sql");
	}

	protected CheckConstraint createCheckConstraint(ExResultSet rs)
			throws SQLException {
		String constraint_name = getString(rs, CONSTRAINT_NAME);
		String columnName = getString(rs, COLUMN_NAME);
		String tableName = getString(rs, TABLE_NAME);
		String schemaName = getString(rs, SCHEMA_NAME);
		String definition = replaceNames(
				unwrap(getString(rs, "definition"), '(', ')'), columnName);
		CheckConstraint c = new CheckConstraint(constraint_name, definition);
		c.setCatalogName(getString(rs, CATALOG_NAME));
		c.setSchemaName(getString(rs, SCHEMA_NAME));
		c.setTableName(tableName);
		boolean isColumnConstraint = rs.getInt("is_column_check_constraint") == 1;
		if (isColumnConstraint) {
			Column column = new Column(columnName);
			column.setTableName(tableName);
			column.setSchemaName(schemaName);
			c.addColumns(column);
		}
		return c;
	}

}
