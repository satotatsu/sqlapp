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

import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerIndexOptions;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * SqlServer2000のインデックス作成クラス
 * 
 * @author satoh
 * 
 */
public class SqlServer2000IndexReader extends IndexReader {

	public SqlServer2000IndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = getString(rs, CATALOG_NAME);
				String schema_name = getString(rs, SCHEMA_NAME);
				String name = getString(rs, INDEX_NAME);
				Index index = map.get(catalog_name, schema_name, name);
				if (index == null) {
					index = createIndex(rs);
					map.put(catalog_name, schema_name, name, index);
				}
				String columnName = getString(rs, COLUMN_NAME);
				if (rs.getInt("is_descending") == 1) {
					index.getColumns().add(new Column(columnName), Order.Desc);
				} else {
					index.getColumns().add(new Column(columnName), Order.Asc);
				}
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes2000.sql");
	}

	public static final String FILE_GROUP_NAME = "FILE_GROUP_NAME";

	protected Index createIndex(ExResultSet rs) throws SQLException {
		Index index = new Index(getString(rs, INDEX_NAME));
		index.setCatalogName(getString(rs, CATALOG_NAME));
		index.setSchemaName(getString(rs, SCHEMA_NAME));
		index.setTableName(getString(rs, TABLE_NAME));
		index.setIndexType(SqlServerUtils.getIndexType(rs.getInt("type")));
		boolean uniqueness = rs.getInt("is_unique") == 1;
		index.setUnique(uniqueness);
		SqlServerIndexOptions.setAllIndex(rs, index);
		setSpecifics(rs, FILE_GROUP_NAME, index);
		return index;
	}
}
