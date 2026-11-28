/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.MigrationDiagnostic;
import com.sqlapp.data.db.dialect.MigrationDiagnostic.Severity;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/**
 * Converts column defaults of tables to be created or changed in the target.
 * <p>
 * The source expression stays in the source Schema; only the target table copy
 * used for SQL generation is changed. Bulk copies map every source column to a
 * target column and bind every value, NULL included, so a changed or omitted
 * default does not affect copied rows: it affects inserts after cutover.
 * </p>
 */
final class TransferDefaultConversions {

	private TransferDefaultConversions() {
	}

	/**
	 * Resolves the dialect that read the source Schema from its product metadata.
	 *
	 * @return the dialect, or {@code null} when the product is unknown
	 */
	static Dialect sourceDialect(final Schema schema) {
		return sourceDialect(schema, null);
	}

	/**
	 * Resolves the source dialect. An explicit product in the settings takes
	 * precedence over the Schema metadata.
	 *
	 * @return the dialect, or {@code null} when the product is unknown
	 * @throws TransferSchemaPreparation.ValidationException when an explicit
	 *                                                       product is not
	 *                                                       supported
	 */
	static Dialect sourceDialect(final Schema schema, final TransferJobConfiguration.DefaultConversion settings) {
		if (settings != null && settings.sourceProductName() != null) {
			final int major = settings.sourceProductMajorVersion() == null ? 0 : settings.sourceProductMajorVersion();
			final Dialect dialect = resolve(settings.sourceProductName(), major, 0, null);
			if (dialect == null)
				throw new TransferSchemaPreparation.ValidationException("defaultConversion.sourceProductName "
						+ settings.sourceProductName() + " is not a supported database product name.");
			return dialect;
		}
		if (schema == null || schema.getProductName() == null || schema.getProductName().isBlank()) {
			return null;
		}
		final int major = schema.getProductMajorVersion() == null ? 0 : schema.getProductMajorVersion();
		final int minor = schema.getProductMinorVersion() == null ? 0 : schema.getProductMinorVersion();
		return resolve(schema.getProductName(), major, minor, schema.getProductRevision());
	}

	private static Dialect resolve(final String product, final int major, final int minor, final Integer revision) {
		final Dialect dialect = DialectResolver.getInstance().getDialect(product, major, minor, revision);
		return dialect == null || dialect.getClass() == Dialect.class ? null : dialect;
	}

	/** Converts with default settings; every task is prepared. */
	static List<TransferDefaultConversion> apply(final Dialect sourceDialect, final Dialect targetDialect,
			final List<TransferSchemaPreparation.Selection> tasks, final Map<String, Table> targets,
			final boolean copiesRows, final TransferJobConfiguration.CompatibilityPolicy policy,
			final List<String> warnings) {
		return apply(sourceDialect, targetDialect, tasks, tasks, targets, copiesRows, policy, null, warnings);
	}

	/**
	 * Converts defaults of the target tables in place and records the results.
	 *
	 * @param sourceDialect dialect that read the source, or {@code null}
	 * @param targetDialect target dialect
	 * @param selected      every selected task, used to validate column overrides
	 * @param prepared      tasks whose target tables are created or changed
	 * @param targets       target table copies keyed by source table name
	 * @param copiesRows    whether the transfer copies rows into these tables
	 * @param policy        compatibility policy for the warnings
	 * @param settings      optional DEFAULT conversion settings of the job
	 * @param warnings      plan warnings to extend
	 * @return structured conversion results
	 * @throws TransferSchemaPreparation.ValidationException when an override
	 *                                                       matches no selected
	 *                                                       column, or strict
	 *                                                       conversion leaves open
	 *                                                       defaults
	 */
	static List<TransferDefaultConversion> apply(final Dialect sourceDialect, final Dialect targetDialect,
			final List<TransferSchemaPreparation.Selection> selected,
			final List<TransferSchemaPreparation.Selection> prepared, final Map<String, Table> targets,
			final boolean copiesRows, final TransferJobConfiguration.CompatibilityPolicy policy,
			final TransferJobConfiguration.DefaultConversion settings, final List<String> warnings) {
		final Map<List<String>, DefaultValueMigration.Override> overrides = overrides(settings, selected);
		final boolean strict = settings != null && settings.strictConversion();
		final var options = strict ? DefaultValueMigration.Options.STRICT : DefaultValueMigration.Options.DEFAULT;
		final List<TransferDefaultConversion> conversions = new ArrayList<>();
		for (final var task : prepared) {
			final Table source = task.getEffectiveSourceTable();
			final Table target = targets.get(source.getName());
			if (target == null) {
				continue;
			}
			for (final Column sourceColumn : source.getColumns()) {
				final var override = overrides.get(List.of(source.getName(), sourceColumn.getName()));
				if (sourceColumn.getDefaultValue() == null && override == null) {
					continue;
				}
				final Column targetColumn = target.getColumns().get(sourceColumn.getName());
				if (targetColumn == null) {
					continue;
				}
				final var result = DefaultValueMigration.convert(sourceColumn, sourceDialect,
						targetColumn.clone().setDefaultValue(null), targetDialect, options, override);
				targetColumn.setDefaultValue(result.targetExpression());
				final var impact = copiesRows ? TransferDefaultConversion.TransferImpact.INDEPENDENT
						: TransferDefaultConversion.TransferImpact.NOT_APPLICABLE;
				final var conversion = new TransferDefaultConversion(task.getTaskId(), target.getName(),
						targetColumn.getName(), result.outcome(), result.sourceExpression(), result.targetExpression(),
						result.differences(), result.verification(), impact, result.diagnostics());
				conversions.add(conversion);
				if (strict) {
					continue;
				}
				for (final MigrationDiagnostic diagnostic : result.diagnostics()) {
					if (diagnostic.severity() == Severity.INFO
							&& result.outcome() == DefaultValueMigration.Outcome.PRESERVED) {
						continue;
					}
					TransferSchemaPreparation.compatibility(policy, warnings, warning(conversion, diagnostic));
				}
			}
		}
		if (strict) {
			final List<String> open = new ArrayList<>();
			for (final var conversion : conversions)
				for (final var diagnostic : conversion.diagnostics())
					if (diagnostic.isBlocking())
						open.add(conversion.table() + "." + conversion.column() + " " + diagnostic.code());
			if (!open.isEmpty())
				throw new TransferSchemaPreparation.ValidationException("Strict DEFAULT conversion leaves "
						+ open.size() + " open finding(s): "
						+ String.join("; ", open.subList(0, Math.min(open.size(), 20)))
						+ (open.size() > 20 ? "; ..." : "")
						+ ". Add defaultConversion.columns replace or remove entries, or turn strict off to proceed with warnings.");
			for (final var conversion : conversions)
				for (final var diagnostic : conversion.diagnostics())
					TransferSchemaPreparation.compatibility(policy, warnings, warning(conversion, diagnostic));
		}
		return List.copyOf(conversions);
	}

