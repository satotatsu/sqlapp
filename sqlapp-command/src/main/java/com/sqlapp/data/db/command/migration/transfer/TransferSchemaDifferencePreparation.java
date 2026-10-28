/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.*;
import com.sqlapp.util.MessageDigests;

/**
 * Selected-table projection over the canonical diff model and target SQL
 * factories.
 */
final class TransferSchemaDifferencePreparation {
	private TransferSchemaDifferencePreparation() {
	}

	static TransferSchemaPreparation.Plan plan(Connection connection, List<TransferSchemaPreparation.Selection> tasks,
			Map<String, Table> desired, SqlFactoryRegistry registry, String jobFingerprint,
			List<String> inheritedWarnings, TransferJobConfiguration.CompatibilityPolicy policy) throws Exception {
		final var first = tasks.getFirst().getEffectiveTargetTable();
		// One bulk Schema metadata capture, never one query per selected table.
		final var actual = TransferJobPreparationService.readSchema(connection, registry.getDialect(),
				first.getSchemaName());
		return compare(actual, tasks, desired, registry, jobFingerprint, inheritedWarnings, policy);
	}

	static TransferSchemaPreparation.Plan compare(Schema actual, List<TransferSchemaPreparation.Selection> tasks,
			Map<String, Table> desired, SqlFactoryRegistry registry, String jobFingerprint,
			List<String> inheritedWarnings, TransferJobConfiguration.CompatibilityPolicy policy) throws Exception {
		final var first = tasks.getFirst().getEffectiveTargetTable();
		final var before = new Schema(first.getSchemaName()).setProductName(actual.getProductName());
		final var after = new Schema(first.getSchemaName()).setProductName(actual.getProductName());
		before.setDialect(registry.getDialect());
		after.setDialect(registry.getDialect());
		final var warnings = new ArrayList<>(inheritedWarnings);
		for (var task : tasks) {
			final var name = task.getEffectiveTargetTable().getName();
			final var old = actual.getTables().stream().filter(table -> name.equals(table.getName())).findFirst()
					.orElse(null);
			final var target = desired.get(task.getEffectiveSourceTable().getName()).clone();
			target.setDialect(registry.getDialect());
			after.getTables().add(target);
			if (old != null) {
				final var captured = old.clone();
				captured.setDialect(registry.getDialect());
				before.getTables().add(captured);
				impacts(old, target, warnings, policy);
			}
		}
		final var difference = before.diff(after, new DefaultSchemaEqualsHandler());
		final var operations = new ArrayList<SqlOperation>();
		// Preserve canonical parent-before-child task order, including mixed CREATE
		// and ALTER work, and isolate unsupported vendor changes per selected table.
		for (var target : after.getTables()) {
			final var old = before.getTables().get(target.getName());
			final var tableDifference = old == null ? null : namedDifference(old, target);
			List<SqlOperation> generated;
			try {
				generated = old == null ? registry.createSql(target, SqlType.CREATE)
						: registry.createSql(tableDifference, SqlType.ALTER);
			} catch (IllegalArgumentException | UnsupportedOperationException unsupported) {
				TransferSchemaPreparation.compatibility(policy, warnings, TransferSchemaChangeChecks.UNRESOLVED
						+ " Table " + target.getName() + ": target factory could not generate its changes ("
						+ unsupported.getClass().getSimpleName()
						+ "). No SQL for this table is included. Review and migrate it manually; other selected-table SQL remains available.");
				continue;
			}
			if ((old == null || tableDifference.getState().isChanged()) && generated.stream()
					.noneMatch(operation -> operation.getSqlText() != null && !operation.getSqlText().isBlank()
							&& (operation.getSqlType() == null || operation.getSqlType().isSql())))
				TransferSchemaPreparation.compatibility(policy, warnings, TransferSchemaChangeChecks.UNRESOLVED
						+ " Table " + target.getName()
						+ ": metadata differs but no executable SQL was generated. Inspect unsupported properties and migrate them manually.");
			operations.addAll(generated);
		}
		final var statements = new ArrayList<TransferSchemaPreparation.Statement>();
		for (var operation : operations) {
			if (operation.getSqlType() != null && !operation.getSqlType().isSql())
				continue;
			if (operation.getSqlText() == null || operation.getSqlText().isBlank())
				continue;
			if (operation.getSqlType() == null)
				throw new IllegalArgumentException(
						"Schema difference factory returned SQL without an operation type; use a separately reviewed SQL migration.");
			if (operation.getSqlType().getSqlMetaType() == com.sqlapp.data.db.sql.SqlMetaType.DML)
				impact(warnings, policy, "Target factory generates " + operation.getSqlType()
						+ " while applying Schema changes. Target data may be filled, rebuilt or removed; source rows are not transferred.");
			else if (!operation.getSqlType().isDdl() && operation.getSqlType() != SqlType.SET_SEARCH_PATH_TO_SCHEMA)
				throw new IllegalArgumentException(
						"Schema difference factory requires unsupported session/transaction operations; use a separately reviewed SQL migration.");
			if (operation.getSqlType() == SqlType.DROP && operation.getOriginal() instanceof Table)
				impact(warnings, policy, "Target factory rebuilds a selected table; existing data may be lost.");
			statements.add(new TransferSchemaPreparation.Statement("schema-difference", operation.getSqlText()));
		}
		if (difference.getState().isChanged())
			TransferSchemaPreparation.compatibility(policy, warnings,
					"Schema differences are generated only for selected tables. Review every CREATE/ALTER/DROP statement, dependencies and vendor properties. "
							+ "SQL completion is not proof of full schema equivalence; unsupported properties may require manual SQL.");
		if (difference.getState().isChanged() && statements.isEmpty())
			TransferSchemaPreparation.compatibility(policy, warnings,
					"Selected Schema metadata differs, but the target factory generated no executable SQL. Differences remain unresolved; review the source and target definitions manually.");
		final var writer = new java.io.StringWriter();
		before.writeXml(writer);
		final String fingerprint = "sha256:" + MessageDigests.SHA256
				.checksumAsString((jobFingerprint + "\n" + writer).getBytes(StandardCharsets.UTF_8));
		return TransferSchemaPreparation.result(fingerprint, statements, warnings,
				TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES);
	}

