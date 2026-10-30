/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static com.sqlapp.data.db.dialect.informix.metadata.InformixTableReader.*;

import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Table;

/** Recreates Informix table fragmentation using the shared partition model. */
public class InformixCreateTableFactory extends AbstractCreateTableFactory<InformixSqlBuilder> {
	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(final Table table) {
		final String rowType = table.getSpecifics().get(INFORMIX_ROW_TYPE);
		if (rowType == null) {
			if (!table.getInherits().isEmpty())
				throw new IllegalArgumentException("Informix UNDER requires its named ROW type: " + table.getName());
			return super.createSql(table);
		}
		final String owner = table.getSpecifics().get(INFORMIX_ROW_TYPE_OWNER);
		if (rowType.isBlank() || owner == null || owner.isBlank() || table.getInherits().size() > 1)
			throw new IllegalArgumentException("Invalid Informix typed table metadata: " + table.getName());
		final var result = new java.util.ArrayList<com.sqlapp.data.db.sql.SqlOperation>();
		final var builder = createSqlBuilder();
		addCreateObject(table, builder);
		builder.space()._add("OF TYPE").space()._add(identifier(owner) + "." + identifier(rowType));
		if (!table.getInherits().isEmpty()) {
			final var parent = table.getInherits().get(0);
			if (table.getPartitioning() != null || table.getTableSpaceName() != null)
				throw new IllegalArgumentException(
						"Informix subtable storage must follow its parent: " + table.getName());
			final String parentOwner = parent.getSchemaName() == null ? table.getSchemaName() : parent.getSchemaName();
			builder.space()._add("UNDER").space()._add(identifier(parentOwner) + "." + identifier(parent.getName()));
		} else
			addOption(table, builder);
		addSql(result, builder, com.sqlapp.data.db.sql.SqlType.CREATE, table);
		for (var column : table.getColumns()) {
			if (column.getDefaultValue() == null)
				continue;
			final var modify = createSqlBuilder();
			modify.alter().table().name(table, getOptions().isDecorateSchemaName()).space()._add("MODIFY (")
					.name(column).space().definition(column, false)._add(')');
			addSql(result, modify, com.sqlapp.data.db.sql.SqlType.ALTER, column);
		}
		addIndexDefinitions(table, result);
		for (var constraint : table.getConstraints().stream()
				.sorted(java.util.Comparator
						.comparingInt(c -> c instanceof com.sqlapp.data.schemas.ForeignKeyConstraint ? 1 : 0))
				.toList()) {
			// Inherited constraints are supplied by the supertable, not recreated on every
			// child.
			if (!table.getInherits().isEmpty()) {
				final var parent = table.getInherits().get(0);
				final var inherited = parent.getConstraints().get(constraint.getName());
				final String ownerKey = com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER;
				if (inherited != null && java.util.Objects.equals(
						constraint.getSpecifics().getOrDefault(ownerKey, table.getSchemaName()),
						inherited.getSpecifics().getOrDefault(ownerKey, parent.getSchemaName())))
					continue;
			}
			result.addAll(getSqlFactoryRegistry().createSql(constraint, com.sqlapp.data.db.sql.SqlType.CREATE));
		}
		addOtherDefinitions(table, result);
		return result;
	}

