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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
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
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Table.TableDataStoreType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

public class SapHanaTableReader extends TableReader {

	protected SapHanaTableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Table> result = list();
		final DoubleKeyMap<String, String, Table> tableMap = CommonUtils.doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Table table = createTable(rs);
				tableMap.put(table.getSchemaName(), table.getName(), table);
				result.add(table);
			}
		});
		SqlNode partNode = getPartitionSqlSqlNode(productVersionInfo);
		ParametersContext ccontext=context.clone();
		ccontext.put(SCHEMA_NAME, tableMap.keySet());
		ccontext.put(TABLE_NAME, tableMap.secondKeySet());
		execute(connection, partNode, ccontext, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schemaName=this.getString(rs, SCHEMA_NAME);
				String tableName=this.getString(rs, TABLE_NAME);
				Table table = tableMap.get(schemaName, tableName);
				table.toPartitioning();
				String level1Expression=this.getString(rs, "LEVEL_1_EXPRESSION");
				if(table.getPartitioning().getPartitioningColumns().isEmpty()) {
					String[] args=level1Expression.split("\\s*,\\s*");
					for(String arg:args) {
						String col=CommonUtils.unwrap(arg, "\"");
						if (!CommonUtils.isEmpty(col)) {
							table.getPartitioning().getPartitioningColumns().add(col);
						}
					}
				}
				String level2Expression=this.getString(rs, "LEVEL_2_EXPRESSION");
				if(table.getPartitioning().getSubPartitioningColumns().isEmpty()) {
					String[] args=level2Expression.split("\\s*,\\s*");
					for(String arg:args) {
						String col=CommonUtils.unwrap(arg, "\"");
						if (!CommonUtils.isEmpty(col)) {
							table.getPartitioning().getSubPartitioningColumns().add(col);
						}
					}
				}
				if(table.getPartitioning().getPartitioningType()==null) {
					int level1Count=this.getInteger(rs, "LEVEL_1_COUNT");
					//int level2Count=this.getInteger(rs, "LEVEL_2_COUNT");
					String level1Type=this.getString(rs, "LEVEL_1_TYPE");
					String level2Type=this.getString(rs, "LEVEL_2_TYPE");
					table.getPartitioning().setPartitioningType(PartitioningType.parse(level1Type));
					table.getPartitioning().setSubPartitioningType(PartitioningType.parse(level2Type));
					if (table.getPartitioning().getPartitioningType().isSizePartitioning()) {
						table.getPartitioning().setPartitionSize(level1Count);
					}
					if (table.getPartitioning().getSubPartitioningType()!=null&&table.getPartitioning().getSubPartitioningType().isSizePartitioning()) {
						table.getPartitioning().setSubPartitionSize(level1Count);
					}
					setStatistics(rs, "EXTENDED_STORAGE_ENABLE_DELTA", table.getPartitioning());
				}
				int level1Partition=this.getInteger(rs, "LEVEL_1_PARTITION");
				int level2Partition=this.getInteger(rs, "LEVEL_2_PARTITION");
				Partition partition;
				if (table.getPartitioning().getPartitions().size()>=level1Partition&&level1Partition>0) {
					partition=table.getPartitioning().getPartitions().get(level1Partition-1);
				} else {
					partition=table.getPartitioning().getPartitions().newElement();
					partition.setId(this.getString(rs, "PART_ID"));
					String levelRangeMinValue=this.getString(rs, "LEVEL_1_RANGE_MIN_VALUE");
					String levelRangeMaxValue=this.getString(rs, "LEVEL_1_RANGE_MAX_VALUE");
					partition.setLowValue(levelRangeMinValue);
					partition.setHighValue(levelRangeMaxValue);
					if (level2Partition==0) {
						setPartitionData(rs, partition);
					}
					table.getPartitioning().getPartitions().add(partition);
				}
				if (level2Partition!=0) {
					SubPartition subpartition=partition.getSubPartitions().newElement();
					subpartition.setId(this.getString(rs, "PART_ID"));
					subpartition.setName(subpartition.getId());
					String levelRangeMinValueSub=this.getString(rs, "LEVEL_2_RANGE_MIN_VALUE");
					String levelRangeMaxValueSub=this.getString(rs, "LEVEL_2_RANGE_MAX_VALUE");
					subpartition.setLowValue(levelRangeMinValueSub);
					subpartition.setHighValue(levelRangeMaxValueSub);
					setPartitionData(rs, subpartition);
					partition.getSubPartitions().add(subpartition);
				} else {
					partition.setName(partition.getId());
				}
				result.add(table);
			}
		});
		return result;
	}
	
	private void setPartitionData(ExResultSet rs, AbstractPartition<?> partition) throws SQLException {
		setSpecifics(rs, "LOAD_UNIT", partition);
		setSpecifics(rs, "INSERT", o-> !Boolean.TRUE.equals(o), partition);
		setSpecifics(rs, "STORAGE_TYPE", partition);
		int drt=this.getInt(rs, "DYNAMIC_RANGE_THRESHOLD");
		if (drt!=-1) {
			partition.getStatistics().put("DYNAMIC_RANGE_THRESHOLD", drt);
		}
		setSpecifics(rs, "DYNAMIC_RANGE_INTERVAL", partition);
		setSpecifics(rs, "PERSISTENT_MEMORY", partition);
		setSpecifics(rs, "GROUP_TYPE", partition);
		setSpecifics(rs, "SUB_TYPE", partition);
		setSpecifics(rs, "GROUP_NAME", partition);
	}

	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table = createTable(getString(rs, TABLE_NAME));
		table.setSchemaName(getString(rs, SCHEMA_NAME));
		table.setId("" + rs.getLong("TABLE_OID"));
		table.setRemarks(getString(rs, "COMMENTS"));
		table.setTableDataStoreType(getString(rs, "TABLE_TYPE"));
		setSpecifics(rs, table);
		setStatistics(rs, table);
		return table;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables.sql");
	}

	protected SqlNode getPartitionSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("partitions.sql");
	}
	
	protected void setSpecifics(ExResultSet rs, Table table)
			throws SQLException {
		setSpecifics(rs, "IS_LOGGED", table);
		setSpecifics(rs, "IS_SYSTEM_TABLE", table);
		setSpecifics(rs, "IS_COLUMN_TABLE", table);
		setSpecifics(rs, "IS_INSERT_ONLY", table);
		setSpecifics(rs, "IS_TENANT_SHARED_DATA", table);
		setSpecifics(rs, "IS_TENANT_SHARED_METADATA", table);
		setSpecifics(rs, "SESSION_TYPE", table);
		setSpecifics(rs, "IS_TEMPORARY", table);
		setSpecifics(rs, "TEMPORARY_TABLE_TYPE", table);
		setSpecifics(rs, "IS_USER_DEFINED_TYPE", table);
		setSpecifics(rs, "USES_EXTKEY", table);
		setSpecifics(rs, "AUTO_MERGE_ON", table);
		if (table.getTableDataStoreType().isColumn()) {
			setSpecifics(rs, "PARTITION_SPEC", table);
			setSpecifics(rs, "USES_DIMFN_CACHE", table);
			setSpecifics(rs, "IS_PUBLIC", table);
			setSpecifics(rs, "COMPRESSED_EXTKEY", table);
			setSpecifics(rs, "HAS_TEXT_FIELDS", table);
			setSpecifics(rs, "USES_QUEUE_TABLE", table);
			setSpecifics(rs, "IS_PRELOAD", table);
			setSpecifics(rs, "IS_PARTIAL_PRELOAD", table);
		}
		setStatistics(rs, "FIXED_PART_SIZE", table);
	}

	protected void setStatistics(ExResultSet rs, Table table)
			throws SQLException {
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SapHanaColumnReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new SapHanaUniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.metadata.TableReader#newForeignKeyConstraintReader()
	 */
	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new SapHanaForeignKeyConstraintReader(this.getDialect());
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.metadata.TableReader#newIndexReader()
	 */
	@Override
	protected IndexReader newIndexReader() {
		return new SapHanaIndexReader(this.getDialect());
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
