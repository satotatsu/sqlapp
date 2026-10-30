/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcTableReader;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
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
	public static final String INFORMIX_ROLLING_FRAGMENTS = "INFORMIX_ROLLING_FRAGMENTS";
	public static final String INFORMIX_ROLLING_SIZE_KB = "INFORMIX_ROLLING_SIZE_KB";
	public static final String INFORMIX_ROLLING_ACTION = "INFORMIX_ROLLING_ACTION";
	public static final String INFORMIX_ROLLING_SCOPE = "INFORMIX_ROLLING_SCOPE";
	public static final String INFORMIX_INTERVAL_DISABLED = "INFORMIX_INTERVAL_DISABLED";
	public static final String INFORMIX_INTERVAL_FRAGMENT = "INFORMIX_INTERVAL_FRAGMENT";
	public static final String INFORMIX_FRAGMENT_HEADER = "INFORMIX_FRAGMENT_HEADER_";
	public static final String INFORMIX_FRAGMENT_EXPRESSION = "INFORMIX_FRAGMENT_EXPRESSION";
	public static final String INFORMIX_ROW_TYPE = "INFORMIX_ROW_TYPE";
	public static final String INFORMIX_ROW_TYPE_OWNER = "INFORMIX_ROW_TYPE_OWNER";
	public static final String INFORMIX_BACKING_INDEX_REQUIRED = "INFORMIX_BACKING_INDEX_REQUIRED";

	public InformixTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setMetadataDetail(final Connection connection, final ParametersContext context,
			final List<Table> tableList) throws SQLException {
		if (tableList.isEmpty()) {
			return;
		}
		super.setMetadataDetail(connection, context, tableList);
		final DoubleKeyMap<String, String, Table> tables = SchemaUtils.toDoubleKeyMap(tableList);
		setTableTypes(connection, tables);
		setUniqueConstraints(connection, tables);
		setFragmentation(connection, tables);
		setConstraintOwners(connection, tables);
	}

	protected void setTableTypes(final Connection connection, final DoubleKeyMap<String, String, Table> tables) {
		if (tables.keySet().isEmpty())
			return;
		final var context = defaultParametersContext(connection);
		context.put(SCHEMA_NAME, tables.keySet());
		context.put(TABLE_NAME, tables.secondKeySet());
		execute(connection, getSqlNodeCache().getString("tableTypes.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final var table = tables.get(getString(rs, SCHEMA_NAME), getString(rs, TABLE_NAME));
				if (table == null || table.getCatalogName() != null
						&& !table.getCatalogName().equals(getString(rs, CATALOG_NAME)))
					return;
				final String type = getString(rs, "row_type_name");
				if (rs.getInt("row_type_id") > 0 && type == null)
					throw new IllegalStateException("Missing Informix table ROW type identity: " + table.getName());
				if (type != null) {
					table.getSpecifics().put(INFORMIX_ROW_TYPE, type);
					table.getSpecifics().put(INFORMIX_ROW_TYPE_OWNER, getString(rs, "row_type_owner"));
				}
				final String parent = getString(rs, "parent_name");
				if (parent != null) {
					if (type == null)
						throw new IllegalStateException(
								"Informix table inheritance requires its ROW type: " + table.getName());
					table.getInherits().add(new Table(parent).setSchemaName(getString(rs, "parent_owner"))
							.setCatalogName(getString(rs, CATALOG_NAME)));
				}
			}
		});
	}

	protected void setConstraintOwners(final Connection connection, final DoubleKeyMap<String, String, Table> tables) {
		if (tables.keySet().isEmpty())
			return;
		final ParametersContext context = defaultParametersContext(connection);
		context.put(SCHEMA_NAME, tables.keySet());
		context.put(TABLE_NAME, tables.secondKeySet());
		execute(connection, getSqlNodeCache().getString("constraintOwners.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Table table = tables.get(getString(rs, SCHEMA_NAME), getString(rs, TABLE_NAME));
				if (table == null || table.getCatalogName() != null
						&& !table.getCatalogName().equals(getString(rs, CATALOG_NAME)))
					return;
				var constraint = table.getConstraints().get(getString(rs, CONSTRAINT_NAME));
				if (constraint == null && "N".equals(getString(rs, "constraint_type"))) {
					final var column = table.getColumns().get(getString(rs, COLUMN_NAME));
					if (column == null)
						throw new IllegalStateException("Cannot resolve Informix NOT NULL column: " + table.getName());
					final var notNull = new com.sqlapp.data.schemas.NotNullConstraint(getString(rs, CONSTRAINT_NAME));
					InformixConstraintModes.apply(notNull, getString(rs, "constraint_state"));
					notNull.setColumn(column);
					table.getConstraints().add(notNull);
					constraint = notNull;
				}
				if (constraint != null)
					InformixConstraintModes.apply(constraint, getString(rs, "constraint_state"));
				final String owner = getString(rs, "constraint_owner");
				if (constraint != null && owner != null && !owner.equals(table.getSchemaName()))
					constraint.getSpecifics().put(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER, owner);
			}
		});
		for (String schema : tables.keySet())
			for (String name : tables.secondKeySet()) {
				final Table table = tables.get(schema, name);
				if (table == null)
					continue;
				final var primary = table.getConstraints().getPrimaryKeyConstraint();
				for (var column : table.getColumns()) {
					var named = table.getConstraints().getNotNullConstraints().stream()
							.filter(c -> java.util.Objects.equals(column.getName(), c.getColumnName())).findFirst();
					boolean primaryColumn = primary != null && primary.getColumns().stream()
							.anyMatch(c -> java.util.Objects.equals(c.getName(), column.getName()));
					if (column.getSpecifics().containsKey(InformixConstraintModes.NOT_NULL_NAME) && named.isEmpty())
						throw new IllegalStateException(
								"Cannot resolve named Informix NOT NULL constraint: " + column.getName());
					column.getSpecifics().remove(InformixConstraintModes.NOT_NULL_NAME);
					column.getSpecifics().remove(InformixConstraintModes.NOT_NULL_OWNER);
					column.getSpecifics().remove(InformixConstraintModes.NOT_NULL_MODE);
					if (named.isPresent() || primaryColumn)
						column.setNotNull(column.isIdentity() || named.isPresent() && named.get().isEnable()
								|| primaryColumn && primary.isEnable());
				}
			}
	}

	protected void setUniqueConstraints(final Connection connection, final DoubleKeyMap<String, String, Table> tables)
			throws SQLException {
		if (tables.keySet().isEmpty()) {
			return;
		}
		final ParametersContext context = defaultParametersContext(connection);
		context.put(SCHEMA_NAME, tables.keySet());
		context.put(TABLE_NAME, tables.secondKeySet());
		final InformixIndexReader reader = newIndexMetadataReader();
		initializeChild(reader);
		for (final InformixIndexReader.Metadata metadata : reader.readIndexes(connection, context, true)) {
			final Index index = metadata.index();
			final boolean backing = "true".equals(index.getSpecifics().get(INFORMIX_BACKING_INDEX_REQUIRED))
					|| index.getIndexType() == com.sqlapp.data.schemas.IndexType.Clustered
					|| index.getPartitioning() != null
					|| index.getSpecifics().get(InformixIndexReader.INFORMIX_ACCESS_METHOD) != null
					|| index.getColumns().stream()
							.anyMatch(c -> c.getSpecifics().get(InformixIndexReader.INFORMIX_OPERATOR_CLASS) != null);
			final Table table = tables.get(index.getSchemaName(), index.getTableName());
			if (table == null
					|| table.getCatalogName() != null && !table.getCatalogName().equals(index.getCatalogName()))
				continue;
			if (table.getConstraints().get(metadata.constraintName()) != null)
				continue;
			final UniqueConstraint constraint = new UniqueConstraint(metadata.constraintName(), false);
			if (index.getIndexType() == com.sqlapp.data.schemas.IndexType.Function) {
				throw new UnsupportedOperationException(
						"Functional Informix UNIQUE constraint is not representable: " + metadata.constraintName());
			}
			index.getColumns().forEach(column -> {
				if (table.getColumns().get(column.getName()) == null) {
					throw new IllegalStateException(
							"Missing Informix UNIQUE constraint column: " + table.getName() + "." + column.getName());
				}
				constraint.getColumns().add(table.getColumns().get(column.getName()), column.getOrder());
			});
			table.getConstraints().add(constraint);
			if (backing) {
				if (index.getName().startsWith(" "))
					index.setName("sqlapp_" + Integer.toUnsignedString(
							(table.getSchemaName() + "." + table.getName() + "." + constraint.getName()).hashCode(),
							36));
				if (table.getIndexes().contains(index.getName()))
					throw new IllegalStateException("Ambiguous Informix backing index: " + index.getName());
				table.getIndexes().add(index);
				constraint.setIndexName(index.getName());
				constraint.getSpecifics().put(INFORMIX_BACKING_INDEX_REQUIRED, "true");
			}
		}
	}

	protected InformixIndexReader newIndexMetadataReader() {
		return new InformixIndexReader(getDialect());
	}

	protected void setFragmentation(final Connection connection, final DoubleKeyMap<String, String, Table> tables) {
		if (tables.keySet().isEmpty()) {
			return;
		}
		final ParametersContext context = defaultParametersContext(connection);
		context.put(SCHEMA_NAME, tables.keySet());
		context.put(TABLE_NAME, tables.secondKeySet());
		execute(connection, getFragmentsSqlNode(), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Table table = tables.get(getString(rs, SCHEMA_NAME), getString(rs, TABLE_NAME));
				if (table == null || table.getCatalogName() != null
						&& !table.getCatalogName().equals(getString(rs, CATALOG_NAME))) {
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
		if (!table.getInherits().isEmpty())
			return null; // Storage is inherited, not a second independent policy.
		final String strategy = rs.getString(4);
		if ("I".equalsIgnoreCase(strategy)) {
			table.setTableSpaceName(rs.getString(7));
			return null;
		}
		Partitioning partitioning = table.getPartitioning();
		if (partitioning == null) {
			partitioning = new Partitioning();
			partitioning.setPartitioningType(toPartitioningType(strategy));
			partitioning.getSpecifics().put(INFORMIX_FRAGMENT_STRATEGY, strategy);
			table.setPartitioning(partitioning);
		}
		final int position = rs.getInt("position");
		final int flags = rs.getInt("fragment_flags");
		if (position == -4) {
			readRollingWindow(rs, partitioning, flags);
		}
		if (position == -2 && (flags & 2048) != 0) {
			partitioning.getSpecifics().put(INFORMIX_INTERVAL_DISABLED, "true");
		}
		if (position < 0) {
			partitioning.getSpecifics().put(INFORMIX_FRAGMENT_HEADER + position, rs.getString(6));
			return null;
		}
		final Partition partition = new Partition(rs.getString(5));
		partition.setTableSpaceName(rs.getString(7));
		if ((flags & 512) != 0)
			partition.getSpecifics().put(INFORMIX_INTERVAL_FRAGMENT, "true");
		partition.getSpecifics().put(INFORMIX_FRAGMENT_EXPRESSION, rs.getString(6));
		partitioning.getPartitions().add(partition);
		return partition;
	}

	private void readRollingWindow(final ExResultSet rs, final Partitioning partitioning, final int flags)
			throws SQLException {
		// Catalog flags confirmed by DETACH/DISCARD and ANY/INTERVAL ONLY/FIRST
		// round-trip tests.
		final int action = flags & 24;
		final int scope = flags & 96;
		if ((flags & ~120) != 0 || action != 8 && action != 16 || scope == 96) {
			throw new UnsupportedOperationException("Unknown Informix rolling-window flags: " + flags);
		}
		final int count = rs.getInt("rolling_fragment_limit");
		if (count < 0)
			throw new IllegalStateException("Invalid Informix rolling fragment count: " + count);
		if (count > 0)
			partitioning.getSpecifics().put(INFORMIX_ROLLING_FRAGMENTS, Integer.toString(count));
		final double size = rs.getDouble("rolling_size");
		final double unit = rs.getDouble("rolling_size_unit");
		if (!Double.isFinite(size) || !Double.isFinite(unit) || size < 0 || unit < 0 || (size == 0) != (unit == 0)) {
			throw new IllegalStateException("Invalid Informix rolling-window storage limit");
		}
		if (size > 0) {
			final var kilobytes = java.math.BigDecimal.valueOf(size).multiply(java.math.BigDecimal.valueOf(unit))
					.toBigIntegerExact();
			partitioning.getSpecifics().put(INFORMIX_ROLLING_SIZE_KB, kilobytes.toString());
		}
		if (count == 0 && size == 0)
			throw new IllegalStateException("Informix rolling-window limit is missing");
		partitioning.getSpecifics().put(INFORMIX_ROLLING_ACTION, action == 8 ? "DISCARD" : "DETACH");
		partitioning.getSpecifics().put(INFORMIX_ROLLING_SCOPE,
				scope == 32 ? "ANY" : scope == 64 ? "INTERVAL ONLY" : "INTERVAL FIRST");
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
