package com.sqlapp.data.db.dialect.defaultvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Difference;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.EvaluationPoint;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;

class TemporalTranslatorTest {

	private static TemporalSemantics sem(final EvaluationPoint point, final TimeBasis basis, final TemporalKind kind,
			final Integer precision) {
		return new TemporalSemantics(point, basis, kind, precision);
	}

	private static final List<TemporalCandidate> CANDIDATES = List.of(
			new TemporalCandidate("TX", sem(EvaluationPoint.TRANSACTION_START, TimeBasis.INSTANT,
					TemporalKind.DATETIME_WITH_TIME_ZONE, 6)),
			new TemporalCandidate("ST", sem(EvaluationPoint.STATEMENT_START, TimeBasis.INSTANT,
					TemporalKind.DATETIME_WITH_TIME_ZONE, 6)),
			new TemporalCandidate("LOCAL", sem(EvaluationPoint.TRANSACTION_START, TimeBasis.SESSION_TIME_ZONE,
					TemporalKind.DATETIME, 6)),
			new TemporalCandidate("TODAY", sem(EvaluationPoint.TRANSACTION_START, TimeBasis.SESSION_TIME_ZONE,
					TemporalKind.DATE, 0)));

	@Test
	void keepsTheSourceKindWhenNoKindIsExpected() {
		final var source = sem(EvaluationPoint.STATEMENT_START, TimeBasis.INSTANT, TemporalKind.DATETIME_WITH_TIME_ZONE, 7);
		final var selection = TemporalTranslator.select(source, null, null, CANDIDATES).orElseThrow();
		assertEquals("ST", selection.candidate().sql());
		assertEquals(List.of(Difference.PRECISION), selection.differences());
	}

	@Test
	void usesTheExpectedKind() {
		final var source = sem(EvaluationPoint.STATEMENT_START, TimeBasis.INSTANT, TemporalKind.DATETIME_WITH_TIME_ZONE, 6);
		final var selection = TemporalTranslator.select(source, TemporalKind.DATE, null, CANDIDATES).orElseThrow();
		assertEquals("TODAY", selection.candidate().sql());
		assertEquals(List.of(Difference.TIME_BASIS, Difference.EVALUATION_POINT), selection.differences());
		assertTrue(TemporalTranslator.select(source, TemporalKind.TIME, null, CANDIDATES).isEmpty());
	}

	@Test
	void prefersTheEarlierCandidateOnTiesAndReportsUnknownSemantics() {
		final var unknown = sem(EvaluationPoint.UNKNOWN, TimeBasis.UNKNOWN, TemporalKind.DATETIME_WITH_TIME_ZONE, null);
		final var selection = TemporalTranslator.select(unknown, null, null, CANDIDATES).orElseThrow();
		assertEquals("TX", selection.candidate().sql());
		assertEquals(List.of(Difference.UNKNOWN_SEMANTICS), selection.differences());
		final var exact = TemporalTranslator.select(
				sem(EvaluationPoint.TRANSACTION_START, TimeBasis.INSTANT, TemporalKind.DATETIME_WITH_TIME_ZONE, 6),
				null, 3, CANDIDATES).orElseThrow();
		assertEquals(List.of(Difference.PRECISION), exact.differences());
	}
}
