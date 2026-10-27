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
		Boolean verify) {

	/** Policy for target preparation; destructive replacement is not supported. */
	public enum SchemaMode {
		CREATE_MISSING, REQUIRE_EXISTING
	}

	/** Common initial-copy intent with verification and default chunk sizing. */
	public TransferJobConfiguration(String source, String target, List<String> tables, SchemaMode schemaMode) {
		this(1, source, target, tables, schemaMode, null, null, null, null, null);
	}

	public TransferJobConfiguration {
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
		verify = verify == null ? Boolean.TRUE : verify;
	}

	private static String connectionName(String value, String field) {
		if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}"))
			throw new IllegalArgumentException(
					field + " must name a runtime connection, not a JDBC URL or credentials.");
		return value;
	}
}
