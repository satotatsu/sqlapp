/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

/** Immutable identities of the approved artifacts behind a declarative job. */
public record BulkMigrationArtifactProvenance(String configurationFingerprint, String assessmentReportFingerprint,
		String ddlVerificationReportFingerprint, String targetValidationReportFingerprint) {
	public BulkMigrationArtifactProvenance(final String configurationFingerprint,
			final String assessmentReportFingerprint, final String ddlVerificationReportFingerprint) {
		this(configurationFingerprint, assessmentReportFingerprint, ddlVerificationReportFingerprint, null);
	}

	public BulkMigrationArtifactProvenance {
		validate("configurationFingerprint", configurationFingerprint, true);
		validate("assessmentReportFingerprint", assessmentReportFingerprint, false);
		validate("ddlVerificationReportFingerprint", ddlVerificationReportFingerprint, false);
		validate("targetValidationReportFingerprint", targetValidationReportFingerprint, false);
	}

	private static void validate(final String name, final String value, final boolean required) {
		if (value == null || value.isBlank()) {
			if (required) {
				throw new IllegalArgumentException(name + " must not be empty");
			}
			return;
		}
		if (!value.matches("sha256:[0-9a-f]{64}")) {
			throw new IllegalArgumentException(name + " must be a lowercase SHA-256 value");
		}
	}
}
