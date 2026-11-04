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
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.HashSet;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateIndexFactory;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
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
	protected List<Index> doGetAll(final Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Index> result = list();
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		final Map<Index, SortedMap<Long, ReferenceColumn>> keys = new IdentityHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = getString(rs, TABLE_CATALOG);
				String schema_name = getString(rs, TABLE_SCHEMA);
				String name = getString(rs, INDEX_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				final String tableName = getString(rs, TABLE_NAME);
				if (name == null || name.isBlank() || tableName == null || tableName.isBlank() || columnName == null
						|| columnName.isBlank())
					throw new IllegalArgumentException("Spanner index metadata requires index, table and column names");
				Index index = map.get(catalog_name, schema_name, name);
				final IndexType type = toIndexType(getString(rs, "index_type"));
				final boolean unique = requiredBoolean(rs, "is_unique", name);
				final boolean nullFiltered = requiredBoolean(rs, "is_null_filtered", name);
				if (index == null) {
					index = new Index(name);
					index.setCatalogName(catalog_name);
					index.setSchemaName(schema_name);
					index.setTableName(getString(rs, TABLE_NAME));
					index.setUnique(unique);
					index.setIndexType(type);
					index.getSpecifics().put("is_null_filtered", nullFiltered);
					result.add(index);
					map.put(catalog_name, schema_name, name, index);
					keys.put(index, new TreeMap<>());
				}
				if (!java.util.Objects.equals(index.getTableName(), tableName))
					throw new IllegalArgumentException("Inconsistent Spanner index owner: " + name);
				if (index.getIndexType() != type || index.isUnique() != unique
						|| index.getSpecifics().get("is_null_filtered", Boolean.class) != nullFiltered)
					throw new IllegalArgumentException("Inconsistent Spanner index attributes: " + name);
				final Long ordinal = getLong(rs, "ordinal_position");
				if (ordinal == null) {
					if (index.getIncludes().stream().anyMatch(c -> columnName.equals(c.getName())))
						throw new IllegalArgumentException("Duplicate Spanner STORING column: " + columnName);
					index.getIncludes().add(new Column(columnName));
				} else {
					if (ordinal <= 0 || ordinal > Integer.MAX_VALUE)
						throw new IllegalArgumentException("Invalid Spanner index ordinal: " + name + "=" + ordinal);
					final String ordering = getString(rs, "column_ordering");
					final var order = SpannerUtils.parseOrder(ordering);
					if (order == null && (ordering != null || index.getIndexType() == IndexType.BTree))
						throw new IllegalArgumentException("Unknown Spanner index ordering: " + name + "=" + ordering);
					if (keys.get(index).putIfAbsent(ordinal, new ReferenceColumn(columnName).setOrder(order)) != null)
						throw new IllegalArgumentException("Duplicate Spanner index ordinal: " + name + "=" + ordinal);
				}
			}
		});
		for (var index : result) {
			long expected = 1;
			final var names = new HashSet<String>();
			index.getIncludes().forEach(c -> names.add(c.getName()));
			for (var column : keys.get(index).entrySet()) {
				if (column.getKey() != expected++)
					throw new IllegalArgumentException("Incomplete Spanner index key sequence: " + index.getName());
				if (!names.add(column.getValue().getName()))
					throw new IllegalArgumentException("Repeated Spanner index column: " + column.getValue().getName());
				index.getColumns().add(column.getValue());
			}
			if (index.getColumns().isEmpty())
				throw new IllegalArgumentException("Missing Spanner index keys: " + index.getName());
		}
		if (!result.isEmpty()) {
			setIndexOptions(connection, context, productVersionInfo, map);
		}
		return result;
	}

	private void setIndexOptions(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo, final TripleKeyMap<String, String, String, Index> map) {
		final boolean[] supported = new boolean[1];
		execute(connection, getSqlNodeCache().getString("indexOptionsAvailability.sql"), context,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(final ExResultSet rs) throws SQLException {
						supported[0] = rs.getLong("option_table_count") > 0;
					}
				});
		if (!supported[0]) {
			return;
		}
		execute(connection, getSqlNodeCache().getString("indexOptions.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Index index = map.get(getString(rs, TABLE_CATALOG), getString(rs, TABLE_SCHEMA),
						getString(rs, INDEX_NAME));
				if (index != null)
					setIndexOption(index, getString(rs, "option_name"), getString(rs, "option_value"));
			}
		});
	}

	private void setIndexOption(final Index index, final String name, final String value) {
		if (name == null || value == null)
			return;
		final String key = name.toUpperCase(java.util.Locale.ROOT);
		if ("DISTANCE_TYPE".equals(key)) {
			final VectorDistanceType distance = toVectorDistanceType(value);
			if (distance == null)
				throw new UnsupportedOperationException("Unrepresentable Cloud Spanner vector distance: " + value);
			index.setVectorDistanceType(distance);
		} else if (java.util.Set.of(SpannerCreateIndexFactory.TREE_DEPTH, SpannerCreateIndexFactory.NUM_LEAVES,
				SpannerCreateIndexFactory.NUM_BRANCHES).contains(key)) {
			try {
				Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException(
						"Cloud Spanner index option exceeds the supported integer format: " + name + "=" + value,
						exception);
			}
			index.getSpecifics().put(key, value);
		} else if (java.util.Set
				.of(SpannerCreateIndexFactory.DISABLE_SEARCH, SpannerCreateIndexFactory.SORT_ORDER_SHARDING)
				.contains(key)) {
			if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
				throw new IllegalArgumentException(
						"Cloud Spanner index option requires true or false: " + name + "=" + value);
			index.getSpecifics().put(key, value.toLowerCase(java.util.Locale.ROOT));
		} else if (java.util.Set.of(SpannerCreateIndexFactory.LOCALITY_GROUP, SpannerCreateIndexFactory.COLUMNAR_POLICY)
				.contains(key)) {
			index.getSpecifics().put(key, value);
		}
	}

	private boolean requiredBoolean(final ExResultSet rs, final String field, final String index) throws SQLException {
		final String value = getString(rs, field);
		if ("true".equalsIgnoreCase(value))
			return true;
		if ("false".equalsIgnoreCase(value))
			return false;
		throw new IllegalArgumentException("Unknown Spanner index " + field + " for " + index + ": " + value);
	}

	static IndexType toIndexType(final String productIndexType) {
		if ("SEARCH".equalsIgnoreCase(productIndexType)) {
			return IndexType.FullText;
		}
		if ("VECTOR".equalsIgnoreCase(productIndexType)) {
			return IndexType.Vector;
		}
		if ("INDEX".equalsIgnoreCase(productIndexType))
			return IndexType.BTree;
		throw new IllegalArgumentException("Unknown Spanner index type: " + productIndexType);
	}

	static VectorDistanceType toVectorDistanceType(final String distanceType) {
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
