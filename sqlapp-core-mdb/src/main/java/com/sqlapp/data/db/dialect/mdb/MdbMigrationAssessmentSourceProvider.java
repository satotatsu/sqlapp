/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSource;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSourceProvider;

/** Access inventory independent of the chosen migration target. */
public final class MdbMigrationAssessmentSourceProvider implements MigrationAssessmentSourceProvider {
	@Override
	public boolean supports(final Path file) {
		final String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".mdb") || name.endsWith(".accdb");
	}

	@Override
	public MigrationAssessmentSource load(final Path file) throws IOException {
		if (!supports(file)) { throw new IllegalArgumentException("Access assessment requires an MDB/ACCDB file"); }
		final var snapshot = MdbFileLoader.loadForAssessment(file);
		final var findings = new ArrayList<Finding>();
		final var inventory = new ArrayList<Inventory>();
		inventory.add(new Inventory(null, "", "linkedTables", snapshot.linkedTables().size()));
		inventory.add(new Inventory(null, "", "savedQueries", snapshot.savedQueries().size()));
		for (final String linkedTable : snapshot.linkedTables()) {
			findings.add(new Finding("access.linked-table", Severity.WARNING, Evidence.SCHEMA,
					new ObjectId(null, "", "linkedTable", linkedTable),
					"Only the link name was collected; the linked source was not opened or assessed.",
					"Inventory and assess the linked data source separately, then plan extraction and application relinking.", null));
		}
		for (final var query : snapshot.savedQueries()) {
			findings.add(new Finding("access.saved-query", Severity.REVIEW, Evidence.SCHEMA,
					new ObjectId(null, "", "savedQuery", query.name()),
					"Saved query type=" + query.type() + ", hidden=" + query.hidden()
							+ ", parameterized=" + query.parameterized() + "; SQL was not translated or executed.",
					"Review Access functions, parameters, form references, joins and update behavior; port and regression-test the query.", null));
		}
		if (!snapshot.relationshipsCollected()) {
			manual(findings, "access.relationship-coverage",
					"Relationships were not collected because this file contains linked tables.",
					"Inventory local and linked relationships separately; zero collected relationships does not mean none exist.");
		}
		manual(findings, "access.application-coverage", "Forms, reports, VBA, macros and application dependencies were not inspected.",
				"Inventory application assets and decide whether Access remains the frontend; test linked-table CRUD, generated keys and business workflows.");
		manual(findings, "access.data-not-scanned", "Row data was not read. Metadata findings do not certify lossless migration.",
				"Use a stable source copy. Profile NULL/empty strings, encoded lengths, numeric/date ranges, duplicate keys and orphans; reconcile rows, values and totals after loading.");
		return new MigrationAssessmentSource(List.of(snapshot.schema()), new MigrationAssessment(findings, inventory),
				false, snapshot.relationshipsCollected());
	}

	private static void manual(final List<Finding> findings, final String rule, final String reason, final String action) {
		findings.add(new Finding(rule, Severity.REVIEW, Evidence.MANUAL_CHECK, null, reason, action, null));
	}
}
