/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationResolver;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder.SchemaSnapshot;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.MessageDigests;

/**
 * Offline authoring of the common CSV/TSV/JSONL initial-copy job using shared
 * eligibility and ordering.
 */
public final class TransferFileJobDraftBuilder {
	private final String sourceConnection;
	private final String targetConnection;

	public TransferFileJobDraftBuilder() {
		this("source", "target");
	}

	private TransferFileJobDraftBuilder(String sourceConnection, String targetConnection) {
		this.sourceConnection = sourceConnection;
		this.targetConnection = targetConnection;
	}

	/**
	 * A builder that writes these runtime connection names into new jobs instead of
	 * {@code source} / {@code target}, for example the names of saved connection
	 * settings. Equal names bind both sides to one connection.
	 */
	public TransferFileJobDraftBuilder withConnectionNames(String source, String target) {
		// Validate early with the job's own rules.
		new TransferJobConfiguration(source, target, List.of("X"),
				TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING);
		return new TransferFileJobDraftBuilder(source, target);
	}

	public SchemaSnapshot inspect(SchemaSnapshot reviewed) throws Exception {
		final var schema = schema(reviewed);
		final var choices = new ArrayList<BulkMigrationJobDraftBuilder.TableChoice>();
		for (final var choice : reviewed.tables()) {
			String restriction = choice.insertSelectable() ? null : choice.restriction();
			if (restriction == null) {
				try {
					supported(schema, choice.table());
				} catch (IllegalArgumentException failure) {
					restriction = failure.getMessage();
				}
			}
			choices.add(new BulkMigrationJobDraftBuilder.TableChoice(choice.table(), choice.primaryKey(), restriction));
		}
		return new SchemaSnapshot(reviewed.file(), reviewed.fingerprint(), choices);
	}

	public TransferJobConfigurationIO.Snapshot save(SchemaSnapshot reviewed, List<String> selected, int chunkSize,
			boolean verify, String nullValue, Path file) throws Exception {
		return save(reviewed, selected, chunkSize, verify, nullValue,
				TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING, file);
	}

	public TransferJobConfigurationIO.Snapshot save(SchemaSnapshot reviewed, List<String> selected, int chunkSize,
			boolean verify, String nullValue, TransferJobConfiguration.SchemaMode schemaMode, Path file)
			throws Exception {
		return save(reviewed, selected, chunkSize, verify, nullValue, schemaMode,
				TransferJobConfiguration.TemporalPolicy.WARN, file);
	}

	public TransferJobConfigurationIO.Snapshot save(SchemaSnapshot reviewed, List<String> selected, int chunkSize,
			boolean verify, String nullValue, TransferJobConfiguration.SchemaMode schemaMode,
			TransferJobConfiguration.TemporalPolicy policy, Path file) throws Exception {
		return save(reviewed, selected, chunkSize, verify, nullValue, schemaMode, policy,
				TransferJobConfiguration.CompatibilityPolicy.WARN, file);
	}

	public TransferJobConfigurationIO.Snapshot save(SchemaSnapshot reviewed, List<String> selected, int chunkSize,
			boolean verify, String nullValue, TransferJobConfiguration.SchemaMode schemaMode,
			TransferJobConfiguration.TemporalPolicy policy,
			TransferJobConfiguration.CompatibilityPolicy compatibilityPolicy, Path file) throws Exception {
		return save(reviewed, selected, chunkSize, verify, nullValue, schemaMode, policy, compatibilityPolicy, null,
				file);
	}

	/**
	 * Explicit target namespace; null retains the target connection's current
	 * schema.
	 */
	public TransferJobConfigurationIO.Snapshot save(SchemaSnapshot reviewed, List<String> selected, int chunkSize,
			boolean verify, String nullValue, TransferJobConfiguration.SchemaMode schemaMode,
			TransferJobConfiguration.TemporalPolicy policy,
			TransferJobConfiguration.CompatibilityPolicy compatibilityPolicy, String targetSchema, Path file)
			throws Exception {
		return save(reviewed, selected, chunkSize, verify, nullValue, schemaMode, policy, compatibilityPolicy,
				targetSchema, TransferJobConfiguration.FileFormat.CSV, file);
	}

