/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
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
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Statistics;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

public class MySqlTableReader extends TableReader {

	protected MySqlTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Table> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Table table = createTable(rs);
				result.add(table);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables.sql");
	}

	protected Table createTable(final ExResultSet rs) throws SQLException {
		final Table table = createTable(getString(rs, TABLE_NAME));
		table.setCatalogName(getString(rs, TABLE_CATALOG));
		table.setSchemaName(getString(rs, TABLE_SCHEMA));
		table.setRemarks(getString(rs, "TABLE_COMMENT"));
		table.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		table.setLastAlteredAt(rs.getTimestamp("UPDATE_TIME"));
		table.setCollation(getString(rs, "TABLE_COLLATION"));
		this.setSpecifics(rs, "ENGINE", table);
		Statistics.ROWS.setValue(rs, "TABLE_ROWS", table);
		Statistics.AVG_ROW_LENGTH.setValue(rs, "AVG_ROW_LENGTH", table);
		Statistics.DATA_LENGTH.setValue(rs, "DATA_LENGTH", table);
		Statistics.MAX_DATA_LENGTH.setValue(rs, "MAX_DATA_LENGTH", table);
		Statistics.INDEX_LENGTH.setValue(rs, "INDEX_LENGTH", table);
		this.setStatistics(rs, "DATA_FREE", table);
		this.setStatistics(rs, "ROW_FORMAT", table);
		final String format = getString(rs, "ROW_FORMAT");
		if ("COMPRESSED".equalsIgnoreCase(format)) {
			table.setCompression(true);
		}
		this.setStatistics("ROW_FORMAT", format, table);
		final String createOption=getString(rs, "CREATE_OPTIONS");
		if ("partitioned".equalsIgnoreCase(createOption)){
			table.toPartitioning();
		}
		return table;
	}

	@Override
	protected void setMetadataDetail(final Connection connection,
			final ParametersContext context, final List<Table> tableList) throws SQLException {
		super.setMetadataDetail(connection, context, tableList);
		final DoubleKeyMap<String,String,Table> tables=SchemaUtils.toDoubleKeyMap(tableList);
		setPartitioning(connection, context, tables);
	}
	
	/**
	 * テーブルのパーティション情報を設定します
	 * 
	 * @param connection
	 * @param table
	 */
	protected void setPartitioning(final Connection connection, final ParametersContext context, final DoubleKeyMap<String,String,Table> tables) {
		final SqlNode node = getSqlNodeCache().getString("partitions.sql");
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String schemaName=getString(rs,
						SCHEMA_NAME);
				final String tableName=getString(rs,
						TABLE_NAME);
				final Table table=tables.get(schemaName, tableName);
				setPartition(rs, table);
			}
		});
	}

	protected void setPartition(final ExResultSet rs, final Table table) throws SQLException{
		final Dialect dialect = this.getDialect();
		final String partitioningType = getString(rs, "PARTITION_METHOD");
		final String subPartitioningType = getString(rs,
				"SUBPARTITION_METHOD");
		final String partitionName = getString(rs, "PARTITION_NAME");
		final String subPartitionName = getString(rs, "SUBPARTITION_NAME");
		final String partitionDescription = getString(rs,
				"PARTITION_DESCRIPTION");
		final String partitionExpression = dialect.unQuote(getString(rs,
				"PARTITION_EXPRESSION"));
		final String subPartitionExpression = dialect.unQuote(getString(rs,
				"SUBPARTITION_EXPRESSION"));
		table.toPartitioning();
		final Partitioning partitioning = table.getPartitioning();
		partitioning.setPartitioningType(PartitioningType
				.parse(partitioningType));
		partitioning.setSubPartitioningType(PartitioningType
				.parse(subPartitioningType));
		if (!isEmpty(partitionExpression)
				&& !partitioning.getPartitioningColumns().contains(
						partitionExpression)) {
			partitioning.getPartitioningColumns().add(
					partitionExpression);
		}
		Partition partition = partitioning.getPartitions().get(
				partitionName);
		if (partition == null) {
			partition = new Partition(partitionName);
			partition.setHighValue(partitionDescription);
			partition.setRemarks(getString(rs, "PARTITION_COMMENT"));
			if (subPartitionExpression==null){
				setPartitionDetails(rs, partition);
			}
			partitioning.getPartitions().add(partition);
		}
		if (!isEmpty(subPartitionName)) {
			if (!isEmpty(subPartitionExpression)
					&& !partitioning.getSubPartitioningColumns()
							.contains(subPartitionExpression)) {
				partitioning.getSubPartitioningColumns().add(
						subPartitionExpression);
			}
			final SubPartition subPartition = new SubPartition(subPartitionName);
			if (subPartitionExpression!=null){
				setPartitionDetails(rs, subPartition);
			}
			partition.getSubPartitions().add(subPartition);
		}
	}
	
	private void setPartitionDetails(final ExResultSet rs, final AbstractPartition<?> partition) throws SQLException{
		partition.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		partition.setLastAlteredAt(rs.getTimestamp("UPDATE_TIME"));
		Statistics.ROWS.setValue(rs, "TABLE_ROWS", partition);
		Statistics.AVG_ROW_LENGTH.setValue(rs, "AVG_ROW_LENGTH", partition);
		Statistics.DATA_LENGTH.setValue(rs, "DATA_LENGTH", partition);
		Statistics.MAX_DATA_LENGTH.setValue(rs, "MAX_DATA_LENGTH", partition);
		Statistics.INDEX_LENGTH.setValue(rs, "INDEX_LENGTH", partition);
		this.setStatistics(rs, "DATA_FREE", partition);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.metadata.TableReader#newColumnReader()
	 */
	@Override
	protected ColumnReader newColumnReader() {
		return new MySqlColumnReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.metadata.TableReader#newIndexReader()
	 */
	@Override
	protected IndexReader newIndexReader() {
		return new MySqlIndexReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.TableReader#newUniqueConstraintReader
	 * ()
	 */
	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new MySqlUniqueConstraintReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.TableReader#newCheckConstraintReader
	 * ()
	 */
	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.TableReader#newForeignKeyConstraintReader
	 * ()
	 */
	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new MySqlForeignKeyConstraintReader(this.getDialect());
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
