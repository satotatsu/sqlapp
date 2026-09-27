/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile;
import com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.*;

class DatabaseMigrationAssessmentHtmlTest {
	@Test
	void rendersEscapedStandaloneReviewWithProfileAndIntegrityDetails() {
		final var table = new ObjectId(null, "s&", "table", "T<script>");
		final var column = new ObjectId(null, "s&", "column", "C<script>", "T<script>");
		final var profile = new MigrationDataProfile(List.of(new TableProfile(table, 2, List.of(
				new ColumnProfile(column, "NUMERIC", Coverage.SCANNED, 0L, null,
						new NumericStatistics(new BigDecimal("1.2"), new BigDecimal("3.4"), 1, 1, 0), null)))),
				List.of(new IntegrityCheck(new ObjectId(null, "s&", "key", "PK<script>", "T<script>"),
						IntegrityKind.PRIMARY_KEY, IntegrityCoverage.CHECKED, List.of("C<script>"), null, List.of(),
						2L, 0L, 1L, "unsafe <reason>")));
		final var assessment = new MigrationAssessment(List.of(
				new Finding("rule<script>", Severity.BLOCKER, Evidence.DATABASE, column,
						"bad <value>", "fix & verify", null),
				new Finding("review", Severity.REVIEW, Evidence.MANUAL_CHECK, null, "review", "act", null)),
				List.of(new Inventory(null, "s&", "tables", 1)));
		final var report = new AssessDatabaseMigrationCommand.Report(2, "abc", "Access <source>", "Oracle", "19c",
				Method.LOGICAL_MIGRATION, true, true, "BLOCKED", assessment, profile);
		final String html = DatabaseMigrationAssessmentHtml.render(report);
		assertTrue(html.startsWith("<!doctype html>"));
		assertTrue(html.contains("1 blockers"));
		assertTrue(html.contains("Integrity checks"));
		assertTrue(html.contains("unsafe &lt;reason&gt;"));
		assertTrue(html.contains("min=1.2, max=3.4"));
		assertFalse(html.contains("<script>"));
		assertFalse(html.contains("bad <value>"));
	}
}
