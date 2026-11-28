package com.sqlapp.data.db.dialect.defaultvalue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.MetaType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.MigrationDiagnostic;
import com.sqlapp.data.db.dialect.MigrationDiagnostic.Severity;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.EvaluationPoint;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.CommonUtils;

/**
 * Converts a column default expression from a source dialect to a target
 * dialect.
 * <p>
 * The same product retains the source expression. Between products, basic
 * literals and recognized current date/time expressions are translated to the
 * target dialect's recommended expression, including approximate translations
 * whose semantic differences are reported. Unrecognized expressions are not
 * guessed: the target default is omitted and the result requires action.
 * </p>
 */
public final class DefaultValueMigration {

	private DefaultValueMigration() {
	}

	/** What happened to a default. */
	public enum Outcome {
		/** The source has no default. */
		NO_DEFAULT,
		/** The source expression is retained. */
		PRESERVED,
		/** Translated without known semantic differences. */
		CONVERTED_EXACT,
		/** Translated with reported semantic differences. */
		CONVERTED_APPROXIMATE,
		/** Replaced by an explicit column override. */
		REPLACED,
		/** Removed by an explicit column override. */
		REMOVED,
		/** Not converted; the target default is omitted until corrected. */
		REQUIRES_ACTION
	}

	/** Semantic difference between the source and target default. */
	public enum Difference {
		/** Different time zone or clock. */
		TIME_BASIS(Severity.WARNING),
		/** Evaluated at a different point (transaction, statement or call). */
		EVALUATION_POINT(Severity.WARNING),
		/** Fewer fractional second digits. */
		PRECISION(Severity.INFO),
		/** Semantics of the source or target are not fully known. */
		UNKNOWN_SEMANTICS(Severity.WARNING),
		/** The target column stores no time of day. */
		COLUMN_TIME_REMOVED(Severity.WARNING),
		/** The target column stores no date. */
		COLUMN_DATE_REMOVED(Severity.WARNING),
		/** The target column stores no time zone offset. */
		COLUMN_TIME_ZONE_REMOVED(Severity.WARNING),
		/** The target column adds a time zone the source value did not have. */
		COLUMN_TIME_ZONE_ADDED(Severity.WARNING);

		private final Severity severity;

		Difference(final Severity severity) {
			this.severity = severity;
		}

		/** Default severity of the difference. */
		public Severity getSeverity() {
			return severity;
		}

		/** Whether the difference comes from translating the expression rather than from the column type. */
		public boolean isTranslation() {
			return this == TIME_BASIS || this == EVALUATION_POINT || this == PRECISION || this == UNKNOWN_SEMANTICS;
		}
	}

	/** How far the result has been checked. */
	public enum Verification {
		/** Not checked; for example retained expressions whose dependencies are unknown. */
		UNCHECKED,
		/** Recognized and checked against the target dialect's rules, without a database. */
		STATICALLY_CHECKED
	}

	/** Conversion options. */
	public record Options(boolean strict) {
		/** Ordinary migration: approximate translations proceed with warnings. */
		public static final Options DEFAULT = new Options(false);
		/** Approximate translations are reported as unresolved. */
		public static final Options STRICT = new Options(true);
	}

	/** An explicit per-column decision that takes precedence over conversion. */
	public sealed interface Override {
		/** Use the given target SQL as the default. */
		record Replace(String sql) implements Override {
			public Replace {
				if (CommonUtils.isEmpty(sql) || sql.isBlank()) {
					throw new IllegalArgumentException("Replacement default SQL must not be blank. Use Remove to omit the default.");
				}
			}
		}

		/** Omit the default in the target. */
		record Remove() implements Override {
		}
	}

	/**
	 * Result of converting one default.
	 *
	 * @param outcome          what happened
	 * @param sourceExpression source default, or {@code null}
	 * @param targetExpression target default to use, or {@code null} to omit it
	 * @param rule             identifier of the rule that produced the result
	 * @param differences      semantic differences between source and target
	 * @param verification     how far the result has been checked
	 * @param diagnostics      findings for the report
	 */
	public record Result(Outcome outcome, String sourceExpression, String targetExpression, String rule,
			List<Difference> differences, Verification verification, List<MigrationDiagnostic> diagnostics) {
		public Result {
			Objects.requireNonNull(outcome, "outcome");
			Objects.requireNonNull(rule, "rule");
			Objects.requireNonNull(verification, "verification");
			differences = List.copyOf(differences);
			diagnostics = List.copyOf(diagnostics);
		}

		/** Whether a finding blocks completion. */
		public boolean isBlocking() {
			return diagnostics.stream().anyMatch(MigrationDiagnostic::isBlocking);
		}
	}

