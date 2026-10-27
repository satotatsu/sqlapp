/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.util.MessageDigests;
import com.sqlapp.util.YamlConverter;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;

/**
 * Offline, bounded and text-preserving IO shared by desktop and headless
 * callers.
 */
public final class TransferJobConfigurationIO {
	private static final int MAX_BYTES = 1_048_576;

	public record Snapshot(Path file, String text, String fingerprint, TransferJobConfiguration configuration) {
		public Snapshot {
			file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(fingerprint, "fingerprint");
			Objects.requireNonNull(configuration, "configuration");
		}

		/**
		 * Resolve optional mapping relative to the job, never the process working
		 * directory.
		 */
		public Path mappingFile() {
			return configuration.mappingFile() == null ? null
					: file.getParent().resolve(configuration.mappingFile()).normalize();
		}

		/** File transport location follows the saved job when its directory moves. */
		public Path filesDirectory() {
			return configuration.files() == null ? null
					: file.getParent().resolve(configuration.files().directory()).normalize();
		}
	}

	public TransferJobConfiguration validate(String text) {
		encoded(text);
		try {
			final var configuration = mapper().readValue(text, TransferJobConfiguration.class);
			if (configuration == null)
				throw new IllegalArgumentException();
			// Check platform path syntax without accessing the file or resolving a
			// database.
			if (configuration.mappingFile() != null)
				Path.of(configuration.mappingFile());
			if (configuration.files() != null)
				Path.of(configuration.files().directory());
			return configuration;
		} catch (RuntimeException failure) {
			// Parser exceptions may contain credentials mistakenly pasted into the job.
			throw new IllegalArgumentException("Invalid transfer job. Require formatVersion: 1, runtime source/target "
					+ "connection names, a nonempty unique tables list and schemaMode (CREATE_MISSING or REQUIRE_EXISTING). "
					+ "Check field names, types, paths and chunkSize (1..1000000). FILE dataMode requires files.directory, "
					+ "a supported encoding and an explicit CSV nullValue; JSONL uses JSON null. Credentials are runtime inputs.");
		}
	}

	public Snapshot read(Path file) throws IOException {
		final byte[] bytes = BoundedMigrationFile.read(file, (long) MAX_BYTES, "maxTransferJobBytes", "Transfer job");
		final String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
		return new Snapshot(file, text, fingerprint(bytes), validate(text));
	}

	/**
	 * Serialize the validated model without embedding runtime connection
	 * information.
	 */
	public String format(TransferJobConfiguration configuration) {
		final String text = mapper().writeValueAsString(Objects.requireNonNull(configuration, "configuration"));
		validate(text);
		return text;
	}

	/**
	 * Save verbatim UTF-8 text. A null expected fingerprint creates a new file;
	 * otherwise the existing file must match. Checks do not constitute a process
	 * lock.
	 */
	public Snapshot write(Path file, String text, String expectedFingerprint) throws IOException {
		Objects.requireNonNull(file, "file");
		final String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!name.endsWith(".yaml") && !name.endsWith(".yml"))
			throw new IllegalArgumentException("Choose a .yaml or .yml transfer job file.");
		if (expectedFingerprint != null && !expectedFingerprint.matches("sha256:[0-9a-f]{64}"))
			throw new IllegalArgumentException("Expected file fingerprint must be a lowercase SHA-256 value.");
		final var configuration = validate(text);
		final byte[] bytes = encoded(text);
		checkCurrent(file, expectedFingerprint);
		AtomicMigrationFile.write(file, temporary -> {
			Files.write(temporary, bytes);
			checkCurrent(file, expectedFingerprint);
		});
		return new Snapshot(file, text, fingerprint(bytes), configuration);
	}

	private static tools.jackson.databind.ObjectMapper mapper() {
		return new YamlConverter().getObjectMapper().rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
				.enable(tools.jackson.databind.cfg.EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
				.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
				.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
				.build();
	}

	private void checkCurrent(Path file, String expected) throws IOException {
		if (expected == null) {
			if (Files.exists(file))
				throw new IOException("Transfer job already exists. Open it before editing or choose a new filename.");
		} else if (!Files.isRegularFile(file) || !expected.equals(read(file).fingerprint())) {
			throw new IOException("Transfer job changed outside the editor. Reopen it or save to a new filename.");
		}
	}

	private static byte[] encoded(String text) {
		Objects.requireNonNull(text, "text");
		if (text.length() > MAX_BYTES)
			throw new IllegalArgumentException("Transfer job exceeds the 1 MiB limit.");
		try {
			final var buffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text));
			if (buffer.remaining() > MAX_BYTES)
				throw new IllegalArgumentException("Transfer job exceeds the 1 MiB limit.");
			final byte[] bytes = new byte[buffer.remaining()];
			buffer.get(bytes);
			return bytes;
		} catch (java.nio.charset.CharacterCodingException failure) {
			throw new IllegalArgumentException("Transfer job contains invalid Unicode characters.");
		}
	}

	private static String fingerprint(byte[] bytes) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(bytes);
	}
}
