/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.migration;

import java.util.ArrayList;

import com.sqlapp.data.schemas.migration.assessment.DatabaseMigrationAssessmentProvider;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSource;

/** Oracle logical migration diagnosis. Only Access sources are currently supported. */
public final class OracleDatabaseMigrationAssessmentProvider implements DatabaseMigrationAssessmentProvider {
	@Override
	public boolean supports(final String sourceProduct, final String targetDatabase, final String targetVersion) {
		return "Microsoft Access".equals(sourceProduct) && "oracle".equalsIgnoreCase(targetDatabase)
				&& AccessOracleMigrationAssessment.supportsTargetVersion(targetVersion);
	}

	@Override
	public String targetProduct() { return "Oracle"; }

	@Override
	public String normalizeTargetVersion(final String version) {
		return AccessOracleMigrationAssessment.validateTargetVersion(version);
	}

	@Override
	public MigrationAssessment assess(final MigrationAssessmentSource source, final String targetVersion) {
		if (!supports(source.sourceProduct(), "oracle", targetVersion)) {
			throw new IllegalArgumentException("Oracle migration assessment supports Access sources and targets 19c, 21c, 23ai, 26ai only");
		}
		final var findings = new ArrayList<Finding>();
		final var inventory = new ArrayList<Inventory>();
		for (final var schema : source.schemas()) {
			final var assessment = AccessOracleMigrationAssessment.assess(schema, targetVersion);
			findings.addAll(assessment.findings());
			inventory.addAll(assessment.inventory());
		}
		findings.add(new Finding("access.oracle.environment", Severity.REVIEW, Evidence.MANUAL_CHECK, null,
				"The Oracle environment and client compatibility were not checked.",
				"Verify exact Oracle release, COMPATIBLE, character sets, identifier lengths/reserved words, collation, privileges and ODBC/JDBC support; rehearse cutover and recovery.", null));
		return new MigrationAssessment(findings, inventory);
	}
}
