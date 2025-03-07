/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2000IndexReader.FILE_GROUP_NAME;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerIndexOptions;
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
 * SqlServer2000のユニーク制約読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2000UniqueConstraintReader extends UniqueConstraintReader {

	protected SqlServer2000UniqueConstraintReader(Dialect dialect) {
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
				String name = getString(rs, INDEX_NAME);
				UniqueConstraint obj = map.get(catalog_name, schema_name, name);
				if (obj == null) {
					obj = createUniqueConstraint(rs);
					map.put(catalog_name, schema_name, name, obj);
				}
				String columnName = getString(rs, COLUMN_NAME);
				if (rs.getInt("is_descending_key") == 1) {
					obj.getColumns().add(new Column(columnName), Order.Desc);
				} else {
					obj.getColumns().add(new Column(columnName), Order.Asc);
				}
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("uniqueConstraints2000.sql");
	}

	protected UniqueConstraint createUniqueConstraint(ExResultSet rs)
			throws SQLException {
		UniqueConstraint obj = new UniqueConstraint(getString(rs, INDEX_NAME));
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setTableName(getString(rs, TABLE_NAME));
		obj.setPrimaryKey(rs.getInt("is_primary_key") == 1);
		obj.setIndexType(SqlServerUtils.getIndexType(rs.getInt("type")));
		obj.setEnable(rs.getInt("is_disabled") != 1);
		SqlServerIndexOptions.setAllUniqueConstraint(rs, obj);
		setSpecifics(rs, FILE_GROUP_NAME, obj);
		return obj;
	}

}
