/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Evidence;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Finding;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Severity;
import com.sqlapp.data.schemas.Schema;

class MigrationAssessmentTest {
	@Test
	void freezesEvidenceAndDistinguishesBlockersFromPendingReviews() {
		final var findings = new ArrayList<Finding>();
		findings.add(new Finding("blocked", Severity.BLOCKER, Evidence.DOCUMENTED_RULE,
				null, "Unsupported method", "Select another method", null));
		final var assessment = new MigrationAssessment(findings, List.of());
		findings.clear();
		assertTrue(assessment.hasBlockers());
		assertFalse(assessment.requiresReview());
		assertThrows(UnsupportedOperationException.class, () -> assessment.findings().clear());
		final var review = new MigrationAssessment(List.of(new Finding("review", Severity.REVIEW,
				Evidence.MANUAL_CHECK, null, "Unverified environment", "Run a rehearsal", null)), List.of());
		assertFalse(review.hasBlockers());
		assertTrue(review.requiresReview());
	}

	@Test
	void missingProviderFailsInsteadOfReturningAnEmptyAssessment() {
		assertThrows(IllegalArgumentException.class,
				() -> MigrationAssessmentProvider.resolve("unsupported-product", "unknown-version"));
	}

	@Test
	void legacyProvidersRemainUsableButCannotSilentlyIgnoreCharsetOptions() {
		final var expected = new MigrationAssessment(List.of(), List.of());
		final MigrationAssessmentProvider provider = new MigrationAssessmentProvider() {
			@Override
			public boolean supports(final String product, final String target) { return true; }
			@Override
			public MigrationAssessment assess(final List<Schema> schemas, final String target,
					final MigrationAssessment.Method method) { return expected; }
		};
		assertSame(expected, provider.assess(List.of(), "target", MigrationAssessment.Method.LOGICAL_MIGRATION, null));
		assertThrows(IllegalArgumentException.class,
				() -> provider.assess(List.of(), "target", MigrationAssessment.Method.LOGICAL_MIGRATION, "AL32UTF8"));
	}
}
