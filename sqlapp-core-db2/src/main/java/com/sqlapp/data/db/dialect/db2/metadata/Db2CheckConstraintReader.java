/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;

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
import com.sqlapp.util.CommonUtils;

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
	protected List<CheckConstraint> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final java.util.Map<List<String>, List<Column>> colMap = new java.util.LinkedHashMap<>();
		final java.util.Map<List<String>, CheckConstraint> tMap = new java.util.LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = null;
				String schema_name = trim(getString(rs, SCHEMA_NAME));
				String name = trim(getString(rs, CONSTRAINT_NAME));
				String source = trim(getString(rs, "TEXT"));
				String column_name = trim(getString(rs, COLUMN_NAME));
				String tableName = trim(getString(rs, TABLE_NAME));
				CheckConstraint c = tMap.get(List.of(schema_name, tableName, name));
				List<Column> cols = colMap.get(List.of(schema_name, tableName, name));
				// short triggetType=rs.getShort("RDB$TRIGGER_TYPE");
				if (c == null) {
					c = new CheckConstraint(name, source);
					c.setSchemaName(schema_name);
					c.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
					c.setTableName(tableName);
					cols = list();
					tMap.put(List.of(schema_name, tableName, name), c);
					colMap.put(List.of(schema_name, tableName, name), cols);
				}
				if (column_name != null) {
					Column column = new Column(column_name);
					column.setTableName(tableName);
					cols.add(column);
				}
			}
		});
		for (CheckConstraint c : new java.util.ArrayList<>(tMap.values())) {
			List<Column> cols = colMap.get(List.of(c.getSchemaName(), c.getTableName(), c.getName()));
			if (cols.size() == 1) {
				CommonUtils.first(cols).setCheckConstraint(c);
			}
		}
		return new java.util.ArrayList<>(tMap.values());
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("checkConstraints.sql");
	}
}
