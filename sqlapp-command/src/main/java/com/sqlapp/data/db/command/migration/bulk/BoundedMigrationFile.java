/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Package-local compatibility bridge to the shared migration artifact reader.
 */
final class BoundedMigrationFile {
	private BoundedMigrationFile() {
	}

	static byte[] read(final Path file, final Long maxFileSizeBytes, final String optionName, final String artifactName)
			throws IOException {
		return com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile.read(file, maxFileSizeBytes,
				optionName, artifactName);
	}

	static String sha256(final Path file, final Long maxFileSizeBytes, final String optionName,
			final String artifactName) throws IOException {
		return com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile.sha256(file, maxFileSizeBytes,
				optionName, artifactName);
	}
}
