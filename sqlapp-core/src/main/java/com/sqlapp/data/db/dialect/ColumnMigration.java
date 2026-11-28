package com.sqlapp.data.db.dialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.MigrationDiagnostic.Severity;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration;
import com.sqlapp.data.schemas.Column;

/** Explicit conversion of a source column definition for a target dialect. */
public final class ColumnMigration {
	private ColumnMigration() {
	}

	/** Code of results from dialects without a type rule for the column. */
	static final String NO_TYPE_RULE = "NO_TYPE_RULE";

	/**
	 * Applies the target dialect's explicit type rule to the column, including
	 * the rule's own handling of DEFAULT and ON UPDATE. Columns without a rule
	 * are reported as unresolved.
	 *
	 * @param source source column; it is not modified
	 * @param target target dialect
	 * @return conversion result
	 */
	public static Result convert(final Column source, final Dialect target) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");
		return target.convertColumnForMigration(source);
	}

	/**
	 * Converts the column for the target dialect with default conversion.
	 *
	 * @see #convert(Column, Dialect, Dialect, DefaultValueMigration.Options,
	 *      DefaultValueMigration.Override)
	 */
	public static Result convert(final Column source, final Dialect sourceDialect, final Dialect target) {
		return convert(source, sourceDialect, target, DefaultValueMigration.Options.DEFAULT, null);
	}

	/**
	 * Converts the column for the target dialect.
	 * <p>
	 * The target dialect's explicit type rule, when one exists, converts the
	 * type; otherwise the column keeps its shared type and the target dialect
	 * chooses the type name. The default is then preserved or translated by
	 * {@link DefaultValueMigration}. ON UPDATE is retained only for the same
	 * product and otherwise reported as unresolved.
	 * </p>
	 *
	 * @param source        source column; it is not modified
	 * @param sourceDialect dialect that read the source, or {@code null} when unknown
	 * @param target        target dialect
	 * @param options       default conversion options
	 * @param override      explicit default decision for this column, or {@code null}
	 * @return conversion result
	 */
	public static Result convert(final Column source, final Dialect sourceDialect, final Dialect target,
			final DefaultValueMigration.Options options, final DefaultValueMigration.Override override) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");
		final Result typed = target.convertColumnForMigration(detached(source).setDefaultValue(null).setOnUpdate(null));
		final Column candidate;
		final List<MigrationDiagnostic> diagnostics = new ArrayList<>();
		if (typed.isNoTypeRule()) {
			candidate = detached(source);
		} else if (typed.candidate() == null) {
			return typed;
		} else {
			candidate = typed.candidate();
			diagnostics.addAll(typed.diagnostics());
		}
		candidate.setDefaultValue(null).setOnUpdate(null);
		final DefaultValueMigration.Result defaultValue = DefaultValueMigration.convert(source, sourceDialect,
				candidate, target, options, override);
		candidate.setDefaultValue(defaultValue.targetExpression());
		diagnostics.addAll(defaultValue.diagnostics());
		final String onUpdate = source.getOnUpdate();
		if (onUpdate != null && !onUpdate.isBlank()) {
			if (sourceDialect != null && Objects.equals(sourceDialect.getProductName(), target.getProductName())) {
				candidate.setOnUpdate(onUpdate);
			} else {
				diagnostics.add(new MigrationDiagnostic(Severity.UNRESOLVED, "ON_UPDATE_UNSUPPORTED",
						"Column " + source.getName() + ": " + target.getProductName()
								+ " requires a trigger or application logic for ON UPDATE " + onUpdate + "."));
			}
		}
		return new Result(candidate, diagnostics, defaultValue);
	}

	/**
	 * Copies the column into a holder table without a dialect, so that type names
	 * set by a target rule are not resolved with the source table's dialect.
	 */
	private static Column detached(final Column source) {
		final Column copy = source.clone();
		new com.sqlapp.data.schemas.Table("column_migration").getColumns().add(copy);
		return copy;
	}

	static Result noRule(final Column source, final Dialect target) {
		return new Result(null,
				List.of(new MigrationDiagnostic(Severity.UNRESOLVED, NO_TYPE_RULE,
						"No column migration rule for " + source.getDataType() + " on " + target.getProductName())),
				null);
	}

	private static final Pattern CURRENT_TIMESTAMP =
			Pattern.compile("CURRENT_TIMESTAMP(?:\\s*\\(\\s*([0-6])?\\s*\\))?", Pattern.CASE_INSENSITIVE);

	static Result mapNativeTimestamp(final Column source, final Dialect target, final int maxPrecision,
			final boolean fixedPrecision, final String currentTimeDefault) {
		final long precision = source.getLength() == null ? 0 : source.getLength();
		if (precision < 0 || precision > maxPrecision) {
			return new Result(null, List.of("Unsupported TIMESTAMP precision on "
					+ target.getProductName() + ": " + precision));
		}
		final var type = target.getDbDataTypes().getDbType(DataType.TIMESTAMP_WITH_TIMEZONE);
		if (type == null || type.getDataType() != DataType.TIMESTAMP_WITH_TIMEZONE) {
			return new Result(null, List.of("No native instant type on " + target.getProductName()));
		}
		final var issues = new ArrayList<String>();
		final Column candidate = source.clone().setDataType(DataType.TIMESTAMP_WITH_TIMEZONE)
				.setDataTypeName(type.getTypeName()).setLength(fixedPrecision ? maxPrecision : precision)
				.setDefaultValue(null).setOnUpdate(null);
		if (fixedPrecision && precision != maxPrecision) {
			issues.add(target.getProductName() + " uses fixed fractional precision " + maxPrecision
					+ " instead of " + precision);
		}
		final String defaultValue = source.getDefaultValue();
		if (defaultValue != null && !defaultValue.isBlank()) {
			final var match = CURRENT_TIMESTAMP.matcher(defaultValue.strip());
			if ("NULL".equalsIgnoreCase(defaultValue.strip()) && !source.isNotNull()) {
				candidate.setDefaultValue("NULL");
			} else if (currentTimeDefault != null && match.matches()
					&& (match.group(1) == null || Long.parseLong(match.group(1)) == precision)) {
				candidate.setDefaultValue(currentTimeDefault);
			} else {
				issues.add("Review TIMESTAMP DEFAULT on " + target.getProductName() + ": " + defaultValue);
			}
		}
		if (source.getOnUpdate() != null && !source.getOnUpdate().isBlank()) {
			issues.add(target.getProductName() + " requires manual handling for ON UPDATE: " + source.getOnUpdate());
		}
		return new Result(candidate, issues);
	}

	/**
	 * A proposed definition and the findings of its conversion.
	 *
	 * @param candidate    converted column, or {@code null} when no definition could be produced
	 * @param diagnostics  findings; only {@link Severity#UNRESOLVED} ones block completion
	 * @param defaultValue result of the default conversion, or {@code null} when
	 *                     the default was handled by a type rule
	 */
	public record Result(Column candidate, List<MigrationDiagnostic> diagnostics,
			DefaultValueMigration.Result defaultValue) {
		public Result {
			diagnostics = List.copyOf(diagnostics);
		}

		/**
		 * Creates a result whose unclassified issues all require a decision.
		 *
		 * @param candidate converted column, or {@code null}
		 * @param issues    unresolved issues
		 */
		public Result(final Column candidate, final List<String> issues) {
			this(candidate, issues.stream().map(issue -> new MigrationDiagnostic(Severity.UNRESOLVED,
					MigrationDiagnostic.UNCLASSIFIED, issue)).toList(), null);
		}

		/** Messages of all findings, in order. */
		public List<String> issues() {
			return diagnostics.stream().map(MigrationDiagnostic::message).toList();
		}

		/** Findings that block completion. */
		public List<MigrationDiagnostic> blockingDiagnostics() {
			return diagnostics.stream().filter(MigrationDiagnostic::isBlocking).toList();
		}

		/**
		 * Returns the definition when one exists and no finding blocks completion.
		 * Warnings and informational findings do not block.
		 */
		public Column requireComplete() {
			final var blocking = blockingDiagnostics();
			if (candidate == null || !blocking.isEmpty()) {
				final var messages = (blocking.isEmpty() ? diagnostics : blocking).stream()
						.map(MigrationDiagnostic::message).toList();
				throw new IllegalStateException(
						messages.isEmpty() ? "No converted column definition" : String.join("; ", messages));
			}
			return candidate;
		}

		boolean isNoTypeRule() {
			return candidate == null && !diagnostics.isEmpty()
					&& diagnostics.stream().allMatch(d -> NO_TYPE_RULE.equals(d.code()));
		}
	}
}
