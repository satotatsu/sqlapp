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


import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

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

/**
 * SqlServer2005のインデックス作成クラス
 * 
 * @author satoh
 * 
 */
public class SqlServer2005IndexReader extends SqlServer2000IndexReader {

	public SqlServer2005IndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final Map<List<String>, Index> map = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = getString(rs, CATALOG_NAME);
				String schema_name = getString(rs, SCHEMA_NAME);
				String name = getString(rs, INDEX_NAME);
				List<String> identity = java.util.Arrays.asList(catalog_name, schema_name, getString(rs, TABLE_NAME), name);
				Index index = map.get(identity);
				if (index == null) {
					index = createIndex(rs);
					map.put(identity, index);
				}
				String columnName = getString(rs, COLUMN_NAME);
				boolean included = getBoolean(rs, "is_included_column");
				String scheme = getString(rs, "partition_scheme");
				if (scheme != null) {
					if (index.getPartitioning() == null) index.setPartitioning(new com.sqlapp.data.schemas.Partitioning());
					index.getPartitioning().setPartitionSchemeName(scheme);
					if (rs.getInt("partition_ordinal") > 0) index.getPartitioning().getPartitioningColumns().add(columnName);
				}
				// Implicit partitioning columns have key ordinal zero and are not index keys.
				if (!included && rs.getInt("key_ordinal") == 0 && !index.getIndexType().isColumnStore()
						&& rs.getInt("partition_ordinal") > 0) return;
				if (included && !index.getIndexType().isColumnStore()) {
					index.getIncludes().add(new Column(columnName));
				} else {
					if (rs.getInt("is_descending_key") == 1) {
						index.getColumns().add(new Column(columnName), Order.Desc);
					} else {
						index.getColumns().add(new Column(columnName), Order.Asc);
					}
				}
			}
		});
		List<Index> result = new ArrayList<>(map.values());
		List<Index> fullTextResult = getMetadataFullTextIndex(connection, context, productVersionInfo);
		result.addAll(fullTextResult);
		return result;
	}

	protected List<Index> getMetadataFullTextIndex(final Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		IndexReader reader = getFullTextIndexReader();
		return reader.getAll(connection, context);
	}

	protected IndexReader getFullTextIndexReader() {
		IndexReader reader = newFullTextIndexReader();
		this.initializeChild(reader);
		return reader;
	}

	protected IndexReader newFullTextIndexReader() {
		SqlServer2005FullTextIndexReader reader = new SqlServer2005FullTextIndexReader(this.getDialect());
		return reader;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes2005.sql");
	}

	@Override
	protected Index createIndex(ExResultSet rs) throws SQLException {
		Index index = super.createIndex(rs);
		return index;
	}
}
