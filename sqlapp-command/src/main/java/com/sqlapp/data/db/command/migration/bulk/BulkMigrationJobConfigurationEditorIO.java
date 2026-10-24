/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.util.MessageDigests;
import com.sqlapp.util.YamlConverter;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

/**
 * Text-preserving, offline IO for editing job configuration, without resolving
 * or executing it.
 */
public class BulkMigrationJobConfigurationEditorIO {
	private static final int MAX_BYTES = 1_048_576;

	/**
	 * Syntax/type failure with the parser's one-based location, or -1 when
	 * unavailable.
	 */
	public static final class ValidationException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
		private final int line;
		private final int column;

		private ValidationException(final String message, final int line, final int column) {
			super(message);
			this.line = line;
			this.column = column;
		}

		public int getLine() {
			return line;
		}

		public int getColumn() {
			return column;
		}
	}

	/**
	 * The exact text and byte identity read from, or saved to, a configuration
	 * file.
	 */
	public record Snapshot(Path file, String text, String fingerprint) {
		public Snapshot {
			file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(fingerprint, "fingerprint");
		}
	}

	/**
	 * Reads even malformed YAML so an editor can repair it; rejects oversized or
	 * invalid UTF-8 text.
	 */
	public Snapshot read(final Path file) throws IOException {
		final byte[] bytes;
		try (var input = Files.newInputStream(file)) {
			bytes = input.readNBytes(MAX_BYTES + 1);
		}
		if (bytes.length > MAX_BYTES)
			throw new IOException("Job configuration exceeds the editor's 1 MiB limit.");
		final String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
		return new Snapshot(file, text, fingerprint(bytes));
	}

	/**
	 * Checks syntax and mapping to the existing configuration model. This does not
	 * validate Schema references, task policies, fingerprints, checkpoints or live
	 * database compatibility.
	 */
	public BulkMigrationJobConfiguration validate(final String text) {
		encoded(text);
		final BulkMigrationJobConfiguration configuration;
		try {
			final var mapper = new YamlConverter().getObjectMapper().rebuild()
					.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
					.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
					.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
					.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
			configuration = mapper.readValue(text, BulkMigrationJobConfiguration.class);
		} catch (final RuntimeException exception) {
			final var location = exception instanceof tools.jackson.core.JacksonException json ? json.getLocation()
					: null;
			final int line = location == null || location.getLineNr() <= 0 ? -1 : location.getLineNr();
			final int column = location == null || location.getColumnNr() <= 0 ? -1 : location.getColumnNr();
			final String position = line > 0 ? " at line " + line + (column > 0 ? ", column " + column : "") : "";
			throw new ValidationException("Invalid job YAML syntax or configuration field/type" + position
					+ ". Check field names, enum values and YAML structure.", line, column);
		}
		if (configuration == null)
			throw new IllegalArgumentException("Job YAML must contain a configuration object.");
		return configuration;
	}

	/**
	 * Saves valid configuration text verbatim. Null expectedFingerprint creates a
	 * new file only; otherwise the current bytes must still match the caller's
	 * opened snapshot. Checks are made before writing and just before atomic
	 * replacement; this is not a cross-process file lock.
	 */
	public Snapshot write(final Path file, final String text, final String expectedFingerprint) throws IOException {
		final String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!name.endsWith(".yaml") && !name.endsWith(".yml") && !name.endsWith(".json")) {
			throw new IllegalArgumentException("Choose a .yaml, .yml or .json job configuration file.");
		}
		if (expectedFingerprint != null && !expectedFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new IllegalArgumentException("Expected file fingerprint must be a lowercase SHA-256 value.");
		}
		validate(text);
		final byte[] bytes = encoded(text);
		checkCurrent(file, expectedFingerprint);
		AtomicMigrationFile.write(file, temporary -> {
			Files.write(temporary, bytes);
			checkCurrent(file, expectedFingerprint);
		});
		return new Snapshot(file, text, fingerprint(bytes));
	}

	private void checkCurrent(final Path file, final String expected) throws IOException {
		if (expected == null) {
			if (Files.exists(file))
				throw new IOException("Destination already exists. Open it before editing or choose a new filename.");
		} else if (!Files.isRegularFile(file) || !expected.equals(read(file).fingerprint())) {
			throw new IOException("Job file changed outside the editor. Reopen it or save to a new filename.");
		}
	}

	private static byte[] encoded(final String text) {
		final byte[] bytes;
		try {
			final var encoded = StandardCharsets.UTF_8.newEncoder()
					.encode(java.nio.CharBuffer.wrap(Objects.requireNonNull(text, "text")));
			bytes = new byte[encoded.remaining()];
			encoded.get(bytes);
		} catch (final java.nio.charset.CharacterCodingException exception) {
			throw new IllegalArgumentException("Job text contains invalid Unicode characters.");
		}
		if (bytes.length > MAX_BYTES)
			throw new IllegalArgumentException("Job configuration exceeds the editor's 1 MiB limit.");
		return bytes;
	}

	private static String fingerprint(final byte[] bytes) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(bytes);
	}
}
