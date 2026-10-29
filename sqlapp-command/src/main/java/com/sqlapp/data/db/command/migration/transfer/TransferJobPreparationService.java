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
import com.sqlapp.util.YamlConverter;

/**
 * Shared read-only preparation of a direct copy and optional missing-table DDL.
 * Owns borrowed JDBC connections, never the caller's DataSources. Preparation
 * does not approve or execute a copy and does not create database objects.
 */
public final class TransferJobPreparationService {
	/**
	 * dataJob is null for SCHEMA_ONLY; schemaPreparation retains the planned Schema
	 * SQL.
	 */
	public record Prepared(Path directory, TransferJobConfigurationIO.Snapshot job, Path sourceSchemaFile,
			BulkMigrationJobConfigurationEditorIO.Snapshot dataJob, String planFingerprint, List<String> taskIds,
			List<String> warnings, TransferSchemaPreparation.Plan schemaPreparation) {
		public Prepared(Path directory, TransferJobConfigurationIO.Snapshot job, Path sourceSchemaFile,
				BulkMigrationJobConfigurationEditorIO.Snapshot dataJob, String planFingerprint, List<String> taskIds,
				List<String> warnings) {
			this(directory, job, sourceSchemaFile, dataJob, planFingerprint, taskIds, warnings,
					new TransferSchemaPreparation.Plan(planFingerprint, List.of(), warnings));
		}

		public Prepared(Path directory, TransferJobConfigurationIO.Snapshot job, Path sourceSchemaFile,
				BulkMigrationJobConfigurationEditorIO.Snapshot dataJob, String planFingerprint, List<String> taskIds) {
			this(directory, job, sourceSchemaFile, dataJob, planFingerprint, taskIds, List.of());
		}

		public Prepared {
			taskIds = List.copyOf(taskIds);
			warnings = List.copyOf(warnings);
			Objects.requireNonNull(schemaPreparation, "schemaPreparation");
		}
	}

	/**
	 * Resolve both logical connections before borrowing either one, capture source
	 * metadata, and use the existing bulk planner/target validator. Output is
	 * placed in a new child of workspace; existing artifacts are never overwritten.
	 */
	public Prepared prepare(Path jobFile, Map<String, ? extends DataSource> connections, Path workspace)
			throws IOException {
		return prepare(jobFile, connections, workspace, null);
	}

