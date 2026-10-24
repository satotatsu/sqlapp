/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class ReviewFileWriter {
	void write(final Path destination, final String text, final Path original, final Path target) throws IOException {
		final Path file = destination.toAbsolutePath().normalize();
		for (final Path input : new Path[] { original, target }) {
			if (file.equals(input.toAbsolutePath().normalize())
					|| Files.exists(file) && Files.isSameFile(file, input)) {
				throw new IllegalArgumentException("Choose a destination different from the input Schema files.");
			}
		}
		final Path temporary = Files.createTempFile(file.getParent(), ".sqlapp-review-", ".tmp");
		try {
			Files.writeString(temporary, text, StandardCharsets.UTF_8);
			try {
				Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (final AtomicMoveNotSupportedException exception) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
