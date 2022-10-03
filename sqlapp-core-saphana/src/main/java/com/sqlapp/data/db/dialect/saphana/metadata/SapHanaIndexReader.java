/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.saphana.metadata;

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
 * SAP HAANA Index Reader
 * 
 * @author satoh
 * 
 */
public class SapHanaIndexReader extends IndexReader {

	private SapHanaFulltextIndexReader sapHanaFulltextIndexReader;

	public SapHanaIndexReader(Dialect dialect) {
		super(dialect);
		sapHanaFulltextIndexReader = new SapHanaFulltextIndexReader(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		sapHanaFulltextIndexReader.setReaderOptions(this.getReaderOptions());
		sapHanaFulltextIndexReader.setReadDbObjectPredicate(this.getReadDbObjectPredicate());
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = getString(rs, INDEX_NAME);
				String schemaName = getString(rs, SCHEMA_NAME);
				String tableName = getString(rs, TABLE_NAME);
				Index index = map.get(schemaName, tableName, name);
				if (index == null) {
					index = createIndex(connection, rs);
					map.put(index.getSchemaName(), index.getTableName(),
							index.getName(), index);
				}
				String asc = getString(rs, "ASCENDING_ORDER");
				Order order = null;
				if ("TRUE".equalsIgnoreCase(asc)) {
					order = Order.Asc;
				} else {
					order = Order.Desc;
				}
				index.getColumns().add(new Column(getString(rs, COLUMN_NAME)),
						order);
			}
		});
		List<Index> list = map.toList();
		List<Index> ftList = sapHanaFulltextIndexReader.getAll(connection,
				context);
		list.addAll(ftList);
		return list;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes.sql");
	}

	@Override
	public void setIndexName(String indexName) {
		super.setIndexName(indexName);
		sapHanaFulltextIndexReader.setIndexName(indexName);
	}

	@Override
	public void setSchemaName(String schemaName) {
		super.setSchemaName(schemaName);
		sapHanaFulltextIndexReader.setSchemaName(schemaName);
	}

	protected Index createIndex(final Connection connection, ExResultSet rs)
			throws SQLException {
		Index index = SapHanaUtils.createIndex(this.getDialect(), connection,
				rs);
		return index;
	}
}
