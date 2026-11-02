/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.util.MessageDigests;

/** Offline authoring over the same Schema projection as transfer execution. */
public final class TransferNameMappingInspectionService {
	public record Column(String source, String target, String sourceType, boolean primaryKey, boolean nullable,
			String targetDefinition, String previewIssue) {
		public Column(String source, String target, String sourceType, boolean primaryKey, boolean nullable) {
			this(source, target, sourceType, primaryKey, nullable, null, null);
		}
	}

	public record Table(String source, String target, List<Column> columns) {
		public Table {
			columns = List.copyOf(columns);
		}
	}

	public record Result(Path schemaFile, String schemaFingerprint, String mappingFingerprint, List<Table> tables,
			List<String> warnings, String targetDialect) {
		public Result(Path schemaFile, String schemaFingerprint, String mappingFingerprint, List<Table> tables,
				List<String> warnings) { this(schemaFile, schemaFingerprint, mappingFingerprint, tables, warnings, null); }
		public Result {
			schemaFile = schemaFile.toAbsolutePath().normalize();
			tables = List.copyOf(tables);
			warnings = List.copyOf(warnings);
		}
	}

	/**
	 * Common offline entry point; captures the Schema before resolving mappings.
	 */
	public Result inspect(Path schemaFile, List<String> selected, TransferNameMappingIO.Configuration mapping)
			throws Exception {
		return inspect(new BulkMigrationJobDraftBuilder().inspect(Objects.requireNonNull(schemaFile, "schemaFile")),
				selected, mapping);
	}

	/**
	 * Rechecks bounded metadata-only Schema bytes and selected identities; no rows,
	 * connections, DDL or output files. The result is not execution authorization.
	 */
	public Result inspect(BulkMigrationJobDraftBuilder.SchemaSnapshot snapshot, List<String> selected,
			TransferNameMappingIO.Configuration mapping) throws Exception {
		return inspect(snapshot, selected, mapping, null);
	}

	/** Optional offline target-builder preview, not live target metadata or an execution plan. */
	public Result inspect(BulkMigrationJobDraftBuilder.SchemaSnapshot snapshot, List<String> selected,
			TransferNameMappingIO.Configuration mapping, com.sqlapp.data.db.dialect.Dialect targetDialect) throws Exception {
		Objects.requireNonNull(snapshot, "snapshot");
		Objects.requireNonNull(mapping, "mapping");
		if (selected == null || selected.isEmpty()
				|| selected.stream().anyMatch(name -> name == null || name.isBlank()))
			throw new IllegalArgumentException("Select at least one source table with a nonblank identity.");
		var io = new TransferNameMappingIO();
		String text = io.format(mapping);
		io.validate(text);
		var schema = TransferFileJobDraftBuilder.schema(snapshot);
		var sources = TransferSchemaOnlyPreparation.select(schema, selected);
		var projection = TransferNameMappingProjection.project(schema, sources, mapping);
		var tables = new ArrayList<Table>();
		for (var source : sources) {
			var target = projection.bySourceName().get(source.getName());
			var key = source.getPrimaryKeyConstraint();
			var keys = key == null ? Set.<String>of()
					: key.getColumns().stream().map(column -> column.getName())
							.collect(java.util.stream.Collectors.toSet());
			var columns = new ArrayList<Column>();
			for (int index = 0; index < source.getColumns().size(); index++) {
				var column = source.getColumns().get(index);
				String definition = null, issue = null;
				if (targetDialect != null) {
					try {
						definition = targetDialect.createSqlBuilder().definition(target.getColumns().get(index).clone(), false).toString();
						if (definition.isBlank()) throw new IllegalArgumentException("Empty target definition preview.");
					} catch (IllegalArgumentException | UnsupportedOperationException failure) {
						definition = null;
						issue = "Target builder preview unavailable (" + failure.getClass().getSimpleName()
								+ "). Inspect the full Schema SQL and compatibility warnings during database Review.";
					}
				}
				columns.add(new Column(column.getName(), target.getColumns().get(index).getName(),
						Objects.toString(column.getDataType(), "Unknown"), keys.contains(column.getName()),
						!column.isNotNull(), definition, issue));
			}
			tables.add(new Table(TransferSchemaOnlyPreparation.identity(source.getCatalogName(), source.getSchemaName(),
					source.getName()), target.getName(), columns));
		}
		var warnings = new ArrayList<String>();
		if (projection.renamedIdentifiers())
			TransferSchemaPreparation.mappingExpressions(TransferJobConfiguration.CompatibilityPolicy.WARN, warnings);
		return new Result(snapshot.file(), snapshot.fingerprint(),
				"sha256:" + MessageDigests.SHA256.checksumAsString(text.getBytes(StandardCharsets.UTF_8)), tables,
				warnings, targetDialect == null ? null : targetDialect.getClass().getName());
	}
}
