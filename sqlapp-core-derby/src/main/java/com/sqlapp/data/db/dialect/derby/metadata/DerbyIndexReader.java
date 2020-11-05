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

import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * Derbyのインデックス読み込みクラス
 * 
 * @author satoh
 * 
 */
public class DerbyIndexReader extends IndexReader {

	public DerbyIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				boolean bool = rs.getBoolean("ISINDEX");
				if (!bool) {
					return;
				}
				Index index = createIndex(connection, rs);
				map.put(index.getCatalogName(), index.getSchemaName(),
						index.getName(), index);
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes.sql");
	}

	protected Index createIndex(final Connection connection, ExResultSet rs)
			throws SQLException {
		String catalogName = null;
		String schemaName = getString(rs, SCHEMA_NAME);
		String name = getString(rs, INDEX_NAME);
		String columnInfo = getString(rs, "index_info");
		String tableName = getString(rs, TABLE_NAME);
		Index index = DerbyUtils.parseIndexDescriptor(connection, getDialect(),
				schemaName, tableName, name, columnInfo);
		index.setCatalogName(catalogName);
		index.setSchemaName(schemaName);
		return index;
	}
}
