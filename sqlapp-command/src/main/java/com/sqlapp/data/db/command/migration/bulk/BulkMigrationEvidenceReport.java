/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Portable result of an offline bulk migration evidence audit. */
public record BulkMigrationEvidenceReport(int formatVersion, Instant generatedAt, String jobId,
		String planFingerprint, String operationalReportFingerprint, String verificationReportFingerprint,
		BulkMigrationOperationalReport.ExecutionEvent executionEvent, boolean dataMatch,
		BulkMigrationArtifactProvenance provenance, List<String> verificationPolicies,
		List<String> verifiedArtifacts) {
	public static final int CURRENT_FORMAT_VERSION = 1;
	public static final String POLICY_SUCCESSFUL_EXECUTION = "SUCCESSFUL_EXECUTION";
	public static final String POLICY_MATCHING_DATA = "MATCHING_DATA";
	public static final String POLICY_PROVENANCE_REQUIRED = "PROVENANCE_REQUIRED";
	public static final String ARTIFACT_OPERATIONAL_REPORT = "OPERATIONAL_REPORT";
	public static final String ARTIFACT_VERIFICATION_REPORT = "VERIFICATION_REPORT";
	public static final String ARTIFACT_CONFIGURATION = "CONFIGURATION";
	public static final String ARTIFACT_ASSESSMENT_REPORT = "ASSESSMENT_REPORT";
	public static final String ARTIFACT_DDL_VERIFICATION_REPORT = "DDL_VERIFICATION_REPORT";
	public static final String ARTIFACT_TARGET_VALIDATION_REPORT = "TARGET_VALIDATION_REPORT";

	public BulkMigrationEvidenceReport {
		Objects.requireNonNull(generatedAt, "generatedAt");
		verificationPolicies = List.copyOf(Objects.requireNonNull(verificationPolicies, "verificationPolicies"));
		verifiedArtifacts = List.copyOf(Objects.requireNonNull(verifiedArtifacts, "verifiedArtifacts"));
	}
}
