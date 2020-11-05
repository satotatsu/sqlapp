/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

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
 * DB2のチェック制約作成クラス
 * 
 * @author satoh
 * 
 */
public class Db2CheckConstraintReader extends CheckConstraintReader {

	public Db2CheckConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, List<Column>> colMap = tripleKeyMap();
		final TripleKeyMap<String, String, String, CheckConstraint> tMap = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = null;
				String schema_name = null;
				String name = trim(getString(rs, CONSTRAINT_NAME));
				String source = trim(getString(rs, "TEXT"));
				String column_name = trim(getString(rs, COLUMN_NAME));
				String tableName = trim(getString(rs, TABLE_NAME));
				CheckConstraint c = tMap.get(catalog_name, schema_name, name);
				List<Column> cols = colMap.get(catalog_name, schema_name, name);
				// short triggetType=rs.getShort("RDB$TRIGGER_TYPE");
				if (c == null) {
					c = new CheckConstraint(name, source);
					c.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
					c.setTableName(tableName);
					cols = list();
					tMap.put(schema_name, schema_name, name, c);
					colMap.put(schema_name, schema_name, name, cols);
				}
				if (column_name != null) {
					Column column = new Column(column_name);
					column.setTableName(tableName);
					cols.add(column);
				}
			}
		});
		for (CheckConstraint c : tMap.toList()) {
			List<Column> cols = colMap.get(c.getCatalogName(),
					c.getSchemaName(), c.getName());
			if (cols.size() == 1) {
				c.addColumns(cols);
			}
		}
		return tMap.toList();
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("checkConstraints.sql");
	}
}
