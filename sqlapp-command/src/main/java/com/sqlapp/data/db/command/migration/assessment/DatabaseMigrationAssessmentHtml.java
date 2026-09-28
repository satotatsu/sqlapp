/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.util.Comparator;

import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Finding;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.ObjectId;

/** Standalone escaped review view of the canonical JSON report. */
final class DatabaseMigrationAssessmentHtml {
	private DatabaseMigrationAssessmentHtml() { }

	static String render(final AssessDatabaseMigrationCommand.Report report) {
		final var html = new StringBuilder(16384);
		html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
				.append("<title>Database migration assessment</title><style>")
				.append("body{font-family:system-ui,sans-serif;margin:0;background:#f4f6f8;color:#17212b}main{max-width:1200px;margin:auto;padding:24px}")
				.append("h1,h2{line-height:1.2}.summary,.card{background:white;border:1px solid #dce2e8;border-radius:8px;padding:16px;margin:16px 0}")
				.append(".facts{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}.label{color:#56616d;font-size:.82rem}.value{font-weight:650;overflow-wrap:anywhere}")
				.append("table{border-collapse:collapse;width:100%;background:white}th,td{border:1px solid #dce2e8;padding:8px;text-align:left;vertical-align:top}th{background:#edf1f5}")
				.append(".BLOCKER{color:#a31414;font-weight:700}.WARNING{color:#8a5700;font-weight:700}.REVIEW{color:#315b82;font-weight:700}.muted{color:#66717d}.scroll{overflow:auto}")
				.append("</style></head><body><main><h1>Database migration assessment</h1>");
		html.append("<section class=\"summary\"><div class=\"facts\">");
		fact(html, "Status", report.status()); fact(html, "Source", report.sourceProduct());
		fact(html, "Target", report.targetProduct() + " " + report.targetVersion());
		fact(html, "Data scanned", report.dataScanned()); fact(html, "Relationships collected", report.relationshipsCollected());
		fact(html, "Report format", report.formatVersion()); fact(html, "Source SHA-256", report.sourceFingerprint());
		html.append("</div></section>");

		final long blockers = report.assessment().findings().stream().filter(f -> f.severity().name().equals("BLOCKER")).count();
		final long warnings = report.assessment().findings().stream().filter(f -> f.severity().name().equals("WARNING")).count();
		final long reviews = report.assessment().findings().stream().filter(f -> f.severity().name().equals("REVIEW")).count();
		html.append("<section><h2>Findings</h2><p>").append(blockers).append(" blockers, ").append(warnings)
				.append(" warnings, ").append(reviews).append(" review items</p><div class=\"scroll\"><table><thead><tr><th>Severity</th><th>Rule</th><th>Object</th><th>Reason</th><th>Action</th></tr></thead><tbody>");
		report.assessment().findings().stream().sorted(Comparator.comparingInt(DatabaseMigrationAssessmentHtml::rank)
				.thenComparing(Finding::ruleId)).forEach(f -> html.append("<tr><td class=\"").append(e(f.severity())).append("\">").append(e(f.severity()))
				.append("</td><td>").append(e(f.ruleId())).append("</td><td>").append(e(object(f.object())))
				.append("</td><td>").append(e(f.reason())).append("</td><td>").append(e(f.action())).append("</td></tr>"));
		html.append("</tbody></table></div></section>");

		html.append("<section><h2>Inventory</h2><div class=\"scroll\"><table><thead><tr><th>Catalog</th><th>Schema</th><th>Type</th><th>Count</th></tr></thead><tbody>");
		report.assessment().inventory().forEach(i -> html.append("<tr><td>").append(e(i.catalog())).append("</td><td>").append(e(i.schema()))
				.append("</td><td>").append(e(i.type())).append("</td><td>").append(i.count()).append("</td></tr>"));
		html.append("</tbody></table></div></section>");

		if (report.dataProfile() != null) {
			html.append("<section><h2>Data profile</h2>");
			for (final var table : report.dataProfile().tables()) {
				html.append("<article class=\"card\"><h3>").append(e(object(table.table()))).append("</h3><p>").append(table.rowCount())
						.append(" rows</p><div class=\"scroll\"><table><thead><tr><th>Column</th><th>Source type</th><th>Coverage</th><th>NULLs</th><th>Observed statistics</th></tr></thead><tbody>");
				table.columns().forEach(c -> html.append("<tr><td>").append(e(c.column().name())).append("</td><td>").append(e(c.sourceType()))
						.append("</td><td>").append(e(c.coverage())).append("</td><td>").append(e(c.nullCount())).append("</td><td>")
						.append(e(statistics(c))).append("</td></tr>"));
				html.append("</tbody></table></div></article>");
			}
			html.append("<h2>Integrity checks</h2><div class=\"scroll\"><table><thead><tr><th>Kind</th><th>Key</th><th>Columns</th><th>Coverage</th><th>Checked</th><th>NULL</th><th>Violations</th><th>Notes</th></tr></thead><tbody>");
			report.dataProfile().integrityChecks().forEach(c -> html.append("<tr><td>").append(e(c.kind())).append("</td><td>").append(e(object(c.object())))
					.append("</td><td>").append(e(String.join(", ", c.columns()))).append("</td><td>").append(e(c.coverage()))
					.append("</td><td>").append(e(c.checkedRows())).append("</td><td>").append(e(c.nullRows())).append("</td><td>")
					.append(e(c.violationRows())).append("</td><td>").append(e(c.reason())).append("</td></tr>"));
			html.append("</tbody></table></div></section>");
		}
		if (report.targetMapping() != null) {
			html.append("<section><h2>Resolved target mapping</h2><p>Fingerprint: ").append(e(report.mappingFingerprint())).append("</p>");
			for (final var table : report.targetMapping().tables()) {
				html.append("<article class=\"card\"><h3>").append(e(object(table.sourceTable()))).append(" → ")
						.append(e((table.targetSchema() == null || table.targetSchema().isBlank() ? "" : table.targetSchema() + ".") + table.targetTable()))
						.append("</h3><div class=\"scroll\"><table><thead><tr><th>Source column</th><th>Target column</th><th>Target type</th><th>Nullable</th><th>Identity</th><th>Default</th><th>Conversion</th></tr></thead><tbody>");
				table.columns().forEach(c -> html.append("<tr><td>").append(e(object(c.sourceColumn()))).append("</td><td>").append(e(c.targetColumn()))
						.append("</td><td>").append(e(c.targetType())).append("</td><td>").append(e(c.nullable())).append("</td><td>")
						.append(e(c.identity())).append("</td><td>").append(e(c.defaultExpression())).append("</td><td>")
						.append(e(c.conversion())).append("</td></tr>"));
				html.append("</tbody></table></div></article>");
			}
			html.append("</section>");
		}
		return html.append("<footer><p class=\"muted\">Review aid generated from the assessment report. The JSON report remains the machine-readable evidence.</p></footer></main></body></html>").toString();
	}