	/**
	 * Shared file format selection; CSV overloads retain their existing defaults.
	 */
	public TransferJobConfigurationIO.Snapshot save(SchemaSnapshot reviewed, List<String> selected, int chunkSize,
			boolean verify, String nullValue, TransferJobConfiguration.SchemaMode schemaMode,
			TransferJobConfiguration.TemporalPolicy policy,
			TransferJobConfiguration.CompatibilityPolicy compatibilityPolicy, String targetSchema,
			TransferJobConfiguration.FileFormat format, Path file) throws Exception {
		return save(reviewed, selected, chunkSize, verify, schemaMode, compatibilityPolicy, targetSchema,
				new TransferJobConfiguration.FileTransfer("packages", format, "UTF-8", nullValue, policy), file);
	}

	/**
	 * File settings shared by GUI and Gradle; existing overloads retain UTF-8
	 * defaults.
	 */
	public TransferJobConfigurationIO.Snapshot save(SchemaSnapshot reviewed, List<String> selected, int chunkSize,
			boolean verify, TransferJobConfiguration.SchemaMode schemaMode,
			TransferJobConfiguration.CompatibilityPolicy compatibilityPolicy, String targetSchema,
			TransferJobConfiguration.FileTransfer files, Path file) throws Exception {
		return save(reviewed, selected, chunkSize, verify, schemaMode, compatibilityPolicy, targetSchema, files, null,
				file);
	}

	/**
	 * Optional target-only key name; the shared Schema planner validates creation.
	 */
	public TransferJobConfigurationIO.Snapshot save(SchemaSnapshot reviewed, List<String> selected, int chunkSize,
			boolean verify, TransferJobConfiguration.SchemaMode schemaMode,
			TransferJobConfiguration.CompatibilityPolicy compatibilityPolicy, String targetSchema,
			TransferJobConfiguration.FileTransfer files, String generatedKeyColumn, Path file) throws Exception {
		java.util.Objects.requireNonNull(files, "file settings");
		final String draft = new BulkMigrationJobDraftBuilder().draftInsert(reviewed, selected, "", chunkSize, verify);
		final var bulk = new BulkMigrationJobConfigurationEditorIO().validate(draft);
		final var schema = schema(reviewed);
		final var tables = bulk.getTasks().stream().map(task -> task.getTable()).toList();
		for (String table : tables)
			supported(schema, table, files.unsupportedTemporalPolicy());
		final var configuration = new TransferJobConfiguration(1, sourceConnection, targetConnection, tables,
				schemaMode, schema.getName() == null || schema.getName().isBlank() ? null : schema.getName(),
				targetSchema, null, chunkSize, verify, TransferJobConfiguration.DataMode.FILE, files,
				compatibilityPolicy, false, generatedKeyColumn);
		final var io = new TransferJobConfigurationIO();
		return io.write(file, io.format(configuration), null);
	}

	static Schema schema(SchemaSnapshot reviewed) throws Exception {
		final byte[] bytes = BoundedMigrationFile.read(reviewed.file(), 16_777_216L, "maxSchemaBytes", "Schema");
		if (!("sha256:" + MessageDigests.SHA256.checksumAsString(bytes)).equals(reviewed.fingerprint())
				|| !new BulkMigrationJobDraftBuilder().inspect(reviewed.file()).fingerprint()
						.equals(reviewed.fingerprint()))
			throw new IllegalArgumentException(
					"Source Schema changed. Read the source again before using the captured Schema.");
		final var root = SchemaUtils.readXml(new java.io.ByteArrayInputStream(bytes));
		if (!(root instanceof Schema schema))
			throw new IllegalArgumentException("A captured source Schema is required.");
		return schema;
	}

	private static void supported(Schema schema, String name) {
		supported(schema, name, TransferJobConfiguration.TemporalPolicy.WARN);
	}

	private static void supported(Schema schema, String name, TransferJobConfiguration.TemporalPolicy policy) {
		final var table = BulkMigrationJobConfigurationResolver.resolveTable(List.copyOf(schema.getTables()), name);
		TransferCsvSource.validateColumnTypes(TransferCsvTemporalCodec.exportView(table, policy));
	}
}