	private static DbObjectDifference namedDifference(Table original, Table target) {
		final var difference = original.diff(target, new DefaultSchemaEqualsHandler());
		// Transfer intent identifies objects by name and has no rename mapping.
		// Replace positional/LCS pairings with canonical per-object differences so
		// removal + addition cannot also generate a guessed rename of the same object.
		matchNames(difference, SchemaObjectProperties.COLUMNS.getLabel(), original.getColumns(), target.getColumns());
		matchNames(difference, SchemaObjectProperties.CONSTRAINTS.getLabel(), original.getConstraints(),
				target.getConstraints());
		matchNames(difference, SchemaObjectProperties.INDEXES.getLabel(), original.getIndexes(), target.getIndexes());
		return difference;
	}

	private static void matchNames(DbObjectDifference table, String property,
			List<? extends AbstractNamedObject<?>> original, List<? extends AbstractNamedObject<?>> target) {
		if (!(table.getProperties().get(property) instanceof DbObjectDifferenceCollection collection))
			return;
		final var differences = collection.getList();
		differences.clear();
		for (var old : original) {
			final var matching = target.stream().filter(next -> Objects.equals(old.getName(), next.getName())).toList();
			if (matching.size() > 1)
				throw new IllegalArgumentException("Ambiguous named Schema component.");
			differences.add(new DbObjectDifference(old, matching.isEmpty() ? null : matching.getFirst(),
					new DefaultSchemaEqualsHandler()));
		}
		for (var next : target)
			if (original.stream().noneMatch(old -> Objects.equals(old.getName(), next.getName())))
				differences.add(new DbObjectDifference(null, next, new DefaultSchemaEqualsHandler()));
	}

	private static void impacts(Table original, Table target, List<String> warnings,
			TransferJobConfiguration.CompatibilityPolicy policy) {
		if (original.diff(target, new DefaultSchemaEqualsHandler()).getChangedProperties().keySet().stream()
				.anyMatch(key -> !java.util.Set
						.of(SchemaObjectProperties.COLUMNS.getLabel(), SchemaObjectProperties.CONSTRAINTS.getLabel(),
								SchemaObjectProperties.INDEXES.getLabel(), SchemaProperties.REMARKS.getLabel())
						.contains(key)))
			impact(warnings, policy, "Table " + original.getName()
					+ ": physical or other table-level metadata changes may affect existing data.");
		for (var column : target.getColumns())
			if (original.getColumns().get(column.getName()) == null
					&& (column.getDefaultValue() != null || column.getFormula() != null || column.isIdentity()))
				impact(warnings, policy, "Table " + original.getName() + ", column " + column.getName()
						+ ": new default/generated column can populate existing target rows.");
		for (var column : original.getColumns()) {
			final var next = target.getColumns().get(column.getName());
			if (next == null) {
				impact(warnings, policy,
						"Table " + original.getName() + ", column " + column.getName() + ": column removal.");
				continue;
			}
			if (column.getDataType() != next.getDataType() || !Objects.equals(column.getScale(), next.getScale())
					|| !Objects.equals(column.getLength(), next.getLength()) && !(column.getLength() != null
							&& next.getLength() != null && next.getLength() >= column.getLength())
					|| column.isIdentity() != next.isIdentity()
					|| !Objects.equals(column.getFormula(), next.getFormula())
					|| column.diff(next, new DefaultSchemaEqualsHandler()).getChangedProperties().keySet().stream()
							.anyMatch(key -> !java.util.Set.of(SchemaProperties.DATA_TYPE.getLabel(),
									SchemaProperties.LENGTH.getLabel(), SchemaProperties.SCALE.getLabel(),
									SchemaProperties.NOT_NULL.getLabel(), SchemaProperties.DEFAULT_VALUE.getLabel(),
									SchemaProperties.REMARKS.getLabel()).contains(key)))
				impact(warnings, policy, "Table " + original.getName() + ", column " + column.getName()
						+ ": type, precision, generated-value, narrowing or other existing-column metadata changes may affect existing data.");
		}
		for (var constraint : original.getConstraints())
			if (target.getConstraints().get(constraint.getName()) == null || constraint
					.diff(target.getConstraints().get(constraint.getName()), new DefaultSchemaEqualsHandler())
					.getState().isChanged())
				impact(warnings, policy, "Table " + original.getName() + ": removal or replacement of constraint "
						+ constraint.getName() + ".");
		for (var index : original.getIndexes())
			if (target.getIndexes().get(index.getName()) == null
					|| index.diff(target.getIndexes().get(index.getName()), new DefaultSchemaEqualsHandler()).getState()
							.isChanged())
				impact(warnings, policy,
						"Table " + original.getName() + ": removal or replacement of index " + index.getName() + ".");
	}

	private static void impact(List<String> warnings, TransferJobConfiguration.CompatibilityPolicy policy,
			String detail) {
		TransferSchemaPreparation.compatibility(policy, warnings, TransferSchemaChangeChecks.DATA_IMPACT + " " + detail
				+ " Explicit allowDestructiveSchemaChanges: true is required for execution; review does not authorize it.");
	}
}
