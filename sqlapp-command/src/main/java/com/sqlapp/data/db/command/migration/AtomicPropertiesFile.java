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

final class AtomicPropertiesFile {
	private AtomicPropertiesFile() {
	}

	static void write(final Path directory, final Path file,
			final Properties values, final String comment) throws IOException {
		Files.createDirectories(directory);
		Path temporary = Files.createTempFile(directory,
				file.getFileName().toString(), ".tmp");
		try {
			try (FileChannel channel = FileChannel.open(temporary,
					StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING);
					var output = Channels.newOutputStream(channel)) {
				values.store(output, comment);
				output.flush();
				channel.force(true);
			}
			try {
				Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
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
}