	Prepared prepare(Path jobFile, Map<String, ? extends DataSource> connections, Path workspace,
			java.util.function.Consumer<String> draft) throws IOException {
		final var io = new TransferJobConfigurationIO();
		final var snapshot = io.read(jobFile);
		final var configuration = snapshot.configuration();
		if (configuration.dataMode() == TransferJobConfiguration.DataMode.FILE)
			throw new IllegalArgumentException("FILE transport requires separate export/import execution, which is not "
					+ "implemented in the direct-JDBC preparation path. No file export or database connection was started.");
		final var mapping = configuration.mappingFile() == null ? null
				: new TransferNameMappingIO().read(snapshot.mappingFile());
		Objects.requireNonNull(connections, "connections");
		final DataSource source = connections.get(configuration.source());
		final DataSource target = connections.get(configuration.target());
		if (source == null || target == null)
			throw new IllegalArgumentException("Supply runtime connections named " + configuration.source() + " and "
					+ configuration.target() + " before preparing this transfer job.");
		if (workspace == null || !Files.isDirectory(workspace) || !Files.isWritable(workspace))
			throw new IllegalArgumentException("Choose an existing writable workspace for transfer artifacts.");

		String phase = "opening runtime connections";
		try (var from = openConnection(source, "opening source connection");
				var to = openConnection(target, "opening target connection")) {
			phase = "configuring read-only source and target connections";
			from.setReadOnly(true);
			to.setReadOnly(true);
			phase = "checking supported database products";
			final Dialect sourceDialect = dialect(from);
			final Dialect targetDialect = dialect(to);
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
			if (configuration.dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY) {
				phase = "planning schema-only target table creation";
				final var prepared = TransferSchemaOnlyPreparation.prepare(to, schema, snapshot, directory, xml,
						targetCatalog, targetSchema, mapping);
				return withDraft(prepared, targetDialect, draft);
			}
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
				// Initial-copy jobs preserve source values, including identity keys used by
				// FKs.
				task.put("bulk", Map.of("keepIdentity", true, "keepNulls", true));
				tasks.add(task);
				tasksById.put((String) task.get("id"), task);
			}
			definition.put("tasks", tasks);
			definition.put("verification", Map.of("enabled", configuration.verify()));
			final var bulkIO = new BulkMigrationJobConfigurationEditorIO();
			phase = "saving the generated bulk configuration";
			var bulk = bulkIO.write(directory.resolve("data-job.yaml"),
					TransferNameMappingIO.pinDataJob(new YamlConverter().toJsonString(definition), mapping), null);
			final var resolver = new BulkMigrationJobConfigurationResolver();
			phase = "resolving selected source tables and NOT NULL keys in the generated bulk configuration";
			final var selected = resolver.resolveJob(bulk.file().toFile(), from, bulk.fingerprint());
			phase = "projecting selected source names for transfer";
			final var projection = mapping == null ? null
					: TransferNameMappingProjection.project(schema,
							selected.plan().getTasks().stream().map(task -> task.getEffectiveSourceTable()).toList(),
							mapping.configuration());
			final var identities = new HashSet<String>();
			for (final var selectedTask : selected.plan().getTasks()) {
				final Table table = selectedTask.getEffectiveSourceTable();
				final String identity = identity(table.getCatalogName(), table.getSchemaName(), table.getName());
				if (!identities.add(identity))
					throw new PreparationException(
							"Multiple selections resolve to the same source table. Select each table once.");
				final var task = Objects.requireNonNull(tasksById.get(selectedTask.getTaskId()));
				task.put("table", identity);
				final var mapped = projection == null ? table : projection.bySourceName().get(table.getName());
				task.put("targetTable", identity(targetCatalog, targetSchema, mapped.getName()));
				if (projection != null) {
					final var columns = new LinkedHashMap<String, String>();
					for (int column = 0; column < table.getColumns().size(); column++) {
						String sourceName = table.getColumns().get(column).getName();
						String targetName = mapped.getColumns().get(column).getName();
						if (!sourceName.equals(targetName))
							columns.put(sourceName, targetName);
					}
					task.put("columnMappings", columns);
				}
			}
			bulk = bulkIO.write(bulk.file(),
					TransferNameMappingIO.pinDataJob(new YamlConverter().toJsonString(definition), mapping),
					bulk.fingerprint());
			phase = "resolving target table identities in the generated bulk configuration";
			final var resolved = resolver.resolveJob(bulk.file().toFile(), from, bulk.fingerprint());
			phase = "validating target tables, columns, primary keys and empty-target conditions";
			final var compatibility = TransferSchemaPreparation.plan(to, schema, resolved.plan(),
					configuration.schemaMode(), configuration.compatibilityPolicy(), projection);
			phase = "checking that the transfer definition has not changed";
			if (!io.read(jobFile).fingerprint().equals(snapshot.fingerprint()))
				throw new PreparationException("Transfer definition changed during preparation. Prepare it again.");
			TransferNameMappingIO.capture(mapping, directory);
			final var savedJob = io.write(directory.resolve("transfer.yaml"), snapshot.text(), null);
			return withDraft(
					new Prepared(directory, savedJob, xml, bulk, resolved.plan().getFingerprint(),
							resolved.plan().getTaskIds(), compatibility.warnings(), compatibility),
					targetDialect, draft);
		} catch (TransferSchemaPreparation.ValidationException failure) {
			throw failure;
		} catch (PreparationException failure) {
			throw new PreparationException(failure);
		} catch (Exception failure) {
			throw new PreparationException(phase, failure);
		}
	}

	private static Prepared withDraft(Prepared prepared, Dialect target, java.util.function.Consumer<String> draft) {
		if (draft != null)
			draft.accept(
					TransferSchemaSqlScript.render(prepared.schemaPreparation(), target, prepared.job().fingerprint()));
		return prepared;
	}

	private static Connection openConnection(DataSource source, String phase) throws java.sql.SQLException {
		try {
			return source.getConnection();
		} catch (java.sql.SQLException | RuntimeException failure) {
			throw new PreparationException(phase, failure);
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
	 * Structured stage/SQL codes and selected library validation text. Raw driver
	 * messages, causes and suppressed cleanup exceptions are not retained.
	 */
	public static final class PreparationException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
		private final String phase;
		private final String exceptionType;
		private final String sqlState;
		private final Integer vendorCode;
		private final String validationDetail;

		private PreparationException(String message) {
			super(message);
			phase = "checking transfer settings";
			exceptionType = "Validation";
			sqlState = null;
			vendorCode = null;
			validationDetail = message;
		}

		private PreparationException(PreparationException original) {
			super(original.getMessage());
			phase = original.phase;
			exceptionType = original.exceptionType;
			sqlState = original.sqlState;
			vendorCode = original.vendorCode;
			validationDetail = original.validationDetail;
		}

		private PreparationException(String phase, Throwable failure) {
			this(phase, failure.getClass().getSimpleName(), sql(failure), validation(failure));
		}

		private PreparationException(String phase, String type, java.sql.SQLException sql, String detail) {
			super("Transfer preparation failed while " + phase + " (" + type + "). "
					+ (detail == null ? "Check connection availability and the selected schemas/tables. "
							: detail + " ")
					+ (sql == null ? ""
							: "SQLState: " + (state(sql) == null ? "not provided" : state(sql)) + "; vendor code: "
									+ sql.getErrorCode() + ". ")
					+ "No copy or database DDL was executed.");
			this.phase = phase;
			exceptionType = type;
			sqlState = sql == null ? null : state(sql);
			vendorCode = sql == null ? null : sql.getErrorCode();
			validationDetail = detail;
		}

		public String phase() {
			return phase;
		}

		public String exceptionType() {
			return exceptionType;
		}

		public String sqlState() {
			return sqlState;
		}

		public Integer vendorCode() {
			return vendorCode;
		}

		public String validationDetail() {
			return validationDetail;
		}

		private static String state(java.sql.SQLException sql) {
			return sql.getSQLState() != null && sql.getSQLState().matches("[A-Za-z0-9]{5}") ? sql.getSQLState() : null;
		}

		private static java.sql.SQLException sql(Throwable failure) {
			final var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
			final var pending = new java.util.ArrayDeque<Throwable>();
			pending.add(failure);
			java.sql.SQLException fallback = null;
			while (!pending.isEmpty() && seen.size() < 64) {
				final var current = pending.removeFirst();
				if (!seen.add(current))
					continue;
				if (current instanceof java.sql.SQLException sql) {
					if (state(sql) != null)
						return sql;
					if (fallback == null)
						fallback = sql;
					if (sql.getNextException() != null)
						pending.add(sql.getNextException());
				}
				if (current.getCause() != null)
					pending.add(current.getCause());
			}
			return fallback;
		}

		private static String validation(Throwable failure) {
			final var frames = failure.getStackTrace();
			if (frames.length == 0)
				return null;
			final String owner = frames[0].getClassName();
			if (!owner.equals("com.sqlapp.jdbc.bulk.BulkMigrationTargetValidator")
					&& !owner.equals("com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationResolver"))
				return null;
			final String message = failure.getMessage();
			if (message == null)
				return null;
			final String safe = message.replaceAll("(?i)jdbc:[^\\s]*", "[JDBC URL]").replaceAll("\\p{Cntrl}", " ");
			return safe.length() > 2048 ? safe.substring(0, 2048) + "…" : safe;
		}
	}
}
