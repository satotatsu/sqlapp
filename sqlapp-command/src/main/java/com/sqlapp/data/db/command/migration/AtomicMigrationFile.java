/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

final class AtomicMigrationFile {
	@FunctionalInterface
	interface TemporaryFileWriter {
		void write(Path file) throws IOException;
	}

	private AtomicMigrationFile() {
	}

	static void write(final Path file, final TemporaryFileWriter writer) throws IOException {
		final Path absolute = file.toAbsolutePath().normalize();
		final Path directory = absolute.getParent();
		Files.createDirectories(directory);
		Path temporary = Files.createTempFile(directory,
				absolute.getFileName().toString(), ".tmp");
		try {
			writer.write(temporary);
			try (FileChannel channel = FileChannel.open(temporary,
					StandardOpenOption.WRITE)) {
				channel.force(true);
			}
			try {
				Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
			}
			temporary = null;
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException ignored) {
					// Preserve the primary write or replacement failure.
				}
			}
		}
	}

	static void writeProperties(final Path file, final Properties values,
			final String comment) throws IOException {
		write(file, temporary -> {
			try (FileChannel channel = FileChannel.open(temporary,
					StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
					var output = Channels.newOutputStream(channel)) {
				values.store(output, comment);
				output.flush();
			}
		});
	}
}
