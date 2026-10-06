/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sqlapp.exceptions.CommandException;

/** Reads JSON bytes without crossing an optional caller-selected size limit. */
final class BoundedMigrationJsonFile {
	private BoundedMigrationJsonFile() {
	}

	static byte[] read(final Path file, final Long maxFileSizeBytes, final String optionName,
			final String artifactName) throws IOException {
		if (maxFileSizeBytes == null) {
			return Files.readAllBytes(file);
		}
		if (maxFileSizeBytes <= 0) {
			throw new CommandException(optionName + " must be greater than zero.");
		}
		if (Files.size(file) > maxFileSizeBytes) {
			throw tooLarge(optionName, artifactName);
		}
		try (var input = Files.newInputStream(file); var output = new ByteArrayOutputStream()) {
			final byte[] buffer = new byte[8192];
			long total = 0;
			int length;
			while ((length = input.read(buffer)) != -1) {
				if (total > maxFileSizeBytes - length) {
					throw tooLarge(optionName, artifactName);
				}
				output.write(buffer, 0, length);
				total += length;
			}
			return output.toByteArray();
		}
	}

	private static CommandException tooLarge(final String optionName, final String artifactName) {
		return new CommandException(artifactName + " exceeds " + optionName + ".");
	}
}
