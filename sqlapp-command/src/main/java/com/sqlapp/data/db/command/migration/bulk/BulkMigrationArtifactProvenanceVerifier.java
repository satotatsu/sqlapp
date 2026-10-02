/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

/** Shared exact-file verification for bulk migration provenance. */
final class BulkMigrationArtifactProvenanceVerifier {
	private BulkMigrationArtifactProvenanceVerifier() {
	}

	static void verify(final File file, final String expectedFingerprint, final String fileProperty,
			final String provenanceProperty) {
		if (file == null) {
			return;
		}
		if (expectedFingerprint == null) {
			throw new CommandException(fileProperty + " requires " + provenanceProperty + " in provenance.");
		}
		final String actual = "sha256:" + MessageDigests.SHA256.checksumAsString(file);
		if (!expectedFingerprint.equals(actual)) {
			throw new CommandException(fileProperty + " fingerprint does not match provenance."
					+ provenanceProperty + ".");
		}
	}
}
