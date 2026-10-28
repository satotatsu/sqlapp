/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.util.List;

/**
 * Connection-free intent for an initial RDB transfer. Connection names refer to
 * runtime-supplied connections, not URLs or persisted credentials. This
 * definition alone is neither an executable plan nor an execution approval.
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record TransferJobConfiguration(int formatVersion, String source, String target, List<String> tables,
		SchemaMode schemaMode, String sourceSchema, String targetSchema, String mappingFile, Integer chunkSize,
		Boolean verify, DataMode dataMode, FileTransfer files, CompatibilityPolicy compatibilityPolicy) {
	public enum CompatibilityPolicy {
		WARN, ERROR
	}

	public TransferJobConfiguration(int formatVersion, String source, String target, List<String> tables,
			SchemaMode schemaMode, String sourceSchema, String targetSchema, String mappingFile, Integer chunkSize,
			Boolean verify, DataMode dataMode, FileTransfer files) {
		this(formatVersion, source, target, tables, schemaMode, sourceSchema, targetSchema, mappingFile, chunkSize,
				verify, dataMode, files, CompatibilityPolicy.WARN);
	}

	/**
	 * Transfer intent; SCHEMA_ONLY creates missing definitions without row access.
	 */
	public enum DataMode {
		JDBC_DIRECT, FILE, SCHEMA_ONLY
	}

	public enum FileFormat {
		CSV, JSONL
	}

	public enum TemporalPolicy {
		WARN, ERROR
	}

	/**
	 * Saved file-transport intent, not a claim that export/import is implemented.
	 * CSV exporters must reject literal null-token collisions rather than lose
	 * data.
	 */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record FileTransfer(String directory, FileFormat format, String encoding, String nullValue,
			TemporalPolicy unsupportedTemporalPolicy) {
		public FileTransfer(String directory, FileFormat format, String encoding, String nullValue) {
			this(directory, format, encoding, nullValue, TemporalPolicy.WARN);
		}

		public FileTransfer {
			unsupportedTemporalPolicy = unsupportedTemporalPolicy == null ? TemporalPolicy.WARN
					: unsupportedTemporalPolicy;
			if (directory == null || directory.isBlank())
				throw new IllegalArgumentException("files.directory is required for file transport.");
			format = format == null ? FileFormat.CSV : format;
			encoding = encoding == null ? "UTF-8" : encoding;
			final java.nio.charset.Charset charset = java.nio.charset.Charset.forName(encoding);
			encoding = charset.name();
			if (format == FileFormat.CSV) {
				if (nullValue == null || nullValue.isBlank()
						|| nullValue.chars().anyMatch(c -> c == ',' || c == '"' || c == '\r' || c == '\n'))
					throw new IllegalArgumentException(
							"CSV files require an explicit nonblank nullValue without commas, quotes or line breaks.");
				if (!charset.newEncoder().canEncode(nullValue))
					throw new IllegalArgumentException("CSV nullValue must be representable in files.encoding.");
			} else if (nullValue != null) {
				throw new IllegalArgumentException("JSONL uses JSON null; omit files.nullValue.");
			}
		}
	}

	/** Policy for target preparation; destructive replacement is not supported. */
	public enum SchemaMode {
		CREATE_MISSING, REQUIRE_EXISTING
	}

	/** Common initial-copy intent with verification and default chunk sizing. */
	public TransferJobConfiguration(String source, String target, List<String> tables, SchemaMode schemaMode) {
		this(1, source, target, tables, schemaMode, null, null, null, null, null);
	}

	/** Retains the original direct-JDBC construction API and defaults. */
	public TransferJobConfiguration(int formatVersion, String source, String target, List<String> tables,
			SchemaMode schemaMode, String sourceSchema, String targetSchema, String mappingFile, Integer chunkSize,
			Boolean verify) {
		this(formatVersion, source, target, tables, schemaMode, sourceSchema, targetSchema, mappingFile, chunkSize,
				verify, null, null);
	}

	public TransferJobConfiguration {
		compatibilityPolicy = compatibilityPolicy == null ? CompatibilityPolicy.WARN : compatibilityPolicy;
		if (formatVersion != 1)
			throw new IllegalArgumentException("Transfer job formatVersion must be 1.");
		source = connectionName(source, "source");
		target = connectionName(target, "target");
		if (tables == null || tables.isEmpty() || tables.stream().anyMatch(value -> value == null || value.isBlank()))
			throw new IllegalArgumentException("Select at least one source table with a nonblank identity.");
		tables = List.copyOf(tables);
		if (tables.stream().distinct().count() != tables.size())
			throw new IllegalArgumentException("Select each source table only once.");
		if (schemaMode == null)
			throw new IllegalArgumentException("Choose schemaMode: CREATE_MISSING or REQUIRE_EXISTING.");
		if ((sourceSchema != null && sourceSchema.isBlank()) || (targetSchema != null && targetSchema.isBlank()))
			throw new IllegalArgumentException("Omit unspecified schema names rather than using blank names.");
		if (mappingFile != null && mappingFile.isBlank())
			throw new IllegalArgumentException("Omit mappingFile when no explicit mapping is needed.");
		chunkSize = chunkSize == null ? 10_000 : chunkSize;
		if (chunkSize < 1 || chunkSize > 1_000_000)
			throw new IllegalArgumentException("chunkSize must be between 1 and 1000000.");
		dataMode = dataMode == null ? DataMode.JDBC_DIRECT : dataMode;
		verify = verify == null ? dataMode != DataMode.SCHEMA_ONLY : verify;
		if (dataMode == DataMode.SCHEMA_ONLY && (schemaMode != SchemaMode.CREATE_MISSING || verify || files != null))
			throw new IllegalArgumentException(
					"SCHEMA_ONLY requires CREATE_MISSING, verify: false and no files settings.");
		if (dataMode == DataMode.FILE && files == null)
			throw new IllegalArgumentException("FILE dataMode requires files settings.");
		if (dataMode == DataMode.JDBC_DIRECT && files != null)
			throw new IllegalArgumentException(
					"files settings require FILE dataMode; they cannot be ignored in JDBC_DIRECT mode.");
	}

	private static String connectionName(String value, String field) {
		if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}"))
			throw new IllegalArgumentException(
					field + " must name a runtime connection, not a JDBC URL or credentials.");
		return value;
	}
}
