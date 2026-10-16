/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.doubleKeyMap;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.util.OracleUtils;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/**
 * Oracleのインデックス作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleIndexReader extends IndexReader {

	public OracleIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final Dialect dialact = this.getDialect();
		final List<Index> result = list();
		final DoubleKeyMap<String, String, Index> map = doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, "OWNER");
				String name = getString(rs, "INDEX_NAME");
				String columnName = getString(rs, COLUMN_NAME);
				String columnExpression = getString(rs, "COLUMN_EXPRESSION");
				String indexType = getString(rs, "INDEX_TYPE");
				String uniqueness = getString(rs, "UNIQUENESS");
				boolean desc = "DESC".equalsIgnoreCase(getString(rs, "DESCEND"));
				Index index = map.get(schema_name, name);
				if (index == null) {
					index = new Index(name);
					index.setSchemaName(getString(rs, "OWNER"));
					index.setTableName(getString(rs, TABLE_NAME));
					index.setIndexType(dialact.getIndexType(indexType));
					if ("BITMAP".equalsIgnoreCase(indexType))
						index.getSpecifics().put("oracle.index.bitmap", true);
					setIndexType(rs, index, indexType);
					if ("UNIQUE".equals(uniqueness)) {
						index.setUnique(true);
					} else {
						index.setUnique(false);
					}
					for (String key : OracleUtils.getTableStatisticsKeys()) {
						setSpecifics(rs, key, index);
					}
					String partitioned = getString(rs, PARTTITIONED_KEY);
					if ("YES".equalsIgnoreCase(partitioned)) {
						index.getSpecifics().put(PARTTITIONED_KEY, Boolean.TRUE.toString());
					}
					String compression = getString(rs, "COMPRESSION");
					index.setCompression("ENABLED".equals(compression) || "ADVANCED LOW".equals(compression) || "ADVANCED HIGH".equals(compression));
					if (index.isCompression()) {
						index.setCompressionType(compression);
						if ("ENABLED".equals(compression)) {
							Integer prefix = getInteger(rs, "PREFIX_LENGTH");
							if (prefix != null) index.getSpecifics().put("oracle.index.prefixLength", prefix);
						}
					}
					index.setTableSpaceName(getString(rs, "TABLESPACE_NAME"));
					//
					setStatistics(rs, "BLEVEL", index);
					setStatistics(rs, "LEAF_BLOCKS", index);
					//
					map.put(schema_name, name, index);
					result.add(index);
				}
				Order order = null;
				if (desc) {
					order = Order.Desc;
				} else {
					order = Order.Asc;
				}
				if (!isEmpty(columnExpression) && columnExpression.matches("\"[^\"]+\"")) {
					index.getColumns().add(new Column(columnExpression.substring(1, columnExpression.length() - 1)),
							order);
				} else if (!isEmpty(columnExpression)) {
					index.getColumns().add(columnExpression, order);
				} else {
					index.getColumns().add(new Column(columnName), order);
				}
			}
		});
		return result;
	}

	protected void setIndexType(final ExResultSet rs, final Index index, final String productIndexType)
			throws SQLException {
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes.sql");
	}

	private static final String PARTTITIONED_KEY = "PARTITIONED";

	@Override
	protected void setMetadataDetail(final Connection connection, final ParametersContext originalContext,
			final List<Index> indexes) throws SQLException {
		final DoubleKeyMap<String, String, Index> objects = doubleKeyMap();
		for (Index index : indexes) {
			if (eq(Boolean.TRUE.toString(), index.getSpecifics().get(PARTTITIONED_KEY))) {
				objects.put(index.getSchemaName(), index.getName(), index);
			}
			index.getSpecifics().remove(this.getDialect(), PARTTITIONED_KEY);
		}
		if (objects.toList().isEmpty())
			return;
		ParametersContext context = new ParametersContext();
		context.put("schemaName", objects.keySet());
		context.put("indexName", objects.secondKeySet());
		execute(connection, getSqlNodeCache().getString("indexPartitions.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Index index = objects.get(rs.getString("INDEX_OWNER"), rs.getString("INDEX_NAME"));
				if (index == null)
					return;
				if (index.getPartitioning() == null) {
					index.getSpecifics().put("oracle.index.locality", rs.getString("LOCALITY"));
					Partitioning info = new Partitioning();
					info.setPartitioningType(rs.getString("PARTITIONING_TYPE"));
					String subType = rs.getString("SUBPARTITIONING_TYPE");
					if (!"NONE".equalsIgnoreCase(subType))
						info.setSubPartitioningType(subType);
					index.setPartitioning(info);
				}
				addIndexPartition(rs, index.getPartitioning());
			}
		});
		DoubleKeyMap<String, String, Partitioning> partitionings = doubleKeyMap();
		for (Index index : objects.toList()) {
			if (index.getPartitioning() != null)
				partitionings.put(index.getSchemaName(), index.getName(), index.getPartitioning());
		}
		OracleMetadataUtils.setPartitionKeys(connection, "INDEX", partitionings, false);
		OracleMetadataUtils.setPartitionKeys(connection, "INDEX", partitionings, true);
		execute(connection, getSqlNodeCache().getString("indexSubPartitions.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Index index = objects.get(rs.getString("INDEX_OWNER"), rs.getString("INDEX_NAME"));
				if (index == null || index.getPartitioning() == null)
					return;
				Partition parent = index.getPartitioning().getPartitions().get(rs.getString("PARTITION_NAME"));
				if (parent == null)
					return;
				SubPartition sub = new SubPartition(rs.getString("SUBPARTITION_NAME"));
				sub.setHighValue(rs.getString("HIGH_VALUE"));
				sub.setCompression("ENABLED".equalsIgnoreCase(rs.getString("COMPRESSION")));
				sub.setTableSpaceName(rs.getString("TABLESPACE_NAME"));
				parent.getSubPartitions().add(sub);
			}
		});
	}

	private Partition addIndexPartition(ExResultSet rs, Partitioning partitionInfo) throws SQLException {
		String partitionName = getString(rs, "PARTITION_NAME");
		String highValue = getString(rs, "HIGH_VALUE");
		String compression = getString(rs, "COMPRESSION");
		Partition partition = new Partition(partitionName);
		partition.setHighValue(highValue);
		if (!"DISABLED".equalsIgnoreCase(compression)) {
			partition.setCompression(true);
		}
		partition.setTableSpaceName(rs.getString("TABLESPACE_NAME"));
		partitionInfo.getPartitions().add(partition);
		return partition;
	}

	/**
	 * パーティションカラム情報を設定します
	 * 
	 * @param connection
	 * @param schemaName    スキーマ名
	 * @param objectName    テーブル名 or インデックス名
	 * @param partitionInfo パーティション情報
	 */
	protected void setPartitionColumnInfo(Connection connection, String schemaName, String objectName,
			final Partitioning partitionInfo) {
		SqlNode node = getSqlNodeCache().getString("partitionKeyColumns.sql");
		ParametersContext context = newParametersContext(connection, null, schemaName);
		OracleMetadataUtils.setPartitionColumnInfo(connection, node, context, "INDEX",
				nativeCaseString(connection, objectName), partitionInfo);
	}

	/**
	 * サブパーティションカラム情報を設定します
	 * 
	 * @param connection
	 * @param schemaName    スキーマ名
	 * @param objectName    テーブル名 or インデックス名
	 * @param partitionInfo パーティション情報
	 */
	protected void setSubPartitionColumnInfo(Connection connection, String schemaName, String objectName,
			final Partitioning partitionInfo) {
		SqlNode node = getSqlNodeCache().getString("subpartitionKeyColumns.sql");
		ParametersContext context = newParametersContext(connection, null, schemaName);
		OracleMetadataUtils.setSubPartitionColumnInfo(connection, node, context, "INDEX",
				nativeCaseString(connection, objectName), partitionInfo);
	}

	/**
	 * インデックスサブパーティション情報を設定します
	 * 
	 * @param connection
	 * @param schemaName
	 * @param indexName
	 * @param partition
	 * @param subPartitioningType
	 */
	protected void setIndexSubPartitionInfo(Connection connection, String schemaName, String indexName,
			final Partition partition, PartitioningType subPartitioningType) {
		SqlNode node = getSqlNodeCache().getString("indexSubPartitions.sql");
		ParametersContext context = newParametersContext(connection, null, schemaName);
		context.put("indexName", nativeCaseString(connection, indexName));
		context.put("partitionName", nativeCaseString(connection, partition.getName()));
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String partitionName = getString(rs, "SUBPARTITION_NAME");
				String highValue = getString(rs, "HIGH_VALUE");
				String compression = getString(rs, "COMPRESSION");
				SubPartition subPartition = new SubPartition(partitionName);
				subPartition.setHighValue(highValue);
				if (!"DISABLED".equalsIgnoreCase(compression)) {
					subPartition.setCompression(true);
				}
				partition.getSubPartitions().add(subPartition);
			}
		});
	}
}
