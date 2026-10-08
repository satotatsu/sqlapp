/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.internal;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * Publishes a newly created artifact directory only after all files are ready.
 */
public final class AtomicMigrationDirectory {
	@FunctionalInterface
	public interface TemporaryDirectoryWriter {
		void write(Path directory) throws Exception;
	}

	private AtomicMigrationDirectory() {
	}

	public static void writeNew(final Path directory, final TemporaryDirectoryWriter writer) throws Exception {
		final Path absolute = directory.toAbsolutePath().normalize();
		if (Files.exists(absolute)) {
			throw new IOException("Migration artifact directory already exists: " + absolute);
		}
		final Path parent = absolute.getParent();
		Files.createDirectories(parent);
		Path temporary = Files.createTempDirectory(parent, "." + absolute.getFileName() + "-");
		try {
			writer.write(temporary);
			try {
				Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, absolute);
			}
			temporary = null;
		} finally {
			if (temporary != null) {
				deleteTree(temporary);
			}
		}
	}

	private static void deleteTree(final Path directory) {
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// Preserve the publication failure.
				}
			});
		} catch (IOException ignored) {
			// Preserve the publication failure.
		}
	}
}
