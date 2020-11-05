/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-derby.
 *
 * sqlapp-core-derby is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-derby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-derby.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.derby.metadata;

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
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * Derbyのチェック制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class DerbyCheckConstraintReader extends CheckConstraintReader {

	public DerbyCheckConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, List<Column>> colMap = tripleKeyMap();
		final TripleKeyMap<String, String, String, CheckConstraint> tMap = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalogName = null;
				String schemaName = getString(rs, "SCHEMANAME");
				String tableName = getString(rs, "TABLENAME");
				String name = getString(rs, "CONSTRAINTNAME");
				String source = getString(rs, "CHECKDEFINITION");
				String column_name = getString(rs, "COLUMNNAME");
				List<Column> cols = colMap.get(catalogName, schemaName, name);
				CheckConstraint c = tMap.get(catalogName, schemaName, name);
				if (c == null) {
					c = new CheckConstraint(name, source);
					c.setSchemaName(schemaName);
					c.setTableName(tableName);
					cols = list();
					tMap.put(catalogName, schemaName, name, c);
					colMap.put(catalogName, schemaName, name, cols);
				}
				Column column = new Column(column_name);
				column.setSchemaName(schemaName);
				column.setTableName(tableName);
				cols.add(column);
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

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("checkConstraints.sql");
	}
}
