/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * Postgresのチェック制約作成クラス
 * 
 * @author satoh
 * 
 */
public class PostgresCheckConstraintReader extends CheckConstraintReader {

	public PostgresCheckConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, CheckConstraint> map = tripleKeyMap();
		final TripleKeyMap<String, String, String, List<Column>> colMap = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, "constraint_schema");
				String table_name = getString(rs, TABLE_NAME);
				String constraint_name = getString(rs, CONSTRAINT_NAME);
				String expression = getString(rs, "consrc");
				CheckConstraint c = map.get(schema_name, table_name,
						constraint_name);
				List<Column> columnList = colMap.get(schema_name, table_name,
						constraint_name);
				if (c == null) {
					c = new CheckConstraint(constraint_name, expression);
					columnList = list();
					c.setSchemaName(schema_name);
					c.setTableName(table_name);
					c.setDeferrability(Deferrability.getDeferrability(
							rs.getBoolean("is_deferrable"),
							rs.getBoolean("initially_deferred")));
					colMap.put(schema_name, table_name, constraint_name,
							columnList);
					map.put(schema_name, table_name, constraint_name, c);
				}
				Column column = new Column(getString(rs, COLUMN_NAME));
				column.setTableName(table_name);
				columnList.add(column);
			}
		});
		for (CheckConstraint c : map.toList()) {
			List<Column> columnList = colMap.get(c.getSchemaName(),
					c.getTableName(), c.getName());
			if (columnList.size() == 1) {
				c.setColumns(columnList.toArray(new Column[0]));
			}
		}
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("checkConstraints.sql");
	}

}
