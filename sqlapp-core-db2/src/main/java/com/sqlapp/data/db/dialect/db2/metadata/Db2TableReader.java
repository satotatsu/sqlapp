/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;

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
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.NullsOrder;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Statistics;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

public class Db2TableReader extends TableReader {

	protected Db2TableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNode(productVersionInfo);
		final DoubleKeyMap<String,String,Table> result = CommonUtils.doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Table table = createTable(rs);
				result.put(table.getSchemaName(), table.getName(), table);
			}
		});
		return result.toList();
	}

	protected SqlNode getSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables.sql");
	}

	protected Table createTable(final ExResultSet rs) throws SQLException {
		final Table table = new Table(getString(rs, TABLE_NAME));
		table.setDialect(getDialect());
		table.setSchemaName(getString(rs, SCHEMA_NAME));
		table.setRemarks(getString(rs, REMARKS));
		table.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		table.setLastAlteredAt(rs.getTimestamp("ALTER_TIME"));
		table.setTableSpaceName(getString(rs, TABLESPACE_NAME));
		table.setIndexTableSpaceName(getString(rs, "index_" + TABLESPACE_NAME));
		table.setLobTableSpaceName(getString(rs, "lob_" + TABLESPACE_NAME));
		final String comp = getString(rs, "COMPRESSION");
		table.setCompression("V".equalsIgnoreCase(comp) || "R".equalsIgnoreCase(comp));
		if ("V".equalsIgnoreCase(comp)) {
			table.setCompressionType("VALUE COMPRESSION");
		}
		final String partitionMode=getString(rs,"PARTITION_MODE");
		if (!CommonUtils.isEmpty(CommonUtils.trim(partitionMode))){
			table.toPartitioning();
			if ("H".equals(partitionMode)){
				table.getPartitioning().setPartitioningType(PartitioningType.Hash);
			}else {
				table.getPartitioning().setPartitioningType(PartitioningType.Range);
			}
		}
		this.setSpecifics(rs, "PCTFREE", table);
		Statistics.ROWS.setValue(rs, "CARD", table);
		Statistics.AVG_ROW_LENGTH.setValue(rs, "AVGROWSIZE", table);
		Statistics.AVG_COMPRESSED_ROW_LENGTH.setValue(rs, "AVGCOMPRESSEDROWSIZE", table);
		Statistics.AVG_ROW_COMPRESSION_RAITO.setValue(rs, "AVGROWCOMPRESSIONRATIO", table);
		Statistics.ROW_COMPRESSED.setValue(rs, "PCTROWSCOMPRESSED", table);
		return table;
	}
	
	@Override
	protected void setMetadataDetail(final Connection connection,
			final ParametersContext context, final List<Table> tableList) throws SQLException {
		super.setMetadataDetail(connection, context, tableList);
		final DoubleKeyMap<String,String,Table> tables=SchemaUtils.toDoubleKeyMap(tableList);
		setPartitionExpression(connection, tables);
		setPartitioning(connection, tables);
	}
	
	/**
	 * テーブルのパーティション式を設定します
	 * 
	 * @param connection
	 * @param tables
	 */
	protected void setPartitionExpression(final Connection connection, final DoubleKeyMap<String,String,Table> tables) {
		final SqlNode node = getPartitionExpressionSqlNode();
		final ParametersContext context = this.defaultParametersContext(connection);
		context.put(SCHEMA_NAME, tables.keySet());
		context.put(TABLE_NAME, tables.secondKeySet());
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String schemaName=getString(rs,
						SCHEMA_NAME);
				final String tableName=getString(rs,
						TABLE_NAME);
				final Table table=tables.get(schemaName, tableName);
				readPartitionExrepssion(rs, table);
			}
		});
	}
	
	protected SqlNode getPartitionExpressionSqlNode() {
		return getSqlNodeCache().getString("partitionExpressions.sql");
	}

	protected void readPartitionExrepssion(final ExResultSet rs, final Table table) throws SQLException{
		final String expression = getString(rs,
				"DATAPARTITIONEXPRESSION");
		final boolean nullsFirst = "Y".equalsIgnoreCase(getString(rs,
				"NULLSFIRST"));
		NullsOrder nullsOrder;
		if (nullsFirst){
			nullsOrder=NullsOrder.NullsFirst;
		} else{
			nullsOrder=NullsOrder.NullsLast;
		}
		final Column column=table.getColumns().get(expression);
		if (column!=null){
			table.getPartitioning().getPartitioningColumns().add(column, nullsOrder);
		} else{
			table.getPartitioning().getPartitioningColumns().add(expression, nullsOrder);
		}
	}


	
	/**
	 * テーブルのパーティション情報を設定します
	 * 
	 * @param connection
	 * @param tables
	 */
	protected void setPartitioning(final Connection connection, final DoubleKeyMap<String,String,Table> tables) {
		final SqlNode node = getPartitionSqlNode();
		final ParametersContext context = this.defaultParametersContext(connection);
		context.put(SCHEMA_NAME, tables.keySet());
		context.put(TABLE_NAME, tables.secondKeySet());
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String schemaName=getString(rs,
						SCHEMA_NAME);
				final String tableName=getString(rs,
						TABLE_NAME);
				final Table table=tables.get(schemaName, tableName);
				readPartition(rs, table);
			}
		});
	}
	
	protected SqlNode getPartitionSqlNode() {
		return getSqlNodeCache().getString("partitions.sql");
	}
	
	protected Partition readPartition(final ExResultSet rs, final Table table) throws SQLException{
		final Dialect dialect = this.getDialect();
		final String partitionName = getString(rs, "DATAPARTITIONNAME");
		final String highValue = getString(rs,
				"HIGHVALUE");
		final String lowValue = getString(rs,
				"LOWVALUE");
		final String partitionExpression = dialect.unQuote(getString(rs,
				"PARTITION_EXPRESSION"));
		final String tableSpace = getString(rs,"TABLE_SPACE");
		final String lobTableSpace = getString(rs,"LOB_TABLE_SPACE");
		Partitioning partitioning = null;
		if (table.getPartitioning() == null) {
			partitioning = new Partitioning();
			table.setPartitioning(partitioning);
		} else {
			partitioning = table.getPartitioning();
		}
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
			partition.setLowValue(lowValue);
			partition.setHighValue(highValue);
			partition.setTableSpaceName(tableSpace);
			partition.setLobTableSpaceName(lobTableSpace);
			partitioning.getPartitions().add(partition);
		}
		return partition;
	}
	
	@Override
	protected ColumnReader newColumnReader() {
		return new Db2ColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new Db2IndexReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new Db2UniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new Db2CheckConstraintReader(this.getDialect());
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
		return new Db2ForeignKeyConstraintReader(this.getDialect());
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
