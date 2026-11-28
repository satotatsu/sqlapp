package com.sqlapp.data.db.dialect.defaultvalue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Difference;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.EvaluationPoint;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;

/**
 * Selects a target dialect's current date/time expression for source
 * semantics, independent of where the expression is used.
 * <p>
 * Candidates of the expected kind are ranked by preserving the time basis,
 * then the evaluation point, then fractional precision (first avoiding loss,
 * then avoiding digits the source did not have); the target's listing order
 * breaks ties. The ranking is a deterministic heuristic, not a proof of
 * equivalence: every remaining difference is returned.
 * </p>
 */
public final class TemporalTranslator {

	private TemporalTranslator() {
	}

	/**
	 * Selected candidate and its differences from the source semantics.
	 *
	 * @param candidate   selected target expression
	 * @param differences translation differences, in a stable order
	 */
	public record Selection(TemporalCandidate candidate, List<Difference> differences) {
		public Selection {
			Objects.requireNonNull(candidate, "candidate");
			differences = List.copyOf(differences);
		}

		/** Whether no difference remains. */
		public boolean isExact() {
			return differences.isEmpty();
		}
	}

	/**
	 * Selects the target expression.
	 *
	 * @param source          semantics of the source value
	 * @param expected        kind of value expected where the expression is used,
	 *                        or {@code null} to keep the source kind
	 * @param targetPrecision fractional digits kept where the value is stored, or
	 *                        {@code null} when unknown or unlimited
	 * @param target          target dialect support
	 * @return selection, or empty when the target has no candidate of the kind
	 */
	public static Optional<Selection> select(final TemporalSemantics source, final TemporalKind expected,
			final Integer targetPrecision, final DefaultExpressionSupport target) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");
		return select(source, expected, targetPrecision,
				target.temporalCandidates(expected == null ? source.kind() : expected));
	}

	/**
	 * Selects among the given candidates; candidates of another kind are ignored.
	 *
	 * @see #select(TemporalSemantics, TemporalKind, Integer, DefaultExpressionSupport)
	 */
	public static Optional<Selection> select(final TemporalSemantics source, final TemporalKind expected,
			final Integer targetPrecision, final List<TemporalCandidate> candidates) {
		Objects.requireNonNull(source, "source");
		final TemporalKind kind = expected == null ? source.kind() : expected;
		final List<TemporalCandidate> legal = candidates.stream()
				.filter(candidate -> candidate.semantics().kind() == kind).toList();
		return legal.stream()
				.min(Comparator.comparingInt((TemporalCandidate c) -> mismatch(source.basis(), c.semantics().basis()))
						.thenComparingInt(c -> mismatch(source.point(), c.semantics().point()))
						.thenComparingInt(c -> precisionLoss(source.precision(),
								min(c.semantics().precision(), targetPrecision)))
						.thenComparingInt(c -> precisionLoss(c.semantics().precision(), source.precision()))
						.thenComparingInt(legal::indexOf))
				.map(candidate -> new Selection(candidate, differences(source, candidate.semantics(), kind,
						targetPrecision)));
	}

	/**
	 * Translation differences between source and target semantics. Differences
	 * caused only by storing the value in a column of another kind are not
	 * included.
	 */
	public static List<Difference> differences(final TemporalSemantics source, final TemporalSemantics target,
			final TemporalKind kind, final Integer targetPrecision) {
		final List<Difference> differences = new ArrayList<>();
		if (source.basis() == TimeBasis.UNKNOWN || target.basis() == TimeBasis.UNKNOWN
				|| source.point() == EvaluationPoint.UNKNOWN || target.point() == EvaluationPoint.UNKNOWN) {
			differences.add(Difference.UNKNOWN_SEMANTICS);
		}
		if (source.basis() != target.basis() && source.basis() != TimeBasis.UNKNOWN
				&& target.basis() != TimeBasis.UNKNOWN) {
			differences.add(Difference.TIME_BASIS);
		}
		if (source.point() != target.point() && source.point() != EvaluationPoint.UNKNOWN
				&& target.point() != EvaluationPoint.UNKNOWN) {
			differences.add(Difference.EVALUATION_POINT);
		}
		if (kind.hasTime() && precisionLoss(source.precision(), min(target.precision(), targetPrecision)) > 0) {
			differences.add(Difference.PRECISION);
		}
		return differences;
	}

	static Integer min(final Integer a, final Integer b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return Math.min(a, b);
	}

	static int precisionLoss(final Integer source, final Integer target) {
		if (source == null || target == null) {
			return 0;
		}
		return Math.max(0, source - target);
	}

	private static <E extends Enum<E>> int mismatch(final E source, final E target) {
		return source == target ? 0 : 1;
	}
}
