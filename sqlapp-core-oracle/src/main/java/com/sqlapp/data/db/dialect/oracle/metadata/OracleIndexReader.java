/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
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
	protected List<Index> doGetAll(final Connection connection,
			ParametersContext context,
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
				boolean desc = "Y".equalsIgnoreCase(getString(rs, "DESCEND"));
				Index index = map.get(schema_name, name);
				if (index == null) {
					index = new Index(name);
					index.setSchemaName(getString(rs, "OWNER"));
					index.setTableName(getString(rs, TABLE_NAME));
					dialact.getIndexType(indexType);
					index.setIndexType(dialact.getIndexType(indexType));
					if ("UNIQUE".equals(uniqueness)) {
						index.setUnique(true);
					} else {
						index.setUnique(false);
					}
					for(String key:OracleUtils.getTableStatisticsKeys()){
						setSpecifics(rs, key, index);
					}
					String partitioned = getString(rs, PARTTITIONED_KEY);
					if ("YES".equalsIgnoreCase(partitioned)) {
						index.getSpecifics().put(PARTTITIONED_KEY, Boolean.TRUE.toString());
					}
					index.setCompression("ENABLED".equalsIgnoreCase(getString(
							rs, "COMPRESSION")));
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
				if (!isEmpty(columnExpression)) {
					index.getColumns().add(columnExpression, order);
				} else {
					index.getColumns().add(new Column(columnName), order);
				}
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes.sql");
	}

	private static final String PARTTITIONED_KEY = "PARTITIONED";

	@Override
	protected void setMetadataDetail(final Connection connection,
			final Index index) throws SQLException {
		String value=index.getSpecifics().get(PARTTITIONED_KEY);
		if (!eq(Boolean.TRUE,
				value)) {
			index.getSpecifics().remove(this.getDialect(),
					PARTTITIONED_KEY);
			return;
		}
		SqlNode node = getSqlNodeCache().getString("indexPartitions.sql");
		ParametersContext context = newParametersContext(connection, null,
				index.getSchemaName());
		context.put("indexName", nativeCaseString(connection, index.getName()));
		index.setPartitioning(null);
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String partitioningType = getString(rs, "PARTITIONING_TYPE");
				String subPartitioningType = getString(rs,
						"SUBPARTITIONING_TYPE");
				// int defSubpartitionCount=rs.getInt("DEF_SUBPARTITION_COUNT");
				Partitioning partitionInfo = null;
				Partition partition = null;
				if (index.getPartitioning() != null) {
					partitionInfo = index.getPartitioning();
					partition = addIndexPartition(rs, partitionInfo);
				} else {
					partitionInfo = new Partitioning();
					partitionInfo.setPartitioningType(partitioningType);
					index.setPartitioning(partitionInfo);
					partition = addIndexPartition(rs, partitionInfo);
					setPartitionColumnInfo(connection, index.getSchemaName(),
							index.getName(), partitionInfo);
					if (!"NONE".equalsIgnoreCase(subPartitioningType)) {
						partitionInfo.setSubPartitioningType(subPartitioningType);
						setSubPartitionColumnInfo(connection,
								index.getSchemaName(), index.getName(),
								partitionInfo);
					}
				}
				if (!"NONE".equalsIgnoreCase(subPartitioningType)) {
					setIndexSubPartitionInfo(connection, index.getSchemaName(),
							index.getName(), partition,
							PartitioningType.parse(subPartitioningType));
				}
			}
		});
	}

	private Partition addIndexPartition(ExResultSet rs, Partitioning partitionInfo)
			throws SQLException {
		String partitionName = getString(rs, "PARTITION_NAME");
		String highValue = getString(rs, "HIGH_VALUE");
		String compression = getString(rs, "COMPRESSION");
		Partition partition = new Partition(partitionName);
		partition.setHighValue(highValue);
		if (!"DISABLED".equalsIgnoreCase(compression)) {
			partition.setCompression(true);
		}
		partitionInfo.getPartitions().add(partition);
		return partition;
	}

	/**
	 * パーティションカラム情報を設定します
	 * 
	 * @param connection
	 * @param schemaName
	 *            スキーマ名
	 * @param objectName
	 *             テーブル名 or インデックス名
	 * @param partitionInfo
	 *            パーティション情報
	 */
	protected void setPartitionColumnInfo(Connection connection,
			String schemaName, String objectName,
			final Partitioning partitionInfo) {
		SqlNode node = getSqlNodeCache().getString("partitionKeyColumns.sql");
		ParametersContext context = newParametersContext(connection, null,
				schemaName);
		OracleMetadataUtils.setPartitionColumnInfo(connection, node, context,
				"INDEX", nativeCaseString(connection, objectName),
				partitionInfo);
	}

	/**
	 * サブパーティションカラム情報を設定します
	 * 
	 * @param connection
	 * @param schemaName
	 *            スキーマ名
	 * @param objectName
	 *             テーブル名 or インデックス名
	 * @param partitionInfo
	 *            パーティション情報
	 */
	protected void setSubPartitionColumnInfo(Connection connection,
			String schemaName, String objectName,
			final Partitioning partitionInfo) {
		SqlNode node = getSqlNodeCache().getString("subpartitionKeyColumns.sql");
		ParametersContext context = newParametersContext(connection, null,
				schemaName);
		OracleMetadataUtils.setSubPartitionColumnInfo(connection, node,
				context, "INDEX", nativeCaseString(connection, objectName),
				partitionInfo);
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
	protected void setIndexSubPartitionInfo(Connection connection,
			String schemaName, String indexName, final Partition partition,
			PartitioningType subPartitioningType) {
		SqlNode node = getSqlNodeCache().getString("indexSubPartitions.sql");
		ParametersContext context = newParametersContext(connection, null,
				schemaName);
		context.put("indexName", nativeCaseString(connection, indexName));
		context.put("partitionName",
				nativeCaseString(connection, partition.getName()));
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
