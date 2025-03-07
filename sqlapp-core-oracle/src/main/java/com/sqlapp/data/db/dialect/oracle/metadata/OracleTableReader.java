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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.util.OracleUtils;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Statistics;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

/**
 * Oracleのテーブル情報読み込み
 * 
 * @author satoh
 * 
 */
public class OracleTableReader extends TableReader {

	protected OracleTableReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * テーブル情報を取得します
	 * 
	 * @param connection
	 * @param context
	 */
	protected List<Table> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Table> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Table table = createTable(rs);
				result.add(table);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables.sql");
	}

	protected Table createTable(ExResultSet rs) throws SQLException {
		String compression = getString(rs, "COMPRESSION");
		String comments = getString(rs, "COMMENTS");
		Table table = createTable(getString(rs, TABLE_NAME));
		table.setSchemaName(getString(rs, "OWNER"));
		table.setRemarks(comments);
		if (!"DISABLED".equalsIgnoreCase(compression)) {
			table.setCompression(true);
		}
		String partitioned = getString(rs, PARTTITIONED_KEY);
		if ("YES".equalsIgnoreCase(partitioned)) {
			table.toPartitioning();
		}
		table.setValid("VALID".equalsIgnoreCase(getString(rs, "STATUS")));
		for(String key:OracleUtils.getTableStatisticsKeys()){
			setSpecifics(rs, key, table);
		}
		Statistics.ROWS.setValue(rs, "NUM_ROWS", table);
		Statistics.AVG_ROW_LENGTH.setValue(rs, "AVG_ROW_LEN", table);
		return table;
	}

	private static final String PARTTITIONED_KEY = "PARTITIONED";

	@Override
	protected void setMetadataDetail(Connection connection,
			ParametersContext context, List<Table> tableList) throws SQLException {
		super.setMetadataDetail(connection, context, tableList);
		if (CommonUtils.isEmpty(tableList)){
			return;
		}
		final DoubleKeyMap<String,String,Table> tables=SchemaUtils.toDoubleKeyMap(tableList);
		setPartitioning(connection, tables);
	}
	
	/**
	 * テーブルのパーティション情報を設定します
	 * 
	 * @param connection
	 * @param tableName
	 */
	protected void setPartitioning(Connection connection, final DoubleKeyMap<String,String,Table> tables) {
		SqlNode node = getSqlNodeCache().getString("tablePartitions.sql");
		ParametersContext context = this.defaultParametersContext(connection);
		context.put(SCHEMA_NAME, tables.keySet());
		context.put(TABLE_NAME, tables.secondKeySet());
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schemaName=getString(rs,
						SCHEMA_NAME);
				String tableName=getString(rs,
						TABLE_NAME);
				Table table=tables.get(schemaName, tableName);
				setPartition(connection, rs, table);
			}
		});
	}

	protected void setPartition(Connection connection, ExResultSet rs, final Table table) throws SQLException{
		String partitioningType = getString(rs, "PARTITIONING_TYPE");
		String subPartitioningType = getString(rs,
				"SUBPARTITIONING_TYPE");
		Partitioning partitioning = null;
		Partition partition = null;
		if (table.getPartitioning() != null) {
			partitioning = table.getPartitioning();
			partition = addPartition(rs, partitioning);
		} else {
			partitioning = new Partitioning();
			partitioning.setPartitioningType(partitioningType);
			table.setPartitioning(partitioning);
			partition = addPartition(rs, partitioning);
			setPartitionColumnInfo(connection, table.getSchemaName(),
					table.getName(), partitioning);
			if (!"NONE".equalsIgnoreCase(subPartitioningType)) {
				partitioning.setSubPartitioningType(subPartitioningType);
				setSubPartitionColumnInfo(connection,
						table.getSchemaName(), table.getName(),
						partitioning);
			}
		}
		if (!"NONE".equalsIgnoreCase(subPartitioningType)) {
			setTableSubPartitionInfo(connection, table.getSchemaName(),
					table.getName(), partition,
					PartitioningType.parse(subPartitioningType));
		}
	}
	
	private Partition addPartition(ExResultSet rs, Partitioning partitioning)
			throws SQLException {
		String partitionName = getString(rs, "PARTITION_NAME");
		Partition partition = new Partition(partitionName);
		setPartitionDetails(rs, partition);
		partitioning.getPartitions().add(partition);
		return partition;
	}
	
	private void setPartitionDetails(ExResultSet rs, AbstractPartition<?> partition) throws SQLException{
		String highValue = getString(rs, "HIGH_VALUE");
		String compression = getString(rs, "COMPRESSION");
		partition.setHighValue(highValue);
		if (!"DISABLED".equalsIgnoreCase(compression)) {
			partition.setCompression(true);
		}
		partition.setTableSpaceName(rs.getString("TABLESPACE_NAME"));
		for(String key:OracleUtils.getTableStatisticsKeys()){
			this.setSpecifics(rs, key, partition);
		}
		//
		Statistics.ROWS.setValue(rs, "NUM_ROWS", partition);
		Statistics.AVG_ROW_LENGTH.setValue(rs, "AVG_ROW_LEN", partition);
		this.setStatistics(rs, "BLOCKS", partition);
		this.setStatistics(rs, "EMPTY_BLOCKS", partition);
		this.setStatistics(rs, "AVG_SPACE", partition);
		this.setStatistics(rs, "CHAIN_CNT", partition);
		this.setStatistics(rs, "SAMPLE_SIZE", partition);
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
				"TABLE", objectName, partitionInfo);
	}

	/**
	 * サブパーティションカラム情報を設定します
	 * 
	 * @param connection
	 * @param schemaName
	 *            スキーマ名
	 * @param objectName
	 *            テーブル名 or インデックス名
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
				context, "TABLE", objectName, partitionInfo);
	}

	/**
	 * テーブルサブパーティション情報の取得(Oracle用)
	 * 
	 * @param connection
	 * @param schemaName
	 * @param tableName
	 */
	protected void setTableSubPartitionInfo(Connection connection,
			String schemaName, String tableName, final Partition partition,
			PartitioningType subPartitioningType) {
		SqlNode node = getSqlNodeCache().getString("tableSubPartitions.sql");
		ParametersContext context = newParametersContext(connection, null,
				schemaName);
		context.put(SchemaProperties.TABLE_NAME.getLabel(), tableName);
		context.put("partitionName", partition.getName());
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				addSubPartition(rs, partition);
			}
		});
	}
	
	private SubPartition addSubPartition(ExResultSet rs, final Partition parent)
			throws SQLException {
		String partitionName = getString(rs, "SUBPARTITION_NAME");
		SubPartition partition = new SubPartition(partitionName);
		setPartitionDetails(rs, partition);
		parent.getSubPartitions().add(partition);
		return partition;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new OracleColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new OracleIndexReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new OracleUniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new OracleCheckConstraintReader(this.getDialect());
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new OracleForeignKeyConstraintReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.TableReader#newExcludeConstraintReader
	 * ()
	 */
	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}

}
