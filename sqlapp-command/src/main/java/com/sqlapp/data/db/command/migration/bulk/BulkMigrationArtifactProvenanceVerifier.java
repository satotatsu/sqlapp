/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;

import com.sqlapp.exceptions.CommandException;

/** Shared exact-file verification for bulk migration provenance. */
final class BulkMigrationArtifactProvenanceVerifier {
	private BulkMigrationArtifactProvenanceVerifier() {
	}

	static void verify(final File file, final String expectedFingerprint, final String fileProperty,
			final String provenanceProperty) {
		verify(file, expectedFingerprint, fileProperty, provenanceProperty, null);
	}

	static void verify(final File file, final String expectedFingerprint, final String fileProperty,
			final String provenanceProperty, final Long maxFileSizeBytes) {
		if (file == null) {
			return;
		}
		if (expectedFingerprint == null) {
			throw new CommandException(fileProperty + " requires " + provenanceProperty + " in provenance.");
		}
		final String actual;
		try {
			actual = BoundedMigrationFile.sha256(file.toPath(), maxFileSizeBytes,
					"maxApprovalArtifactFileSizeBytes", fileProperty);
		} catch (java.io.IOException e) {
			throw new CommandException("Could not fingerprint " + fileProperty + ": " + e.getMessage(), e);
		}
		if (!expectedFingerprint.equals(actual)) {
			throw new CommandException(fileProperty + " fingerprint does not match provenance."
					+ provenanceProperty + ".");
		}
	}
}
