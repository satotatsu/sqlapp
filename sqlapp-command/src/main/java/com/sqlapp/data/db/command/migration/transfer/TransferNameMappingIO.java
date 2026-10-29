/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.util.MessageDigests;
import com.sqlapp.util.YamlConverter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.*;

/** Bounded, pinned name-only mapping IO; no connections or SQL execution. */
public final class TransferNameMappingIO {
	static final String CAPTURED_FILE = "transfer-mapping.yaml";
	private static final String DATA_JOB_PIN = "# transfer-name-mapping: ";
	private static final long MAX_BYTES = 1_048_576;

	static String pinDataJob(String text, Snapshot mapping) {
		return mapping == null ? text : DATA_JOB_PIN + mapping.fingerprint() + "\n" + text;
	}

	static void checkDataJobPin(String text, String fingerprint) {
		if (!text.startsWith(DATA_JOB_PIN + fingerprint + "\n"))
			throw new IllegalArgumentException("Reviewed data job name mapping fingerprint changed.");
	}

	static void capture(Snapshot mapping, Path directory) throws IOException {
		if (mapping == null)
			return;
		var io = new TransferNameMappingIO();
		if (!io.read(mapping.file()).fingerprint().equals(mapping.fingerprint()))
			throw new IllegalArgumentException("Name mapping changed during preparation. Review again.");
		io.write(directory.resolve(CAPTURED_FILE), mapping.text(), null);
	}

	public record TableMapping(String targetTable, Map<String, String> columns) {
		public TableMapping {
			if (targetTable != null) {
				name(targetTable);
				if (targetTable.contains("."))
					throw new IllegalArgumentException("Use targetSchema for namespaces, not dots in targetTable.");
			}
			columns = columns == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(columns));
			columns.forEach((source, target) -> {
				name(source);
				name(target);
			});
		}
	}

	public record Configuration(int formatVersion, Map<String, TableMapping> tables) {
		public Configuration {
			if (formatVersion != 1 || tables == null)
				throw new IllegalArgumentException("Require formatVersion: 1 and tables.");
			tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
			tables.forEach((source, mapping) -> {
				name(source);
				Objects.requireNonNull(mapping, "table mapping");
			});
		}
	}

	public record Snapshot(Path file, String text, String fingerprint, Configuration configuration) {
		public Snapshot {
			file = file.toAbsolutePath().normalize();
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(fingerprint, "fingerprint");
			Objects.requireNonNull(configuration, "configuration");
		}
	}

	public Configuration validate(String text) {
		bytes(text);
		try {
			var tree = mapper().readTree(text);
			tree.path("tables").forEach(table -> {
				var target = table.get("targetTable");
				if (target != null && !target.isNull() && !target.isString())
					throw new IllegalArgumentException();
				table.path("columns").forEach(value -> {
					if (!value.isString())
						throw new IllegalArgumentException();
				});
			});
			var value = mapper().readValue(text, Configuration.class);
			if (value == null)
				throw new IllegalArgumentException();
			return value;
		} catch (RuntimeException failure) {
			throw new IllegalArgumentException(
					"Invalid name mapping. Require formatVersion: 1 and tables keyed by source table identity; targetTable and columns (source-to-target names) are optional. Check unknown/duplicate fields, names and value types.");
		}
	}

	public Snapshot read(Path file) throws IOException {
		byte[] bytes = BoundedMigrationFile.read(file, MAX_BYTES, "maxTransferMappingBytes", "Transfer name mapping");
		String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
		return new Snapshot(file, text, fingerprint(bytes), validate(text));
	}

	public String format(Configuration value) {
		return mapper().writeValueAsString(Objects.requireNonNull(value));
	}

	public Snapshot write(Path file, String text, String expectedFingerprint) throws IOException {
		String filename = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!filename.endsWith(".yaml") && !filename.endsWith(".yml"))
			throw new IllegalArgumentException("Choose a .yaml or .yml mapping file.");
		var configuration = validate(text);
		byte[] bytes = bytes(text);
		check(file, expectedFingerprint);
		AtomicMigrationFile.write(file, temporary -> {
			Files.write(temporary, bytes);
			check(file, expectedFingerprint);
		});
		return new Snapshot(file, text, fingerprint(bytes), configuration);
	}

	private void check(Path file, String expected) throws IOException {
		if (expected == null ? Files.exists(file)
				: !Files.isRegularFile(file) || !expected.equals(read(file).fingerprint()))
			throw new IOException("Mapping file exists or changed. Reopen it before saving, or choose a new file.");
	}

	private static void name(String value) {
		if (value == null || value.isBlank() || value.codePoints().anyMatch(Character::isISOControl)
				|| !StandardCharsets.UTF_8.newEncoder().canEncode(value))
			throw new IllegalArgumentException(
					"Mapping names must be nonblank Unicode identifiers without control characters.");
	}

	private static byte[] bytes(String text) {
		if (text == null || text.length() > MAX_BYTES || !StandardCharsets.UTF_8.newEncoder().canEncode(text))
			throw new IllegalArgumentException("Name mapping must be valid UTF-8 and at most 1 MiB.");
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_BYTES)
			throw new IllegalArgumentException("Name mapping exceeds 1 MiB.");
		return bytes;
	}

	private static String fingerprint(byte[] bytes) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(bytes);
	}

	private static tools.jackson.databind.ObjectMapper mapper() {
		return new YamlConverter().getObjectMapper().rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
				DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
				.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
				.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
	}
}
