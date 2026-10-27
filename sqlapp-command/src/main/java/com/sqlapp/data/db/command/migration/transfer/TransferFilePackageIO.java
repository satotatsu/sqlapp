/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.MessageDigests;

/**
 * Completion marker and byte-integrity checks for a portable CSV export
 * package.
 */
public final class TransferFilePackageIO {
	public record TableFile(String taskId, String catalog, String schema, String table, List<String> columns,
			String file, @com.fasterxml.jackson.annotation.JsonProperty(required = true) long rows,
			@com.fasterxml.jackson.annotation.JsonProperty(required = true) long bytes, String fingerprint) {
		public TableFile {
			if (taskId == null || taskId.isBlank() || table == null || table.isBlank() || columns == null
					|| columns.isEmpty() || columns.stream().anyMatch(value -> value == null || value.isBlank())
					|| new HashSet<>(columns).size() != columns.size() || file == null
					|| !file.matches("table-[0-9]+\\.csv") || rows < 0 || bytes < 0 || !hash(fingerprint))
				throw new IllegalArgumentException("Invalid exported table descriptor.");
			columns = List.copyOf(columns);
		}
	}

	public record Warning(String taskId, String column, String message) {
	}

	public record Manifest(int formatVersion, String jobFingerprint, String schemaFingerprint,
			TransferJobConfiguration.FileTransfer files, List<TableFile> tables, List<Warning> warnings) {
		public Manifest(int formatVersion, String jobFingerprint, String schemaFingerprint,
				TransferJobConfiguration.FileTransfer files, List<TableFile> tables) {
			this(formatVersion, jobFingerprint, schemaFingerprint, files, tables, List.of());
		}

		public Manifest {
			warnings = warnings == null ? List.of() : List.copyOf(warnings);
			if (formatVersion != 1 || !hash(jobFingerprint) || !hash(schemaFingerprint) || files == null
					|| files.format() != TransferJobConfiguration.FileFormat.CSV || tables == null || tables.isEmpty())
				throw new IllegalArgumentException("Invalid CSV package manifest.");
			tables = List.copyOf(tables);
			if (tables.stream().map(TableFile::file).distinct().count() != tables.size()
					|| tables.stream().map(TableFile::taskId).distinct().count() != tables.size())
				throw new IllegalArgumentException("Duplicate exported file or task identity.");
			for (var warning : warnings)
				if (warning.taskId() == null || warning.column() == null || warning.message() == null
						|| tables.stream().noneMatch(table -> table.taskId().equals(warning.taskId())
								&& table.columns().contains(warning.column())))
					throw new IllegalArgumentException("Invalid export warning identity.");
		}
	}

	public record Snapshot(Path file, String fingerprint, Manifest manifest) {
	}

	/**
	 * Checks only integrity, not authenticity, live target compatibility or
	 * execution approval.
	 */
	public Snapshot read(Path file, String expectedFingerprint) throws IOException {
		final byte[] bytes = BoundedMigrationFile.read(file, 1_048_576L, "maxManifestBytes", "File manifest");
		final String fingerprint = fingerprint(bytes);
		if (expectedFingerprint != null && !expectedFingerprint.equals(fingerprint))
			throw new IllegalArgumentException("Package manifest fingerprint changed.");
		final Manifest manifest;
		try {
			final String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
			manifest = mapper().readValue(text, Manifest.class);
			if (manifest == null)
				throw new IllegalArgumentException();
		} catch (Exception failure) {
			throw new IllegalArgumentException("Invalid file package manifest.");
		}
		validateMembers(file.toAbsolutePath().normalize().getParent(), manifest);
		return new Snapshot(file.toAbsolutePath().normalize(), fingerprint, manifest);
	}

	/**
	 * Publish last, after the exporter has closed its source connection and all
	 * data files.
	 */
	Snapshot publish(Path directory, Manifest manifest) throws IOException {
		validateMembers(directory, manifest);
		final byte[] bytes = mapper().writeValueAsString(manifest).getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 1_048_576)
			throw new IllegalArgumentException("File manifest exceeds 1 MiB.");
		final Path file = directory.resolve("manifest.json");
		if (Files.exists(file))
			throw new IOException("Package manifest already exists.");
		AtomicMigrationFile.write(file, temporary -> {
			Files.write(temporary, bytes);
			if (Files.exists(file))
				throw new IOException("Package manifest already exists.");
		});
		return new Snapshot(file.toAbsolutePath().normalize(), fingerprint(bytes), manifest);
	}

	private static void validateMembers(Path directory, Manifest manifest) throws IOException {
		final Path jobFile = member(directory, "transfer.yaml");
		final var job = new TransferJobConfigurationIO().read(jobFile);
		if (!job.fingerprint().equals(manifest.jobFingerprint())
				|| job.configuration().dataMode() != TransferJobConfiguration.DataMode.FILE
				|| !manifest.files().equals(job.configuration().files()))
			throw new IllegalArgumentException("Package definition or file settings changed.");
		final Path schema = member(directory, "source-schema.xml");
		if (!BoundedMigrationFile.sha256(schema, 16_777_216L, "maxSchemaBytes", "Schema")
				.equals(manifest.schemaFingerprint()))
			throw new IllegalArgumentException("Package Schema changed.");
		for (final TableFile table : manifest.tables()) {
			final Path data = member(directory, table.file());
			if (Files.size(data) != table.bytes()
					|| !BoundedMigrationFile.sha256(data, null, "maxDataBytes", "CSV data").equals(table.fingerprint()))
				throw new IllegalArgumentException("Package CSV data changed or is incomplete.");
		}
	}

	private static Path member(Path directory, String name) throws IOException {
		final Path file = directory.resolve(name);
		if (Files.isSymbolicLink(file) || !Files.isRegularFile(file))
			throw new IOException("Package member is missing, not a regular file or a symbolic link.");
		return file;
	}

	private static boolean hash(String value) {
		return value != null && value.matches("sha256:[0-9a-f]{64}");
	}

	private static String fingerprint(byte[] bytes) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(bytes);
	}

	private static tools.jackson.databind.ObjectMapper mapper() {
		return new JsonConverter().getObjectMapper().rebuild()
				.enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
				.disable(tools.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT)
				.disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
				.enable(tools.jackson.databind.cfg.EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
				.enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
	}
}
