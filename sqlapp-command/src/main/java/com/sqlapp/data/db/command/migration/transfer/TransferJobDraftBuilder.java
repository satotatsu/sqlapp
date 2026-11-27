/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.file.Path;
import java.util.List;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;

/**
 * Offline direct-job authoring; shared bulk authoring validates selection,
 * fingerprint and dependency order.
 */
public final class TransferJobDraftBuilder {
	private final String sourceConnection;
	private final String targetConnection;

	public TransferJobDraftBuilder() {
		this("source", "target");
	}

	private TransferJobDraftBuilder(String sourceConnection, String targetConnection) {
		this.sourceConnection = sourceConnection;
		this.targetConnection = targetConnection;
	}

	/**
	 * A builder that writes these runtime connection names into new jobs instead of
	 * {@code source} / {@code target}, for example the names of saved connection
	 * settings. Equal names bind both sides to one connection.
	 */
	public TransferJobDraftBuilder withConnectionNames(String source, String target) {
		// Validate early with the job's own rules.
		new TransferJobConfiguration(source, target, List.of("X"),
				TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING);
		return new TransferJobDraftBuilder(source, target);
	}

	/**
	 * Resolve a captured selection without creating a job or opening connections.
	 */
	public List<String> resolveTables(BulkMigrationJobDraftBuilder.SchemaSnapshot snapshot, List<String> selected)
			throws Exception {
		return resolveTables(snapshot, selected, TransferJobConfiguration.DataMode.JDBC_DIRECT);
	}

	public List<String> resolveTables(BulkMigrationJobDraftBuilder.SchemaSnapshot snapshot, List<String> selected,
			TransferJobConfiguration.DataMode mode) throws Exception {
		if (selected == null || selected.isEmpty()
				|| selected.stream().anyMatch(name -> name == null || name.isBlank()))
			throw new IllegalArgumentException("Select at least one table with a nonblank source identity.");
		var schema = TransferFileJobDraftBuilder.schema(snapshot);
		var sourceTables = List.copyOf(schema.getTables());
		var names = mode == TransferJobConfiguration.DataMode.SCHEMA_ONLY ? selected
				: selected.stream()
						.map(name -> com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationResolver
								.resolveTable(sourceTables, name))
						.map(table -> TransferSchemaOnlyPreparation.identity(schema.getCatalogName(), schema.getName(),
								table.getName()))
						.toList();
		return TransferSchemaOnlyPreparation.select(schema, names).stream()
				.map(table -> schema.getName() == null || schema.getName().isBlank() ? table.getName()
						: TransferSchemaOnlyPreparation.identity(schema.getCatalogName(), schema.getName(),
								table.getName()))
				.toList();
	}

	/** Save schema-only intent without requiring data-copy keys or reading rows. */
	public TransferJobConfigurationIO.Snapshot saveSchemaOnly(BulkMigrationJobDraftBuilder.SchemaSnapshot snapshot,
			List<String> selected, TransferJobConfiguration.CompatibilityPolicy policy, String targetSchema, Path file)
			throws Exception {
		return saveSchemaOnly(snapshot, selected, policy, targetSchema,
				TransferJobConfiguration.SchemaMode.CREATE_MISSING, false, file);
	}

	public TransferJobConfigurationIO.Snapshot saveSchemaOnly(BulkMigrationJobDraftBuilder.SchemaSnapshot snapshot,
			List<String> selected, TransferJobConfiguration.CompatibilityPolicy policy, String targetSchema,
			TransferJobConfiguration.SchemaMode mode, boolean allowDestructiveSchemaChanges, Path file)
			throws Exception {
		return saveSchemaOnly(snapshot, selected, policy, targetSchema, mode, allowDestructiveSchemaChanges, file,
				null);
	}

	public TransferJobConfigurationIO.Snapshot saveSchemaOnly(BulkMigrationJobDraftBuilder.SchemaSnapshot snapshot,
			List<String> selected, TransferJobConfiguration.CompatibilityPolicy policy, String targetSchema,
			TransferJobConfiguration.SchemaMode mode, boolean allowDestructiveSchemaChanges, Path file,
			String generatedKeyColumn) throws Exception {
		final var schema = TransferFileJobDraftBuilder.schema(snapshot);
		final var tables = TransferSchemaOnlyPreparation.select(schema, selected).stream()
				.map(table -> TransferSchemaOnlyPreparation.identity(schema.getCatalogName(), schema.getName(),
						table.getName()))
				.toList();
		final var configuration = new TransferJobConfiguration(1, sourceConnection, targetConnection, tables, mode,
				sourceSchema(schema.getName()), targetSchema, null, null, false,
				TransferJobConfiguration.DataMode.SCHEMA_ONLY, null, policy, allowDestructiveSchemaChanges,
				generatedKeyColumn);
		final var io = new TransferJobConfigurationIO();
		return io.write(file, io.format(configuration), null);
	}

	public TransferJobConfigurationIO.Snapshot saveDirect(BulkMigrationJobDraftBuilder.SchemaSnapshot schema,
			List<String> selected, int chunkSize, boolean verify, TransferJobConfiguration.CompatibilityPolicy policy,
			String targetSchema, Path file) throws Exception {
		return saveDirect(schema, selected, chunkSize, verify, policy, targetSchema,
				TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING, file);
	}

	public TransferJobConfigurationIO.Snapshot saveDirect(BulkMigrationJobDraftBuilder.SchemaSnapshot schema,
			List<String> selected, int chunkSize, boolean verify, TransferJobConfiguration.CompatibilityPolicy policy,
			String targetSchema, TransferJobConfiguration.SchemaMode schemaMode, Path file) throws Exception {
		return saveDirect(schema, selected, chunkSize, verify, policy, targetSchema, schemaMode, file, null);
	}

	public TransferJobConfigurationIO.Snapshot saveDirect(BulkMigrationJobDraftBuilder.SchemaSnapshot schema,
			List<String> selected, int chunkSize, boolean verify, TransferJobConfiguration.CompatibilityPolicy policy,
			String targetSchema, TransferJobConfiguration.SchemaMode schemaMode, Path file, String generatedKeyColumn)
			throws Exception {
		final var draft = new BulkMigrationJobConfigurationEditorIO()
				.validate(new BulkMigrationJobDraftBuilder().draftInsert(schema, selected, "", chunkSize, verify));
		final var canonical = TransferFileJobDraftBuilder.schema(schema);
		final var job = new TransferJobConfiguration(1, sourceConnection, targetConnection,
				draft.getTasks().stream().map(task -> task.getTable()).toList(), schemaMode,
				sourceSchema(canonical.getName()), targetSchema, null, chunkSize, verify,
				TransferJobConfiguration.DataMode.JDBC_DIRECT, null, policy, false, generatedKeyColumn);
		final var io = new TransferJobConfigurationIO();
		return io.write(file, io.format(job), null);
	}

	private static String sourceSchema(String name) {
		return name == null || name.isBlank() ? null : name;
	}
}
