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
import java.util.SortedSet;
import java.util.TreeSet;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateIndexFactory;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Partitioning;
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
	private record Header(boolean unique, String parent, String filter, List<String> partition, List<String> order,
			Boolean managed) {
	}

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
		final Map<Index, SortedSet<String>> filters = new IdentityHashMap<>();
		final Map<Index, Header> headers = new IdentityHashMap<>();
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
				final var header = new Header(unique, getString(rs, "parent_table_name"), getString(rs, "filter"),
						columnNames(rs, "search_partition_by"), columnNames(rs, "search_order_by"),
						getString(rs, "spanner_is_managed") == null ? null
								: requiredBoolean(rs, "spanner_is_managed", name));
				if ((!header.partition().isEmpty() || !header.order().isEmpty()) && type != IndexType.FullText)
					throw new IllegalArgumentException("SEARCH layout metadata on a non-SEARCH index: " + name);
				if (header.order().size() > 1)
					throw new UnsupportedOperationException("Spanner SEARCH ORDER BY supports one column: " + name);
				if (index == null) {
					index = new Index(name);
					index.setCatalogName(catalog_name);
					index.setSchemaName(schema_name);
					index.setTableName(getString(rs, TABLE_NAME));
					// SEARCH's internal uniqueness is not a user-declared UNIQUE index.
					index.setUnique(unique && type != IndexType.FullText);
					index.setIndexType(type);
					index.getSpecifics().put("is_null_filtered", nullFiltered);
					if (header.managed() != null)
						index.getSpecifics().put(SpannerCreateIndexFactory.SPANNER_IS_MANAGED, header.managed());
					result.add(index);
					map.put(catalog_name, schema_name, name, index);
					keys.put(index, new TreeMap<>());
					filters.put(index, new TreeSet<>());
					headers.put(index, header);
					if (header.parent() != null && !header.parent().isBlank())
						index.getSpecifics().put(SpannerCreateIndexFactory.PARENT_TABLE_NAME, header.parent());
					if (header.filter() != null && !header.filter().isBlank())
						index.setWhere(header.filter());
				}
				if (!java.util.Objects.equals(index.getTableName(), tableName))
					throw new IllegalArgumentException("Inconsistent Spanner index owner: " + name);
				if (index.getIndexType() != type || !headers.get(index).equals(header)
						|| index.getSpecifics().get("is_null_filtered", Boolean.class) != nullFiltered)
					throw new IllegalArgumentException("Inconsistent Spanner index attributes: " + name);
				final Long ordinal = getLong(rs, "ordinal_position");
				final boolean hasFilter = header.filter() != null && !header.filter().isBlank();
				final boolean baseNullable = !hasFilter && nullable(rs, "base_is_nullable", name);
				final boolean indexNullable = hasFilter || nullable(rs, "index_is_nullable", name);
				// NULL_FILTERED already excludes all key NULLs; only retain extra per-column
				// restrictions.
				if (baseNullable && !indexNullable && !(nullFiltered && ordinal != null))
					filters.get(index).add(columnName);
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
			final Header header = headers.get(index);
			long expected = 1;
			final var names = new HashSet<String>();
			final var layoutNames = new HashSet<String>();
			index.getIncludes().forEach(c -> names.add(c.getName()));
			for (var column : keys.get(index).entrySet()) {
				if (column.getKey() != expected++)
					throw new IllegalArgumentException("Incomplete Spanner index key sequence: " + index.getName());
				final String columnName = column.getValue().getName();
				if (header.partition().contains(columnName) || header.order().contains(columnName)) {
					layoutNames.add(columnName);
					if (header.order().contains(columnName)) {
						if (column.getValue().getOrder() == null)
							throw new IllegalArgumentException("Missing SEARCH ORDER BY direction: " + columnName);
						index.getSpecifics().put(SpannerCreateIndexFactory.SEARCH_ORDER_BY_COLUMN, columnName);
						index.getSpecifics().put(SpannerCreateIndexFactory.SEARCH_ORDER_BY_DIRECTION,
								column.getValue().getOrder().getSqlValue());
					}
					continue;
				}
				if (!names.add(column.getValue().getName()))
					throw new IllegalArgumentException("Repeated Spanner index column: " + column.getValue().getName());
				index.getColumns().add(column.getValue());
			}
			if (index.getColumns().isEmpty())
				throw new IllegalArgumentException("Missing Spanner index keys: " + index.getName());
			if (!layoutNames.containsAll(header.partition()) || !layoutNames.containsAll(header.order()))
				throw new IllegalArgumentException("Missing SEARCH layout columns: " + index.getName());
			if (!header.partition().isEmpty()) {
				final var partitioning = new Partitioning();
				header.partition()
						.forEach(name -> partitioning.getPartitioningColumns().add(new ReferenceColumn(name)));
				index.setPartitioning(partitioning);
			}
			if (!filters.get(index).isEmpty()) {
				final var predicate = new SpannerSqlBuilder(getDialect());
				int count = 0;
				for (String column : filters.get(index)) {
					if (count++ > 0)
						predicate._add(" AND ");
					predicate.name(new Column(column))._add(" IS NOT NULL");
				}
				index.setWhere(predicate.toString());
			}
		}
		if (!result.isEmpty()) {
			setIndexOptions(connection, context, productVersionInfo, map);
		}
		return result;
	}

	private List<String> columnNames(final ExResultSet rs, final String field) throws SQLException {
		Object value = rs.getObject(field);
		if (value == null)
			return List.of();
		final java.sql.Array array = value instanceof java.sql.Array a ? a : null;
		try {
			if (array != null)
				value = array.getArray();
			if (!(value instanceof Object[] values))
				throw new IllegalArgumentException("Spanner " + field + " must be a JDBC string array");
			final var result = new java.util.ArrayList<String>();
			for (Object element : values) {
				if (!(element instanceof String name) || name.isBlank() || result.contains(name))
					throw new IllegalArgumentException("Invalid or duplicate Spanner " + field + " column: " + element);
				result.add(name);
			}
			return List.copyOf(result);
		} finally {
			if (array != null)
				array.free();
		}
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

	private boolean nullable(final ExResultSet rs, final String field, final String index) throws SQLException {
		final String value = getString(rs, field);
		if ("YES".equalsIgnoreCase(value))
			return true;
		if ("NO".equalsIgnoreCase(value))
			return false;
		throw new IllegalArgumentException("Missing or unknown Spanner index nullability " + field + " for " + index
				+ ": " + value + "; base column metadata is required to reconstruct WHERE");
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