	private static Map<List<String>, DefaultValueMigration.Override> overrides(
			final TransferJobConfiguration.DefaultConversion settings,
			final List<TransferSchemaPreparation.Selection> selected) {
		if (settings == null || settings.columns().isEmpty()) {
			return Map.of();
		}
		final Map<List<String>, DefaultValueMigration.Override> overrides = new java.util.HashMap<>();
		final List<String> unknown = new ArrayList<>();
		for (final var entry : settings.columns()) {
			final boolean found = selected.stream().map(TransferSchemaPreparation.Selection::getEffectiveSourceTable)
					.anyMatch(table -> table.getName().equals(entry.table())
							&& table.getColumns().get(entry.column()) != null);
			if (!found) {
				unknown.add(entry.table() + "." + entry.column());
			}
			overrides.put(List.of(entry.table(), entry.column()), entry.toOverride());
		}
		if (!unknown.isEmpty())
			throw new TransferSchemaPreparation.ValidationException("defaultConversion.columns names "
					+ String.join(", ", unknown)
					+ ", which match no selected table column. Use the target table and column names after name mapping.");
		return overrides;
	}

	/**
	 * Keeps an existing target DEFAULT when the planned DEFAULT is recognized with
	 * the same meaning, so that a database's normalized spelling (for example
	 * PostgreSQL {@code (statement_timestamp())::date} for
	 * {@code CAST(statement_timestamp() AS date)}) is not reported as a change.
	 * Unrecognized expressions keep the text comparison.
	 *
	 * @param existing table read from the target
	 * @param desired  planned table, changed in place
	 * @param dialect  target dialect
	 */
	static void keepEquivalentDefaults(final Table existing, final Table desired, final Dialect dialect) {
		final var support = dialect.getDefaultExpressionSupport();
		for (final Column column : desired.getColumns()) {
			final Column current = existing.getColumns().get(column.getName());
			if (current == null || column.getDefaultValue() == null || current.getDefaultValue() == null
					|| column.getDefaultValue().equals(current.getDefaultValue())) {
				continue;
			}
			final var planned = support.parse(column.getDefaultValue(), column);
			if (planned instanceof com.sqlapp.data.db.dialect.defaultvalue.DefaultExpression.Unrecognized) {
				continue;
			}
			if (planned.equals(support.parse(current.getDefaultValue(), current))) {
				column.setDefaultValue(current.getDefaultValue());
			}
		}
	}

	private static String warning(final TransferDefaultConversion conversion, final MigrationDiagnostic diagnostic) {
		final boolean open = diagnostic.isBlocking();
		final String prefix = open ? TransferSchemaChangeChecks.DEFAULT_UNRESOLVED
				: TransferSchemaChangeChecks.DEFAULT_CONVERTED;
		final StringBuilder builder = new StringBuilder(prefix).append(' ').append(diagnostic.severity()).append(' ')
				.append(diagnostic.code()).append(" Table ").append(conversion.table()).append(": ")
				.append(diagnostic.message());
		if (open) {
			builder.append(switch (conversion.transferImpact()) {
			case INDEPENDENT -> " Copied rows supply this column; correct or accept the DEFAULT before cutover.";
			case NOT_APPLICABLE -> " Correct or accept the DEFAULT before applications insert rows.";
			case REQUIRES_DEFAULT -> " Copied rows rely on this DEFAULT; supply a value or a target DEFAULT.";
			});
		}
		return builder.toString();
	}
}
