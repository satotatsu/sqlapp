/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.metadata;

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
 * Hsqlのインデックス読み込みクラス
 * 
 * @author satoh
 * 
 */
public class HsqlIndexReader extends IndexReader {

	public HsqlIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = getString(rs, "table_cat");
				String schema_name = getString(rs, "table_schem");
				String name = getString(rs, INDEX_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				boolean uniqueness = !rs.getBoolean("non_unique");
				Index index = map.get(catalog_name, schema_name, name);
				if (index == null) {
					index = new Index(name);
					index.setCatalogName(catalog_name);
					index.setSchemaName(schema_name);
					index.setTableName(getString(rs, TABLE_NAME));
					index.setUnique(uniqueness);
					int indexType = rs.getInt("type");
					index.setWhere(getString(rs, "filter_condition"));
					//
					map.put(catalog_name, schema_name, name, index);
				}
				index.getColumns().add(new Column(columnName),
						Order.parse(getString(rs, "ASC_OR_DESC")));
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes.sql");
	}
}
