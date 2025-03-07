/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
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
 * Firebirdのインデックス読み込みクラス
 * 
 * @author satoh
 * 
 */
public class FirebirdIndexReader extends IndexReader {

	public FirebirdIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Index> result = list();
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = null;
				String schema_name = null;
				String name = trim(getString(rs, INDEX_NAME));
				String columnName = trim(getString(rs, COLUMN_NAME));
				boolean uniqueness = !rs.getBoolean("NON_UNIQUE");
				Index index = map.get(catalog_name, schema_name, name);
				if (index == null) {
					index = new Index(name);
					index.setCatalogName(catalog_name);
					index.setSchemaName(schema_name);
					index.setTableName(trim(getString(rs, TABLE_NAME)));
					index.setUnique(uniqueness);
					index.setEnable(!rs.getBoolean("INDEX_INACTIVE"));
					index.setRemarks(getString(rs, "DESCRIPTION"));
					//
					map.put(catalog_name, schema_name, name, index);
					result.add(index);
				}
				boolean isDesc = rs.getBoolean("IS_DESC");
				if (isDesc) {
					index.getColumns().add(new Column(columnName), Order.Desc);
				} else {
					index.getColumns().add(new Column(columnName), Order.Asc);
				}
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes.sql");
	}
}
