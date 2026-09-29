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
		return load(file, false);
	}

	@Override
	public MigrationAssessmentSource load(final Path file, final boolean scanData) throws IOException {
		if (!supports(file)) { throw new IllegalArgumentException("Access assessment requires an MDB/ACCDB file"); }
		final var snapshot = MdbFileLoader.loadForAssessment(file);
		final var findings = new ArrayList<Finding>();
		final var inventory = new ArrayList<Inventory>();
		inventory.add(new Inventory(null, "", "linkedTables", snapshot.linkedTables().size()));
		inventory.add(new Inventory(null, "", "savedQueries", snapshot.savedQueries().size()));
		inventory.addAll(savedQueryInventory(snapshot.savedQueries()));
		inventory.addAll(indexInventory(snapshot.schema()));
		int allowZeroLengthColumns = 0;
		for (final var table : snapshot.schema().getTables()) {
			for (final var column : table.getColumns()) {
				if (Boolean.TRUE.equals(column.getSpecifics().get(MdbFileLoader.ALLOW_ZERO_LENGTH, Boolean.class))) {
					allowZeroLengthColumns++;
					findings.add(new Finding("access.allow-zero-length", Severity.REVIEW, Evidence.SCHEMA,
							new ObjectId(snapshot.schema().getCatalogName(), snapshot.schema().getName(),
									"column", column.getName(), table.getName()),
							"The Access text column permits a zero-length string distinct from NULL.",
							"Preserve and test empty-string semantics; Oracle converts scalar empty strings to NULL, while other targets and clients may apply different validation.", null));
				}
			}
		}
		inventory.add(new Inventory(snapshot.schema().getCatalogName(), snapshot.schema().getName(),
				"accessAllowZeroLengthColumns", allowZeroLengthColumns));
		final String fileFormat = snapshot.schema().getSpecifics().get(MdbFileLoader.SOURCE_FILE_FORMAT, String.class);
		findings.add(new Finding("access.file-format", Severity.REVIEW, Evidence.SCHEMA,
				new ObjectId(null, "", "databaseFile", fileFormat),
				"Access file format=" + fileFormat + ".",
				"Confirm the selected Access driver, deployment architecture and retained frontend support this file format.", null));
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
		final var scan = scanData ? MdbDataProfiler.scan(file, snapshot.schema()) : null;
		if (scan == null) {
			manual(findings, "access.data-not-scanned", "Row data was not read. Metadata findings do not certify lossless migration.",
					"Use a stable source copy. Profile NULL/empty strings, encoded lengths, numeric/date ranges, duplicate keys and orphans; reconcile rows, values and totals after loading.");
		} else {
			findings.addAll(scan.findings());
			manual(findings, "access.data-scan-coverage", "Local scalar values were profiled; this is not a full integrity or migration verification.",
					"Review integrityChecks for completed and omitted key checks. Inspect nullable uniqueness, excluded columns and linked sources separately. Validate target encoding, precision, collation and post-load reconciliation.");
		}
		return new MigrationAssessmentSource(List.of(snapshot.schema()), new MigrationAssessment(findings, inventory),
				scanData, snapshot.relationshipsCollected(), scan == null ? null : scan.profile());
	}

	static List<Inventory> savedQueryInventory(final List<MdbAssessmentSnapshot.SavedQuery> queries) {
		int viewCandidates = 0;
		int hidden = 0;
		int parameterized = 0;
		int actionOrSpecial = 0;
		for (final var query : queries) {
			final boolean selectLike = "SELECT".equals(query.type()) || "UNION".equals(query.type());
			if (!query.hidden() && !query.parameterized() && selectLike) { viewCandidates++; }
			if (query.hidden()) { hidden++; }
			if (query.parameterized()) { parameterized++; }
			if (!selectLike) { actionOrSpecial++; }
		}
		return List.of(
				new Inventory(null, "", "savedQueryViewCandidates", viewCandidates),
				new Inventory(null, "", "savedQueryManualPorts", queries.size() - viewCandidates),
				new Inventory(null, "", "savedQueryHidden", hidden),
				new Inventory(null, "", "savedQueryParameterized", parameterized),
				new Inventory(null, "", "savedQueryActionOrSpecial", actionOrSpecial));
	}

	static List<Inventory> indexInventory(final com.sqlapp.data.schemas.Schema schema) {
		int indexes = 0;
		int unique = 0;
		int ignoreNulls = 0;
		for (final var table : schema.getTables()) {
			for (final var index : table.getIndexes()) {
				indexes++;
				if (index.isUnique()) { unique++; }
				if (Boolean.TRUE.equals(index.getSpecifics().get(MdbFileLoader.INDEX_IGNORE_NULLS, Boolean.class))) {
					ignoreNulls++;
				}
			}
		}
		return List.of(
				new Inventory(schema.getCatalogName(), schema.getName(), "accessIndexes", indexes),
				new Inventory(schema.getCatalogName(), schema.getName(), "accessUniqueIndexes", unique),
				new Inventory(schema.getCatalogName(), schema.getName(), "accessIgnoreNullsIndexes", ignoreNulls));
	}

	private static void manual(final List<Finding> findings, final String rule, final String reason, final String action) {
		findings.add(new Finding(rule, Severity.REVIEW, Evidence.MANUAL_CHECK, null, reason, action, null));
	}
}
