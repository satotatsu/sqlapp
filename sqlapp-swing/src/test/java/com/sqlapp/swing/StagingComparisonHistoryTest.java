/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity;
import com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement.*;
import com.sqlapp.data.db.command.migration.transfer.TransferStagingComparisonReportIO.Report;

class StagingComparisonHistoryTest {
	private final StagingComparisonHistory history = new StagingComparisonHistory();
	private static final String HASH = "sha256:" + "a".repeat(64);

	@Test
	void comparesResolvedIdentitiesDespiteReorderingAndShowsOnlyChangedCountsAndStatuses() {
		var first = report(HASH, "one", List.of(table("a", 2, true), table("b", 2, true)));
		assertEquals(0, show(first).tables().size());
		var next = report(HASH, "one", List.of(table("b", 1, false), table("a", 2, true)));
		var review = show(next);
		var section = review.tables().getLast();
		assertEquals("Changes since previous comparison", section.title());
		assertEquals(1, section.rows().size());
		assertEquals("b", section.rows().getFirst().getFirst());
		assertEquals(List.of("2", "2", "2", "1", "Match", "Different", "Unchanged", "Changed"),
				section.rows().getFirst().subList(4, 12));
		assertEquals(List.of(4, 5, 6, 7), section.numericColumns());
		assertTrue(section.summary().contains("unchanged values do not prove unchanged data"));
		assertEquals(new ReportReviewService().stagingComparison(next).details(), review.details());
	}

	@ParameterizedTest
	@CsvSource({
		"false,true,true,true,Match status improved",
		"false,false,false,true,Match status improved",
		"false,false,true,false,Match status improved",
		"true,true,false,true,New mismatch",
		"true,true,true,false,New mismatch",
		"false,true,false,false,New mismatch",
		"false,true,true,false,Mixed status changes",
		"true,false,false,true,Mixed status changes"
	})
	void classifiesBothMatchStatusesWithoutTreatingMixedChangesAsSuccess(boolean oldTarget, boolean oldStaging,
			boolean newTarget, boolean newStaging, String expected) {
		var target = new Target("a", "CAT", "PUBLIC", "ITEMS", "STAGE_a");
		show(report(HASH, "one", List.of(new TableComparison(target, 2, oldTarget ? 2 : 1, oldTarget, oldStaging))));
		var current = report(HASH, "one", List.of(new TableComparison(target, 2, newTarget ? 2 : 1, newTarget, newStaging)));
		var view = show(current);
		var section = view.tables().getLast();
		assertEquals("Change", section.columns().get(12));
		assertEquals(expected, section.rows().getFirst().get(12));
		assertEquals(new ReportReviewService().stagingComparison(current).details(), view.details());
	}

	@Test
	void unchangedCountsAndStatusesAreExplicitlyReportedWithoutClaimingUnchangedData() {
		var report = report(HASH, "one", List.of(table("a", 2, true)));
		show(report);
		var section = show(report).tables().getLast();
		assertTrue(section.rows().isEmpty());
		assertTrue(section.summary().startsWith("0 table(s)"));
		assertTrue(section.summary().contains("commit remains unconfirmed"));
	}

	@Test
	void differentOriginalResultTargetOrObjectSetDoesNotReusePreviousEvidence() {
		var baseline = report(HASH, "one", List.of(table("a", 2, true)));
		show(baseline);
		assertNoHistory(show(report("sha256:" + "b".repeat(64), "one", List.of(table("a", 2, true)))));
		history.clear();
		show(baseline);
		assertNoHistory(show(report(HASH, "two", List.of(table("a", 2, true)))));
		history.clear();
		show(baseline);
		assertNoHistory(show(report(HASH, "one", List.of(table("b", 2, true)))));
		history.clear();
		assertNoHistory(show(report(HASH, "two", List.of(table("b", 2, true)))));
	}

	@Test
	void stagingRowCountChangeAppearsEvenWhenBothMatchFlagsAndTargetCountStayTheSame() {
		var before = table("a", 1, false);
		show(report(HASH, "one", List.of(before)));
		var after = new TableComparison(before.target(), 3, 1, false, false);
		var section = show(report(HASH, "one", List.of(after))).tables().getLast();
		assertEquals(List.of("2", "3", "1", "1"), section.rows().getFirst().subList(4, 8));
		assertEquals("Row counts changed", section.rows().getFirst().get(12));
	}

	private void assertNoHistory(ReportReviewService.Review review) {
		assertTrue(review.tables().stream().noneMatch(table -> table.title().equals("Changes since previous comparison")));
	}
	private ReportReviewService.Review show(Report report) {
		return history.show(report, new ReportReviewService().stagingComparison(report));
	}
	private TableComparison table(String id, long targetRows, boolean recordedMatch) {
		return new TableComparison(new Target(id, null, "PUBLIC", "ITEMS", "STAGE_" + id),
				2, targetRows, targetRows == 2, recordedMatch);
	}
	private Report report(String fingerprint, String target, List<TableComparison> tables) {
		return new Report(1, fingerprint, State.COMMIT_UNCERTAIN,
				new DatabaseIdentity("HSQL", "2.7", "sha256:" + (target.equals("one") ? "1" : "2").repeat(64)),
				new Comparison(tables));
	}
}