	/**
	 * Converts the default of a source column for a target column.
	 *
	 * @param source        source column with its default as read by the source dialect
	 * @param sourceDialect source dialect, or {@code null} when it cannot be resolved
	 * @param target        target column after type conversion; it is not modified
	 * @param targetDialect target dialect
	 * @param options       conversion options
	 * @param override      explicit decision for this column, or {@code null}
	 * @return conversion result
	 */
	public static Result convert(final Column source, final Dialect sourceDialect, final Column target,
			final Dialect targetDialect, final Options options, final Override override) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(targetDialect, "targetDialect");
		final Options effective = options == null ? Options.DEFAULT : options;
		final String sql = source.getDefaultValue();
		final String name = source.getName();
		if (override instanceof Override.Replace replace) {
			return new Result(Outcome.REPLACED, sql, replace.sql(), "column-override", List.of(), Verification.UNCHECKED,
					List.of(new MigrationDiagnostic(Severity.INFO, "DEFAULT_REPLACED",
							"Column " + name + ": DEFAULT replaced by an explicit override.")));
		}
		if (override instanceof Override.Remove) {
			return new Result(Outcome.REMOVED, sql, null, "column-override", List.of(), Verification.UNCHECKED,
					List.of(new MigrationDiagnostic(Severity.INFO, "DEFAULT_REMOVED",
							"Column " + name + ": DEFAULT removed by an explicit override"
									+ (CommonUtils.isEmpty(sql) ? "." : "; source DEFAULT was " + sql + "."))));
		}
		if (CommonUtils.isEmpty(sql) || sql.isBlank()) {
			return new Result(Outcome.NO_DEFAULT, sql, null, "none", List.of(), Verification.STATICALLY_CHECKED,
					List.of());
		}
		if (sourceDialect == null) {
			return requiresAction(sql, "source-dialect", "SOURCE_DIALECT_UNKNOWN", "Column " + name
					+ ": source dialect is unknown; DEFAULT " + sql + " is not converted. Specify the source dialect.");
		}
		final DefaultExpressionSupport sourceSupport = sourceDialect.getDefaultExpressionSupport();
		final DefaultExpressionSupport targetSupport = targetDialect.getDefaultExpressionSupport();
		final DefaultExpression parsed = sourceSupport.parse(sql, source);
		if (targetSupport.canPreserve(sourceDialect, sql, parsed)) {
			if (parsed instanceof DefaultExpression.Unrecognized) {
				return new Result(Outcome.PRESERVED, sql, sql, "same-product", List.of(), Verification.UNCHECKED,
						List.of(new MigrationDiagnostic(Severity.INFO, "DEFAULT_PRESERVED_UNCHECKED", "Column " + name
								+ ": DEFAULT " + sql
								+ " is retained; referenced objects and target capabilities are not verified.")));
			}
			return new Result(Outcome.PRESERVED, sql, sql, "same-product", List.of(), Verification.STATICALLY_CHECKED,
					List.of());
		}
		if (sourceSupport.isGeneratorDefault(sql, source)) {
			return requiresAction(sql, "generator", "GENERATOR_DEFAULT", "Column " + name + ": DEFAULT " + sql
					+ " belongs to the column's identity/sequence generator; generator conversion is handled separately.");
		}
		return switch (parsed) {
		case DefaultExpression.NullValue nullValue -> exact(sql, "NULL", "literal-null");
		case DefaultExpression.StringLiteral string -> string(sql, string, source, target, targetSupport);
		case DefaultExpression.NumericLiteral number -> numeric(sql, number, source, target, targetSupport);
		case DefaultExpression.BooleanLiteral bool -> bool(sql, bool.value(), source, target, targetSupport);
		case DefaultExpression.CurrentTime time ->
			temporal(sql, time.semantics(), source, target, targetSupport, targetDialect, effective);
		case DefaultExpression.Unrecognized unrecognized -> requiresAction(sql, "unrecognized", "DEFAULT_UNRECOGNIZED",
				"Column " + name + ": DEFAULT " + sql + " is not recognized for " + targetDialect.getProductName()
						+ "; it is omitted until replaced or explicitly removed.");
		};
	}

	private static Result string(final String sql, final DefaultExpression.StringLiteral string, final Column source,
			final Column target, final DefaultExpressionSupport support) {
		final String literal = support.stringLiteral(string.value(), target);
		if (isCharacter(target) || target.getDataType() == null) {
			return exact(sql, literal, "literal-string");
		}
		return new Result(Outcome.CONVERTED_EXACT, sql, literal, "literal-string", List.of(),
				Verification.STATICALLY_CHECKED,
				List.of(new MigrationDiagnostic(Severity.INFO, "DEFAULT_LITERAL_TARGET_PARSED", "Column "
						+ source.getName() + ": string DEFAULT " + sql + " is interpreted by the target type "
						+ target.getDataType() + "; check its format on the target.")));
	}

	private static Result bool(final String sql, final boolean value, final Column source, final Column target,
			final DefaultExpressionSupport support) {
		if (target.getDataType() == DataType.BOOLEAN) {
			return exact(sql, support.booleanLiteral(value, target), "literal-boolean");
		}
		return requiresAction(sql, "literal-boolean", "DEFAULT_TARGET_TYPE", "Column " + source.getName()
				+ ": boolean DEFAULT " + sql + " has no translation for target type " + target.getDataType() + ".");
	}

	private static Result numeric(final String sql, final DefaultExpression.NumericLiteral number, final Column source,
			final Column target, final DefaultExpressionSupport support) {
		if (target.getDataType() == DataType.BOOLEAN) {
			if (source.getDataType() == DataType.BOOLEAN && ("0".equals(number.text()) || "1".equals(number.text()))) {
				return exact(sql, support.booleanLiteral("1".equals(number.text()), target), "literal-boolean");
			}
			return requiresAction(sql, "literal-boolean", "DEFAULT_UNRECOGNIZED", "Column " + source.getName()
					+ ": numeric DEFAULT " + sql + " cannot be interpreted as a boolean value.");
		}
		return exact(sql, number.text(), "literal-number");
	}

	private static Result temporal(final String sql, final TemporalSemantics function, final Column source,
			final Column target, final DefaultExpressionSupport support, final Dialect targetDialect,
			final Options options) {
		final String name = source.getName();
		final TemporalKind targetKind = TemporalKind.of(target.getDataType());
		if (targetKind == null) {
			return requiresAction(sql, "temporal", "DEFAULT_TARGET_TYPE", "Column " + name + ": current date/time DEFAULT "
					+ sql + " has no translation for target type " + target.getDataType() + ".");
		}
		final TemporalSemantics stored = storedSemantics(function, source);
		final List<TemporalCandidate> candidates = support.temporalCandidates(target).stream()
				.filter(candidate -> candidate.semantics().kind() == targetKind).toList();
		if (candidates.isEmpty()) {
			return requiresAction(sql, "temporal", "DEFAULT_NO_CANDIDATE", "Column " + name + ": "
					+ targetDialect.getProductName() + " has no current date/time DEFAULT for " + targetKind + ".");
		}
		final Integer targetPrecision = precisionOf(target, targetKind);
		final TemporalCandidate selected = candidates.stream()
				.min(Comparator.comparingInt((TemporalCandidate c) -> mismatch(stored.basis(), c.semantics().basis()))
						.thenComparingInt(c -> mismatch(stored.point(), c.semantics().point()))
						.thenComparingInt(c -> precisionLoss(stored.precision(),
								min(c.semantics().precision(), targetPrecision)))
						.thenComparingInt(candidates::indexOf))
				.orElseThrow();
		final List<Difference> differences = new ArrayList<>();
		final List<MigrationDiagnostic> diagnostics = new ArrayList<>();
		final TemporalSemantics chosen = selected.semantics();
		if (stored.basis() == TimeBasis.UNKNOWN || chosen.basis() == TimeBasis.UNKNOWN
				|| stored.point() == EvaluationPoint.UNKNOWN || chosen.point() == EvaluationPoint.UNKNOWN) {
			add(differences, diagnostics, Difference.UNKNOWN_SEMANTICS, options,
					"Column " + name + ": semantics of " + sql + " or " + selected.sql() + " are not fully known.");
		}
		if (stored.basis() != chosen.basis() && stored.basis() != TimeBasis.UNKNOWN
				&& chosen.basis() != TimeBasis.UNKNOWN) {
			add(differences, diagnostics, Difference.TIME_BASIS, options, "Column " + name + ": " + sql + " uses "
					+ describe(stored.basis()) + " but " + selected.sql() + " uses " + describe(chosen.basis()) + ".");
		}
		if (stored.point() != chosen.point() && stored.point() != EvaluationPoint.UNKNOWN
				&& chosen.point() != EvaluationPoint.UNKNOWN) {
			add(differences, diagnostics, Difference.EVALUATION_POINT, options, "Column " + name + ": " + sql
					+ " is evaluated at " + describe(stored.point()) + " but " + selected.sql() + " at "
					+ describe(chosen.point()) + ".");
		}
		final Integer targetDigits = min(chosen.precision(), targetPrecision);
		if (targetKind.hasTime() && precisionLoss(stored.precision(), targetDigits) > 0) {
			add(differences, diagnostics, Difference.PRECISION, options, "Column " + name + ": fractional seconds "
					+ stored.precision() + " of " + sql + " become " + targetDigits + " in the target.");
		}
		coercion(stored.kind(), targetKind, name, differences, diagnostics, options);
		final boolean approximate = differences.stream().anyMatch(Difference::isTranslation);
		final Outcome outcome = approximate ? Outcome.CONVERTED_APPROXIMATE : Outcome.CONVERTED_EXACT;
		return new Result(outcome, sql, selected.sql(), "temporal", differences, Verification.STATICALLY_CHECKED,
				diagnostics);
	}

	/** Semantics of the value the source column stores, after coercion to the source column type. */
	private static TemporalSemantics storedSemantics(final TemporalSemantics function, final Column source) {
		final TemporalKind sourceKind = TemporalKind.of(source.getDataType());
		TemporalSemantics stored = function;
		if (sourceKind != null && sourceKind != function.kind()) {
			stored = stored.withKind(sourceKind);
		}
		final Integer columnPrecision = precisionOf(source, stored.kind());
		if (stored.kind().hasTime()) {
			stored = stored.withPrecision(min(stored.precision(), columnPrecision));
		}
		return stored;
	}

	private static void coercion(final TemporalKind source, final TemporalKind target, final String name,
			final List<Difference> differences, final List<MigrationDiagnostic> diagnostics, final Options options) {
		if (source == target) {
			return;
		}
		if (source.hasTime() && !target.hasTime()) {
			add(differences, diagnostics, Difference.COLUMN_TIME_REMOVED, options,
					"Column " + name + ": the target column type stores no time of day; the DEFAULT keeps only the date.");
		}
		if (source.hasDate() && !target.hasDate()) {
			add(differences, diagnostics, Difference.COLUMN_DATE_REMOVED, options,
					"Column " + name + ": the target column type stores no date; the DEFAULT keeps only the time.");
		}
		if (source.hasTimeZone() && !target.hasTimeZone()) {
			add(differences, diagnostics, Difference.COLUMN_TIME_ZONE_REMOVED, options,
					"Column " + name + ": the target column type stores no time zone offset.");
		} else if (!source.hasTimeZone() && target.hasTimeZone()) {
			add(differences, diagnostics, Difference.COLUMN_TIME_ZONE_ADDED, options, "Column " + name
					+ ": the target column type stores a time zone the source value did not have.");
		}
	}

	private static void add(final List<Difference> differences, final List<MigrationDiagnostic> diagnostics,
			final Difference difference, final Options options, final String message) {
		differences.add(difference);
		final Severity severity = options.strict() ? Severity.UNRESOLVED : difference.getSeverity();
		diagnostics.add(new MigrationDiagnostic(severity, "DEFAULT_" + difference.name(), message));
	}

	private static Integer precisionOf(final Column column, final TemporalKind kind) {
		if (kind == null || !kind.hasTime()) {
			return null;
		}
		final Long length = column.getLength();
		if (length == null || length < 0 || length > 12) {
			return null;
		}
		return length.intValue();
	}

	private static Integer min(final Integer a, final Integer b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return Math.min(a, b);
	}

	private static int precisionLoss(final Integer source, final Integer target) {
		if (source == null || target == null) {
			return 0;
		}
		return Math.max(0, source - target);
	}

	private static <E extends Enum<E>> int mismatch(final E source, final E target) {
		return source == target ? 0 : 1;
	}

	private static String describe(final TimeBasis basis) {
		return switch (basis) {
		case SESSION_TIME_ZONE -> "the session time zone";
		case SERVER_CLOCK -> "the database server clock";
		case UTC -> "UTC";
		case INSTANT -> "an absolute instant";
		case UNKNOWN -> "an unknown time basis";
		};
	}

	private static String describe(final EvaluationPoint point) {
		return switch (point) {
		case TRANSACTION_START -> "transaction start";
		case STATEMENT_START -> "statement start";
		case CALL -> "each call";
		case UNKNOWN -> "an unknown point";
		};
	}

	private static Result exact(final String sql, final String target, final String rule) {
		return new Result(Outcome.CONVERTED_EXACT, sql, target, rule, List.of(), Verification.STATICALLY_CHECKED,
				List.of());
	}

	private static Result requiresAction(final String sql, final String rule, final String code, final String message) {
		return new Result(Outcome.REQUIRES_ACTION, sql, null, rule, List.of(), Verification.UNCHECKED,
				List.of(new MigrationDiagnostic(Severity.UNRESOLVED, code, message)));
	}

	/** Whether the column type is a character string type. */
	static boolean isCharacter(final Column column) {
		final DataType type = column.getDataType();
		return type != null && type != DataType.BIT && type.getMetaTtype() == MetaType.CHARACTER;
	}
}
