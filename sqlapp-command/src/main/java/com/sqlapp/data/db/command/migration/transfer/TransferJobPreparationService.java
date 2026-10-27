/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationResolver;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationTargetValidator;
import com.sqlapp.util.YamlConverter;

/**
 * Shared read-only preparation of an initial copy into existing target tables.
 * Owns borrowed JDBC connections, never the caller's DataSources. Preparation
 * does not approve or execute a copy and does not create database objects.
 */
public final class TransferJobPreparationService {
	public record Prepared(Path directory, TransferJobConfigurationIO.Snapshot job, Path sourceSchemaFile,
			BulkMigrationJobConfigurationEditorIO.Snapshot dataJob, String planFingerprint, List<String> taskIds) {
		public Prepared {
			taskIds = List.copyOf(taskIds);
		}
	}

	/**
	 * Resolve both logical connections before borrowing either one, capture source
	 * metadata, and use the existing bulk planner/target validator. Output is
	 * placed in a new child of workspace; existing artifacts are never overwritten.
	 */
	public Prepared prepare(Path jobFile, Map<String, ? extends DataSource> connections, Path workspace)
			throws IOException {
		final var io = new TransferJobConfigurationIO();
		final var snapshot = io.read(jobFile);
		final var configuration = snapshot.configuration();
		if (configuration.dataMode() != TransferJobConfiguration.DataMode.JDBC_DIRECT)
			throw new IllegalArgumentException("FILE transport requires separate export/import execution, which is not "
					+ "implemented in the direct-JDBC preparation path. No file export or database connection was started.");
		if (configuration.schemaMode() != TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING)
			throw new IllegalArgumentException(
					"CREATE_MISSING preparation is not implemented yet. Prepare target tables "
							+ "separately and choose REQUIRE_EXISTING, or wait for target Schema preparation support.");
		if (configuration.mappingFile() != null)
			throw new IllegalArgumentException(
					"Transfer mappingFile preparation is not implemented yet; it cannot be ignored.");
		Objects.requireNonNull(connections, "connections");
		final DataSource source = connections.get(configuration.source());
		final DataSource target = connections.get(configuration.target());
		if (source == null || target == null)
			throw new IllegalArgumentException("Supply runtime connections named " + configuration.source() + " and "
					+ configuration.target() + " before preparing this transfer job.");
		if (workspace == null || !Files.isDirectory(workspace) || !Files.isWritable(workspace))
			throw new IllegalArgumentException("Choose an existing writable workspace for transfer artifacts.");

		String phase = "opening runtime connections";
		try (var from = source.getConnection(); var to = target.getConnection()) {
			from.setReadOnly(true);
			to.setReadOnly(true);
			phase = "checking supported database products";
			final Dialect sourceDialect = dialect(from);
			final Dialect targetDialect = dialect(to);
			if (!Objects.equals(from.getMetaData().getDatabaseProductName(), to.getMetaData().getDatabaseProductName()))
				throw new PreparationException("Cross-product transfer preparation requires conversion support; this "
						+ "initial preparation path currently supports the same database product only.");
			phase = "resolving source Schema metadata";
			final Schema schema = readSchema(from, sourceDialect, configuration.sourceSchema());
			final String targetSchema = configuration.targetSchema() == null
					? targetDialect.getCatalogReader().getSchemaReader().getCurrentSchemaName(to)
					: configuration.targetSchema();
			if (targetSchema == null || targetSchema.isBlank())
				throw new PreparationException(
						"Set targetSchema explicitly; the target current schema is unavailable.");
			final String targetCatalog = to.getCatalog();
			phase = "capturing source Schema and resolving selected tables";
			final Path directory = Files.createTempDirectory(workspace.toAbsolutePath().normalize(), "transfer-");
			final Path xml = directory.resolve("source-schema.xml");
			AtomicMigrationFile.write(xml, temporary -> {
				try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
					schema.writeXml(writer);
				} catch (javax.xml.stream.XMLStreamException failure) {
					throw new java.io.IOException("Unable to serialize source Schema metadata.");
				}
			});
			final var definition = new LinkedHashMap<String, Object>();
			definition.put("schemaFile", "source-schema.xml");
			definition.put("schemaFingerprint",
					BoundedMigrationFile.sha256(xml, 16_777_216L, "maxSchemaBytes", "Schema"));
			final var tasks = new ArrayList<Map<String, Object>>();
			final var tasksById = new LinkedHashMap<String, Map<String, Object>>();
			for (int index = 0; index < configuration.tables().size(); index++) {
				final var task = new LinkedHashMap<String, Object>();
				task.put("id", "transfer-table-" + (index + 1));
				task.put("table", configuration.tables().get(index));
				task.put("mode", "INSERT");
				task.put("resume", false);
				task.put("requireEmptyTarget", true);
				task.put("chunkSize", configuration.chunkSize());
				tasks.add(task);
				tasksById.put((String) task.get("id"), task);
			}
			definition.put("tasks", tasks);
			definition.put("verification", Map.of("enabled", configuration.verify()));
			final var bulkIO = new BulkMigrationJobConfigurationEditorIO();
			phase = "saving the generated bulk configuration";
			var bulk = bulkIO.write(directory.resolve("data-job.yaml"), new YamlConverter().toJsonString(definition),
					null);
			final var resolver = new BulkMigrationJobConfigurationResolver();
			phase = "resolving selected source tables and NOT NULL keys in the generated bulk configuration";
			final var selected = resolver.resolveJob(bulk.file().toFile(), from, bulk.fingerprint());
			final var identities = new HashSet<String>();
			for (final var selectedTask : selected.plan().getTasks()) {
				final Table table = selectedTask.getEffectiveSourceTable();
				final String identity = identity(table.getCatalogName(), table.getSchemaName(), table.getName());
				if (!identities.add(identity))
					throw new PreparationException(
							"Multiple selections resolve to the same source table. Select each table once.");
				final var task = Objects.requireNonNull(tasksById.get(selectedTask.getTaskId()));
				task.put("table", identity);
				task.put("targetTable", identity(targetCatalog, targetSchema, table.getName()));
			}
			bulk = bulkIO.write(bulk.file(), new YamlConverter().toJsonString(definition), bulk.fingerprint());
			phase = "resolving target table identities in the generated bulk configuration";
			final var resolved = resolver.resolveJob(bulk.file().toFile(), from, bulk.fingerprint());
			phase = "validating target tables, columns, primary keys and empty-target conditions";
			BulkMigrationTargetValidator.validate(to, resolved.plan());
			phase = "checking that the transfer definition has not changed";
			if (!io.read(jobFile).fingerprint().equals(snapshot.fingerprint()))
				throw new PreparationException("Transfer definition changed during preparation. Prepare it again.");
			final var savedJob = io.write(directory.resolve("transfer.yaml"), snapshot.text(), null);
			return new Prepared(directory, savedJob, xml, bulk, resolved.plan().getFingerprint(),
					resolved.plan().getTaskIds());
		} catch (PreparationException failure) {
			throw failure;
		} catch (Exception failure) {
			throw new IllegalArgumentException("Transfer preparation failed while " + phase + " ("
					+ failure.getClass().getSimpleName()
					+ "). Check connection availability and the selected schemas/tables. No copy or database DDL was executed.");
		}
	}

	static Dialect dialect(Connection connection) {
		final var dialect = DialectResolver.getInstance().getDialect(connection);
		if (dialect == null || dialect.getClass() == Dialect.class)
			throw new IllegalArgumentException("A supported database dialect is required.");
		return dialect;
	}

	static Schema readSchema(Connection connection, Dialect dialect, String requested) throws Exception {
		final var reader = dialect.getCatalogReader().getSchemaReader();
		String name = requested;
		if (name == null) {
			try {
				name = reader.getCurrentSchemaName(connection);
			} catch (java.sql.SQLFeatureNotSupportedException unavailable) {
				name = null;
			}
		}
		if (name != null)
			reader.setSchemaName(name);
		final String selected = name;
		final var schemas = reader.getAll(connection).stream()
				.filter(schema -> selected == null || selected.equals(schema.getName())).toList();
		if (schemas.size() != 1)
			throw new PreparationException(
					"Source schema must resolve uniquely. Set sourceSchema to an exact schema name.");
		final Schema schema = schemas.getFirst();
		final var tables = reader.getTableReader();
		tables.setCatalogName(schema.getCatalogName());
		tables.setSchemaName(schema.getName());
		tables.loadFull(connection, schema);
		SchemaUtils.validate(schema);
		return schema;
	}

	private static String identity(String catalog, String schema, String table) {
		final var parts = new ArrayList<String>();
		for (String part : new String[] { catalog, schema, table }) {
			if (part == null || part.isBlank())
				continue;
			if (part.contains("."))
				throw new PreparationException("Identifiers containing dots need explicit mapping support.");
			parts.add(part);
		}
		return String.join(".", parts);
	}

	/**
	 * Messages authored here contain no driver diagnostics or runtime credentials.
	 */
	private static final class PreparationException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;

		PreparationException(String message) {
			super(message);
		}
	}
}
