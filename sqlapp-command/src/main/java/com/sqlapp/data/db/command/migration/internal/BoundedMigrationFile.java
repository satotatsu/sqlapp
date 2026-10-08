/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.sqlapp.exceptions.CommandException;

/**
 * Shared bounded byte-snapshot and fingerprint reader for migration artifacts.
 */
public final class BoundedMigrationFile {
	private BoundedMigrationFile() {
	}

	public static byte[] read(final Path file, final Long maxFileSizeBytes, final String optionName,
			final String artifactName) throws IOException {
		if (maxFileSizeBytes == null) {
			return Files.readAllBytes(file);
		}
		validateLimit(maxFileSizeBytes, optionName);
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

	public static String sha256(final Path file, final Long maxFileSizeBytes, final String optionName,
			final String artifactName) throws IOException {
		if (maxFileSizeBytes != null) {
			validateLimit(maxFileSizeBytes, optionName);
			if (Files.size(file) > maxFileSizeBytes) {
				throw tooLarge(optionName, artifactName);
			}
		}
		try (var input = Files.newInputStream(file)) {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			final byte[] buffer = new byte[8192];
			long total = 0;
			int length;
			while ((length = input.read(buffer)) != -1) {
				if (maxFileSizeBytes != null && total > maxFileSizeBytes - length) {
					throw tooLarge(optionName, artifactName);
				}
				digest.update(buffer, 0, length);
				total += length;
			}
			return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	private static void validateLimit(final Long maxFileSizeBytes, final String optionName) {
		if (maxFileSizeBytes <= 0) {
			throw new CommandException(optionName + " must be greater than zero.");
		}
	}

	private static CommandException tooLarge(final String optionName, final String artifactName) {
		return new CommandException(artifactName + " exceeds " + optionName + ".");
	}
}
