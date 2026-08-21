/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcTableReader;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/** Informix JDBC table reader. */
public class InformixTableReader extends JdbcTableReader {
	public static final String INFORMIX_FRAGMENT_STRATEGY = "INFORMIX_FRAGMENT_STRATEGY";
	public static final String INFORMIX_FRAGMENT_EXPRESSION = "INFORMIX_FRAGMENT_EXPRESSION";

	public InformixTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setMetadataDetail(final Connection connection,
			final ParametersContext context, final List<Table> tableList) throws SQLException {
		super.setMetadataDetail(connection, context, tableList);
		final DoubleKeyMap<String, String, Table> tables = SchemaUtils.toDoubleKeyMap(tableList);
		setUniqueConstraints(connection, tables);
		setFragmentation(connection, tables);
	}

	protected void setUniqueConstraints(final Connection connection,
			final DoubleKeyMap<String, String, Table> tables) throws SQLException {
		final ParametersContext context = defaultParametersContext(connection);
		context.put(SCHEMA_NAME, tables.keySet());
		context.put(TABLE_NAME, tables.secondKeySet());
		final List<UniqueDescriptor> descriptors = new java.util.ArrayList<>();
		execute(connection, getSqlNodeCache().getString("uniqueConstraints.sql"), context,
				new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Table table = tables.get(rs.getString(2), rs.getString(4));
				if (table != null) {
					descriptors.add(new UniqueDescriptor(table, rs.getString(3),
							rs.getInt(6), rs.getString(7)));
				}
			}
		});
		for (UniqueDescriptor descriptor : descriptors) {
			loadUniqueConstraint(connection, descriptor);
		}
	}

	private void loadUniqueConstraint(final Connection connection,
			final UniqueDescriptor descriptor) throws SQLException {
		if (descriptor.table().getConstraints().get(descriptor.name()) != null) {
			return;
		}
		final Map<Integer, String> columns = new HashMap<>();
		try (var statement = connection.prepareStatement(
				"SELECT colno, colname FROM syscolumns WHERE tabid=?")) {
			statement.setInt(1, descriptor.tableId());
			try (var resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					columns.put(resultSet.getInt(1), resultSet.getString(2));
				}
			}
		}
		final StringBuilder sql = new StringBuilder("SELECT ");
		for (int i = 1; i <= 16; i++) {
			if (i > 1) {
				sql.append(',');
			}
			sql.append("part").append(i);
		}
		sql.append(" FROM sysindexes WHERE tabid=? AND idxname=?");
		final UniqueConstraint constraint = new UniqueConstraint(descriptor.name(), false);
		try (var statement = connection.prepareStatement(sql.toString())) {
			statement.setInt(1, descriptor.tableId());
			statement.setString(2, descriptor.indexName());
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return;
				}
				for (int i = 1; i <= 16; i++) {
					final int columnNumber = Math.abs(resultSet.getInt(i));
					if (columnNumber == 0) {
						break;
					}
					final String columnName = columns.get(columnNumber);
					if (columnName != null) {
						constraint.getColumns().add(descriptor.table().getColumns().get(columnName));
					}
				}
			}
		}
		if (!constraint.getColumns().isEmpty()) {
			descriptor.table().getConstraints().add(constraint);
		}
	}

	private record UniqueDescriptor(Table table, String name, int tableId, String indexName) {
	}

	protected void setFragmentation(final Connection connection,
			final DoubleKeyMap<String, String, Table> tables) {
		final ParametersContext context = defaultParametersContext(connection);
		context.put(SCHEMA_NAME, tables.keySet());
		context.put(TABLE_NAME, tables.secondKeySet());
		execute(connection, getFragmentsSqlNode(), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Table table = tables.get(getString(rs, SCHEMA_NAME),
						getString(rs, TABLE_NAME));
				if (table == null) {
					return;
				}
				readFragment(rs, table);
			}
		});
	}

	protected SqlNode getFragmentsSqlNode() {
		return getSqlNodeCache().getString("fragments.sql");
	}

	protected Partition readFragment(final ExResultSet rs, final Table table) throws SQLException {
		final String strategy = rs.getString(4);
		Partitioning partitioning = table.getPartitioning();
		if (partitioning == null) {
			partitioning = new Partitioning();
			partitioning.setPartitioningType(toPartitioningType(strategy));
			partitioning.getSpecifics().put(INFORMIX_FRAGMENT_STRATEGY, strategy);
			table.setPartitioning(partitioning);
		}
		final Partition partition = new Partition(rs.getString(5));
		partition.setTableSpaceName(rs.getString(7));
		partition.getSpecifics().put(INFORMIX_FRAGMENT_EXPRESSION,
				rs.getString(6));
		partitioning.getPartitions().add(partition);
		return partition;
	}

	private PartitioningType toPartitioningType(final String strategy) {
		if ("R".equalsIgnoreCase(strategy)) {
			return PartitioningType.RoundRobin;
		}
		if ("L".equalsIgnoreCase(strategy)) {
			return PartitioningType.List;
		}
		if ("N".equalsIgnoreCase(strategy)) {
			return PartitioningType.Range;
		}
		return null;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new InformixColumnReader(getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new InformixCheckConstraintReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new InformixIndexReader(getDialect());
	}

}
