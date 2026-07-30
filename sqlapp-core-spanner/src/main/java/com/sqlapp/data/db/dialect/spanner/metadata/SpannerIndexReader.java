/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.spanner.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateIndexFactory;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * Spannerのインデックス読み込みクラス
 * 
 * @author satoh
 * 
 */
public class SpannerIndexReader extends IndexReader {

	public SpannerIndexReader(Dialect dialect) {
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
				Index index = map.get(catalog_name, schema_name, name);
				if (index == null) {
					index = new Index(name);
					index.setCatalogName(catalog_name);
					index.setSchemaName(schema_name);
					index.setTableName(getString(rs, TABLE_NAME));
					index.setUnique(rs.getBoolean("IS_UNIQUE"));
					index.setIndexType(toIndexType(
							getString(rs, "INDEX_TYPE")));
					index.setVectorDistanceType(toVectorDistanceType(
							getString(rs, "DISTANCE_TYPE")));
					index.getSpecifics().put(
							SpannerCreateIndexFactory.IS_NULL_FILTERED,
							rs.getBoolean("IS_NULL_FILTERED"));
					setSpecifics(rs, SpannerCreateIndexFactory.TREE_DEPTH,
							index);
					setSpecifics(rs, SpannerCreateIndexFactory.NUM_LEAVES,
							index);
					setSpecifics(rs, SpannerCreateIndexFactory.NUM_BRANCHES,
							index);
					setSpecifics(rs,
							SpannerCreateIndexFactory.DISABLE_SEARCH,
							index);
					setSpecifics(rs,
							SpannerCreateIndexFactory.SORT_ORDER_SHARDING,
							index);
					setSpecifics(rs,
							SpannerCreateIndexFactory.LOCALITY_GROUP,
							index);
					setSpecifics(rs,
							SpannerCreateIndexFactory.COLUMNAR_POLICY,
							index);
					result.add(index);
					map.put(catalog_name, schema_name, name, index);
				}
				if (getLong(rs, "ORDINAL_POSITION") == null) {
					index.getIncludes().add(new Column(columnName));
				} else {
					index.getColumns().add(new Column(columnName),
							Order.parse(getString(rs, "COLUMN_ORDERING")));
				}
			}
		});
		return result;
	}

	static IndexType toIndexType(final String productIndexType) {
		if ("SEARCH".equalsIgnoreCase(productIndexType)) {
			return IndexType.FullText;
		}
		if ("VECTOR".equalsIgnoreCase(productIndexType)) {
			return IndexType.Vector;
		}
		return IndexType.BTree;
	}

	static VectorDistanceType toVectorDistanceType(
			final String distanceType) {
		if ("COSINE".equalsIgnoreCase(distanceType)) {
			return VectorDistanceType.Cosine;
		}
		if ("DOT_PRODUCT".equalsIgnoreCase(distanceType)) {
			return VectorDistanceType.DotProduct;
		}
		if ("EUCLIDEAN".equalsIgnoreCase(distanceType)) {
			return VectorDistanceType.Euclidean;
		}
		return null;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes.sql");
	}

}
