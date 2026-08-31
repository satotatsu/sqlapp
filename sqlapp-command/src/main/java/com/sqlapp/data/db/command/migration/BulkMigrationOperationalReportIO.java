/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Atomically writes bulk migration operational reports as UTF-8 JSON. */
public final class BulkMigrationOperationalReportIO {
	private final JsonConverter converter;

	public BulkMigrationOperationalReportIO() {
		this(new JsonConverter());
	}

	BulkMigrationOperationalReportIO(final JsonConverter converter) {
		this.converter = Objects.requireNonNull(converter, "converter");
		this.converter.setIndentOutput(true);
	}

	public void write(final Path file, final BulkMigrationOperationalReport report) {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(report, "report");
		final Path absolute = file.toAbsolutePath();
		final Path directory = absolute.getParent();
		Path temporary = null;
		try {
			Files.createDirectories(directory);
			temporary = Files.createTempFile(directory, absolute.getFileName().toString(), ".tmp");
			converter.writeJsonValue(temporary.toFile(), report);
			try {
				Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write bulk migration report: " + absolute, e);
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException ignored) {
					// Preserve the original report-writing failure.
				}
			}
		}
	}
}