	private String identifier(final String name) {
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Informix typed table requires resolved type/parent identifiers");
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	@Override
	protected void addUniqueConstraintDefinitions(final Table table, final InformixSqlBuilder builder) {
		for (var constraint : table.getConstraints().getUniqueConstraints())
			if (!requiresBackingIndex(constraint))
				addConstraintDefinition(constraint, builder);
	}

	@Override
	protected void addForeignKeyConstraintDefinitions(final Table table, final InformixSqlBuilder builder) {
		if (table.getConstraints().getUniqueConstraints().stream().noneMatch(this::requiresBackingIndex))
			super.addForeignKeyConstraintDefinitions(table, builder);
	}

	private boolean requiresBackingIndex(final com.sqlapp.data.schemas.UniqueConstraint constraint) {
		return "true".equals(constraint.getSpecifics().get(INFORMIX_BACKING_INDEX_REQUIRED));
	}

	@Override
	protected void addOption(final Table table, final InformixSqlBuilder builder) {
		final Partitioning partitioning = table.getPartitioning();
		if (partitioning == null) {
			if (table.getTableSpaceName() != null) {
				builder.space()._add("IN").space().name(table.getTableSpaceName());
			}
			return;
		}
		String strategy = partitioning.getSpecifics().get(INFORMIX_FRAGMENT_STRATEGY);
		if (strategy == null && partitioning.getPartitioningType() == PartitioningType.RoundRobin) {
			strategy = "R";
		}
		if (strategy == null) {
			throw new IllegalArgumentException("Informix fragmentation strategy is required: " + table.getName());
		}
		if (!"N".equals(strategy) && (partitioning.getSpecifics().get(INFORMIX_INTERVAL_DISABLED) != null
				|| partitioning.getSpecifics().get(INFORMIX_ROLLING_ACTION) != null
				|| partitioning.getSpecifics().get(INFORMIX_ROLLING_FRAGMENTS) != null
				|| partitioning.getSpecifics().get(INFORMIX_ROLLING_SIZE_KB) != null
				|| partitioning.getSpecifics().get(INFORMIX_ROLLING_SCOPE) != null)) {
			throw new IllegalArgumentException(
					"Informix interval/rolling options require RANGE/INTERVAL fragmentation: " + table.getName());
		}
		switch (strategy) {
		case "R" -> builder.space()._add("FRAGMENT BY ROUND ROBIN");
		case "E" -> builder.space()._add("FRAGMENT BY EXPRESSION");
		case "L", "N" -> {
			builder.space()._add("FRAGMENT BY").space()._add("L".equals(strategy) ? "LIST" : "RANGE")._add('(')
					._add(required(partitioning, -3))._add(')');
			if ("N".equals(strategy)) {
				builder.space()._add("INTERVAL(")._add(required(partitioning, -2))._add(')');
				addRollingWindow(partitioning, builder);
				builder.space()._add("STORE IN(")._add(required(partitioning, -1))._add(')');
			}
		}
		default -> throw new UnsupportedOperationException("Unsupported Informix fragmentation strategy: " + strategy);
		}
		if (partitioning.getPartitions().isEmpty()) {
			throw new IllegalArgumentException("Informix fragments are required: " + table.getName());
		}
		int i = 0;
		for (final Partition partition : partitioning.getPartitions()) {
			if ("true".equals(partition.getSpecifics().get(INFORMIX_INTERVAL_FRAGMENT))) {
				if ("true".equals(partitioning.getSpecifics().get(INFORMIX_INTERVAL_DISABLED))) {
					throw new UnsupportedOperationException(
							"Disabled Informix interval with generated fragments cannot be recreated safely: "
									+ table.getName());
				}
				continue; // The interval policy creates these data-dependent fragments during loading.
			}
			if (partition.getTableSpaceName() == null || partition.getTableSpaceName().isBlank()) {
				throw new IllegalArgumentException("Informix fragment dbspace is required: " + partition.getName());
			}
			builder.comma(i++ > 0).space();
			if (partition.getName() != null && !partition.getName().isBlank()) {
				builder._add("PARTITION").space().name(partition.getName()).space();
			}
			if (!"R".equals(strategy)) {
				final String expression = partition.getSpecifics().get(INFORMIX_FRAGMENT_EXPRESSION);
				if (expression == null || expression.isBlank()) {
					throw new IllegalArgumentException(
							"Informix fragment expression is required: " + partition.getName());
				}
				builder._add(expression).space();
			}
			builder._add("IN").space().name(partition.getTableSpaceName());
		}
		if (i == 0)
			throw new IllegalArgumentException("Informix initial range fragments are required: " + table.getName());
	}

	private void addRollingWindow(final Partitioning partitioning, final InformixSqlBuilder builder) {
		final String count = partitioning.getSpecifics().get(INFORMIX_ROLLING_FRAGMENTS);
		final String size = partitioning.getSpecifics().get(INFORMIX_ROLLING_SIZE_KB);
		final String action = partitioning.getSpecifics().get(INFORMIX_ROLLING_ACTION);
		final String scope = partitioning.getSpecifics().get(INFORMIX_ROLLING_SCOPE);
		if (count == null && size == null && action == null && scope == null) {
			if (partitioning.getSpecifics().get(INFORMIX_FRAGMENT_HEADER + "-4") != null)
				throw new IllegalArgumentException(
						"Informix rolling-window metadata is incomplete; read the source again");
			return;
		}
		if (count == null && size == null
				|| !java.util.Set.of("DETACH", "DISCARD").contains(action == null ? "" : action)) {
			throw new IllegalArgumentException(
					"Informix rolling-window requires a positive limit and an explicit DETACH or DISCARD action");
		}
		if (scope != null && !java.util.Set.of("ANY", "INTERVAL ONLY", "INTERVAL FIRST").contains(scope)) {
			throw new IllegalArgumentException("Invalid Informix rolling-window scope: " + scope);
		}
		if (size == null && scope != null && !"INTERVAL FIRST".equals(scope)) {
			throw new IllegalArgumentException("Informix ANY/INTERVAL ONLY requires a storage size limit");
		}
		if (count != null)
			builder.space()._add("ROLLING(")._add(positive(count))._add(" FRAGMENTS)");
		if (size != null)
			builder.space()._add("LIMIT TO ")._add(positive(size))._add(" KB");
		builder.space()._add(action);
		if (size != null && scope != null)
			builder.space()._add(scope);
	}

	private java.math.BigInteger positive(final String value) {
		final var number = new java.math.BigInteger(value);
		if (number.signum() <= 0)
			throw new IllegalArgumentException("Informix rolling-window limit must be positive: " + value);
		return number;
	}

	@Override
	protected void addOtherDefinitions(final Table table,
			final java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		if (table.getSpecifics().get(INFORMIX_ROW_TYPE) == null
				&& table.getConstraints().getUniqueConstraints().stream().anyMatch(this::requiresBackingIndex)) {
			for (var constraint : table.getConstraints().getUniqueConstraints())
				if (requiresBackingIndex(constraint))
					result.addAll(getSqlFactoryRegistry().createSql(constraint, com.sqlapp.data.db.sql.SqlType.CREATE));
			for (var constraint : table.getConstraints().getForeignKeyConstraints())
				result.addAll(getSqlFactoryRegistry().createSql(constraint, com.sqlapp.data.db.sql.SqlType.CREATE));
		}
		if (table.getPartitioning() == null)
			return;
		final String disabled = table.getPartitioning().getSpecifics().get(INFORMIX_INTERVAL_DISABLED);
		if (disabled == null || "false".equals(disabled))
			return;
		if (!"true".equals(disabled))
			throw new IllegalArgumentException("Invalid Informix interval disabled flag: " + disabled);
		final InformixSqlBuilder builder = createSqlBuilder();
		builder._add("ALTER FRAGMENT ON TABLE").space().name(table, getOptions().isDecorateSchemaName()).space()
				._add("MODIFY INTERVAL DISABLED");
		addSql(result, builder, com.sqlapp.data.db.sql.SqlType.ALTER, table);
	}

	private String required(final Partitioning partitioning, final int position) {
		final String value = partitioning.getSpecifics().get(INFORMIX_FRAGMENT_HEADER + position);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing Informix fragmentation header at position " + position);
		}
		return value;
	}
}
