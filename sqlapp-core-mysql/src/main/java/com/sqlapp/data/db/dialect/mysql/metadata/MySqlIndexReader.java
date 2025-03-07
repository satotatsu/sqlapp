/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * MySqlのインデックス読み込みクラス
 * 
 * @author satoh
 * 
 */
public class MySqlIndexReader extends IndexReader {

	public MySqlIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Index> result = list();
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = getString(rs, TABLE_CATALOG);
				String schema_name = getString(rs, TABLE_SCHEMA);
				String name = getString(rs, INDEX_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				String indexType = getString(rs, "INDEX_TYPE");
				Index index = map.get(catalog_name, schema_name, name);
				Long subPart=getLong(rs, "SUB_PART");
				if (index == null) {
					index = new Index(name);
					index.setCatalogName(catalog_name);
					index.setSchemaName(schema_name);
					index.setTableName(getString(rs, TABLE_NAME));
					index.setRemarks(getString(rs, "COMMENT"));
					index.setIndexType(IndexType.parse(indexType));
					index.setUnique(rs.getInt("NON_UNIQUE") == 0);
					result.add(index);
					map.put(catalog_name, schema_name, name, index);
				}
				String visible=getString(rs, "IS_VISIBLE");
				if (visible!=null) {
					Boolean bool=Converters.getDefault().convertObject(visible, Boolean.class);
					if (!bool.booleanValue()) {
						
					}
				}
				// index.setCompression("DISABLED".equalsIgnoreCase(getString(rs,
				// "COMPRESSION")));
				// setDbSpecificInfo(rs, "LOGGING", index);
				index.getColumns().add(new Column(columnName),
						Order.parse(getString(rs, "ASC_OR_DESC"))).setLength(subPart);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes.sql");
	}

}