	private static void fact(final StringBuilder html, final String label, final Object value) {
		html.append("<div><div class=\"label\">").append(e(label)).append("</div><div class=\"value\">").append(e(value)).append("</div></div>");
	}
	private static int rank(final Finding finding) { return switch (finding.severity()) { case BLOCKER -> 0; case WARNING -> 1; case REVIEW -> 2; }; }
	private static String object(final ObjectId id) {
		if (id == null) { return ""; }
		final var value = new StringBuilder();
		if (id.catalog() != null && !id.catalog().isBlank()) { value.append(id.catalog()).append('.'); }
		if (id.schema() != null && !id.schema().isBlank()) { value.append(id.schema()).append('.'); }
		if (id.table() != null && !id.table().isBlank()) { value.append(id.table()).append('.'); }
		return value.append(id.name()).append(" (").append(id.type()).append(')').toString();
	}
	private static String statistics(final com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.ColumnProfile c) {
		if (c.text() != null) { return "empty=" + c.text().emptyCount() + ", max code points=" + c.text().maximumCodePoints() + ", UTF-16=" + c.text().maximumUtf16Units() + ", UTF-8=" + c.text().maximumUtf8Bytes(); }
		if (c.numeric() != null) { return "min=" + c.numeric().minimum() + ", max=" + c.numeric().maximum() + ", integer digits=" + c.numeric().maximumIntegerDigits() + ", scale=" + c.numeric().maximumScale() + ", non-finite=" + c.numeric().nonFiniteCount(); }
		if (c.dateTime() != null) { return "min=" + c.dateTime().minimum() + ", max=" + c.dateTime().maximum() + ", fractional digits=" + c.dateTime().maximumFractionalDigits(); }
		return "";
	}
	private static String e(final Object value) {
		if (value == null) { return ""; }
		return value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}
}
