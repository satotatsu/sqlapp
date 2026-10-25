/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.schema;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.util.MessageDigests;

/** Offline, text-preserving SQL editing. This does not parse or execute SQL. */
public class MigrationSqlEditorIO {
	private static final int MAX_BYTES = 1_048_576;

	public record Snapshot(Path file, String text, String fingerprint) {
		public Snapshot {
			file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(fingerprint, "fingerprint");
		}
	}

	/** Reads up to 1 MiB of strict UTF-8, including SQL needing repair. */
	public Snapshot read(final Path file) throws IOException {
		requireSql(file);
		final byte[] bytes;
		try (var input = Files.newInputStream(file)) {
			bytes = input.readNBytes(MAX_BYTES + 1);
		}
		if (bytes.length > MAX_BYTES)
			throw new IllegalArgumentException("SQL editor files must not exceed 1 MiB.");
		final String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
		return new Snapshot(file, text, fingerprint(bytes));
	}

	/**
	 * Null expected fingerprint creates a new file only. Otherwise checks the
	 * opened bytes before writing and before replacement. This is optimistic
	 * conflict detection, not a cross-process file lock.
	 */
	public Snapshot write(final Path file, final String text, final String expectedFingerprint) throws IOException {
		requireSql(file);
		if (expectedFingerprint != null && !expectedFingerprint.matches("sha256:[0-9a-f]{64}"))
			throw new IllegalArgumentException("Expected SQL fingerprint must be a lowercase SHA-256 value.");
		final var encoded = StandardCharsets.UTF_8.newEncoder()
				.encode(CharBuffer.wrap(Objects.requireNonNull(text, "text")));
		if (encoded.remaining() > MAX_BYTES)
			throw new IllegalArgumentException("SQL editor text must not exceed 1 MiB.");
		final byte[] bytes = new byte[encoded.remaining()];
		encoded.get(bytes);
		checkCurrent(file, expectedFingerprint);
		AtomicMigrationFile.write(file, temporary -> {
			Files.write(temporary, bytes);
			checkCurrent(file, expectedFingerprint);
		});
		return new Snapshot(file, text, fingerprint(bytes));
	}

	/**
	 * Verifies saved bytes and delegates filename/duplicate-version checks to the
	 * migration reader.
	 */
	public Path migrationDirectory(final Snapshot snapshot) throws IOException {
		Objects.requireNonNull(snapshot, "snapshot");
		checkCurrent(snapshot.file(), snapshot.fingerprint());
		final var handler = new DbVersionFileHandler();
		handler.setUpSqlDirectory(snapshot.file().getParent().toFile());
		final boolean recognized;
		try {
			recognized = handler.read().stream()
					.anyMatch(sql -> sql.getUpSqlFile().toPath().toAbsolutePath().normalize().equals(snapshot.file()));
		} catch (final NumberFormatException exception) {
			throw new IllegalArgumentException("A SQL filename version exceeds the non-negative Java long range.",
					exception);
		}
		if (!recognized)
			throw new IllegalArgumentException(
					"Use <numeric version>_<description>.sql before selecting a migration directory.");
		return snapshot.file().getParent();
	}

	private void checkCurrent(final Path file, final String expected) throws IOException {
		if (expected == null) {
			if (Files.exists(file))
				throw new IOException("Destination exists. Open it before editing or choose a new filename.");
		} else if (!Files.isRegularFile(file) || !expected.equals(read(file).fingerprint())) {
			throw new IOException("SQL file changed outside the editor. Reopen it or save to a new filename.");
		}
	}

	private static void requireSql(final Path file) {
		if (file == null || file.getFileName() == null || !file.getFileName().toString().endsWith(".sql"))
			throw new IllegalArgumentException(
					"Choose a file with the lowercase .sql extension used by migration planning.");
	}

	private static String fingerprint(final byte[] bytes) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(bytes);
	}
}
