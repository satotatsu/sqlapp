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
		final var schema = TransferFileJobDraftBuilder.schema(snapshot);
		final var tables = TransferSchemaOnlyPreparation.select(schema, selected).stream()
				.map(table -> TransferSchemaOnlyPreparation.identity(schema.getCatalogName(), schema.getName(),
						table.getName()))
				.toList();
		final var configuration = new TransferJobConfiguration(1, "source", "target", tables, mode, schema.getName(),
				targetSchema, null, null, false, TransferJobConfiguration.DataMode.SCHEMA_ONLY, null, policy,
				allowDestructiveSchemaChanges);
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
		final var draft = new BulkMigrationJobConfigurationEditorIO()
				.validate(new BulkMigrationJobDraftBuilder().draft(schema, selected, "", chunkSize, verify));
		final var canonical = TransferFileJobDraftBuilder.schema(schema);
		final var job = new TransferJobConfiguration(1, "source", "target",
				draft.getTasks().stream().map(task -> task.getTable()).toList(), schemaMode, canonical.getName(),
				targetSchema, null, chunkSize, verify, TransferJobConfiguration.DataMode.JDBC_DIRECT, null, policy);
		final var io = new TransferJobConfigurationIO();
		return io.write(file, io.format(job), null);
	}
}
