/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationResolver;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanCommand;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.*;

/**
 * Target-only review/import of a completed CSV package; never reconnects to its
 * source.
 */
public final class TransferFileImportService {
	public record Review(Path manifestFile, String manifestFingerprint, DatabaseIdentity targetIdentity,
			String planFingerprint, TransferSchemaPreparation.Plan schemaPreparation) {
		public Review(Path manifestFile, String manifestFingerprint, DatabaseIdentity targetIdentity,
				String planFingerprint) {
			this(manifestFile, manifestFingerprint, targetIdentity, planFingerprint,
					new TransferSchemaPreparation.Plan(planFingerprint, List.of()));
		}
	}

	public record Outcome(String manifestFingerprint, String planFingerprint, TransferJobExecutionService.Phase phase,
			BulkMigrationJobResult copyResult, boolean verificationRequested,
			Map<String, BulkMigrationVerificationResult> verification,
			Map<String, ChunkedBulkMigrationProgress> committedProgress, String stoppedTaskId, Path auditFile,
			TransferSchemaPreparation.Progress schemaPreparation) {
		public Outcome(String manifestFingerprint, String planFingerprint, TransferJobExecutionService.Phase phase,
				BulkMigrationJobResult copyResult, boolean verificationRequested,
				Map<String, BulkMigrationVerificationResult> verification,
				Map<String, ChunkedBulkMigrationProgress> committedProgress, String stoppedTaskId, Path auditFile) {
			this(manifestFingerprint, planFingerprint, phase, copyResult, verificationRequested, verification,
					committedProgress, stoppedTaskId, auditFile,
					new TransferSchemaPreparation.Progress(List.of(), 0, null));
		}

		public Outcome {
			verification = Map.copyOf(verification);
			committedProgress = Map.copyOf(committedProgress);
		}
	}

	public static final class ImportException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		private final Outcome outcome;

		ImportException(Outcome outcome) {
			super("CSV import failed during " + outcome.phase()
					+ ". Committed work may remain; inspect the outcome before retrying. "
					+ (outcome.auditFile() == null ? "Audit report unavailable." : "Audit report saved."));
			this.outcome = outcome;
		}

		public Outcome outcome() {
			return outcome;
		}
	}

	private record Loaded(TransferFilePackageIO.Snapshot snapshot, TransferJobConfiguration configuration,
			Schema schema, Map<String, TransferCsvSource> sources) {
	}

	/** Complete offline preflight; never borrows a target connection. */
	public void validatePackage(Path manifest, String expectedFingerprint) throws Exception {
		load(manifest, expectedFingerprint);
	}

	public Review review(Path manifest, String expectedFingerprint, Map<String, ? extends DataSource> connections)
			throws Exception {
		final Loaded loaded = load(manifest, expectedFingerprint);
		try (var target = binding(loaded, connections).getConnection()) {
			target.setReadOnly(true);
			final var plan = plan(loaded, target);
			final var schemaPlan = TransferSchemaPreparation.plan(target, loaded.schema(), plan,
					loaded.configuration().schemaMode(), loaded.configuration().compatibilityPolicy());
			new TransferFilePackageIO().read(manifest, loaded.snapshot().fingerprint());
			return new Review(manifest.toAbsolutePath().normalize(), loaded.snapshot().fingerprint(),
					MigrationPlanCommand.databaseIdentity(target), schemaPlan.fingerprint(), schemaPlan);
		} catch (TransferSchemaPreparation.ValidationException failure) {
			throw failure;
		} catch (Exception failure) {
			throw new IllegalArgumentException(
					"Unable to review CSV import. Check target binding, product, schema, columns, keys and empty tables.");
		}
	}

	/**
	 * Explicit write boundary; captures reviewed bytes before preflight and never
	 * resets or resumes a target.
	 */
	public Outcome execute(Review review, Map<String, ? extends DataSource> connections, Path workspace) {
		Objects.requireNonNull(review, "review");
		TransferJobExecutionService.Phase phase = TransferJobExecutionService.Phase.REVALIDATION;
		final var progress = new LinkedHashMap<String, ChunkedBulkMigrationProgress>();
		final var verified = new LinkedHashMap<String, BulkMigrationVerificationResult>();
		final String[] active = { null };
		BulkMigrationJobResult result = null;
		Path directory = null;
		boolean verificationRequested = true;
		List<TransferSchemaPreparation.Statement> ddl = List.of();
		int completedDdl = 0;
		Integer attemptedDdl = null;
		List<String> schemaWarnings = List.of();
		try {
			if (workspace == null || !Files.isDirectory(workspace) || !Files.isWritable(workspace))
				throw new IllegalArgumentException("Choose an existing writable import workspace.");
			final var original = new TransferFilePackageIO().read(review.manifestFile(), review.manifestFingerprint());
			directory = Files.createTempDirectory(workspace.toAbsolutePath().normalize(), "import-");
			final var members = new ArrayList<>(List.of("manifest.json", "transfer.yaml", "source-schema.xml"));
			original.manifest().tables().forEach(table -> members.add(table.file()));
			for (String name : members)
				Files.copy(original.file().getParent().resolve(name), directory.resolve(name),
						java.nio.file.LinkOption.NOFOLLOW_LINKS);
			final Loaded loaded = load(directory.resolve("manifest.json"), review.manifestFingerprint());
			verificationRequested = loaded.configuration().verify();
			try (var target = binding(loaded, connections).getConnection()) {
				if (!MigrationPlanCommand.databaseIdentity(target).equals(review.targetIdentity()))
					throw new IllegalArgumentException("Target database identity changed.");
				final var plan = plan(loaded, target);
				final var schemaPlan = TransferSchemaPreparation.plan(target, loaded.schema(), plan,
						loaded.configuration().schemaMode(), loaded.configuration().compatibilityPolicy());
				if (!schemaPlan.fingerprint().equals(review.planFingerprint())
						|| !schemaPlan.equals(review.schemaPreparation()))
					throw new IllegalArgumentException("CSV import plan changed. Review again.");
				if (!target.getMetaData().supportsTransactions() || verificationRequested
						&& !target.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE))
					throw new IllegalArgumentException(
							"Target transaction/verification isolation support is insufficient.");
				target.setReadOnly(false);
				target.setAutoCommit(true);
				new TransferFilePackageIO().read(loaded.snapshot().file(), review.manifestFingerprint());
				ddl = schemaPlan.statements();
				schemaWarnings = schemaPlan.warnings();
				if (!ddl.isEmpty()) {
					phase = TransferJobExecutionService.Phase.SCHEMA_PREPARATION;
					final TransferSchemaPreparation.Progress[] observed = {
							new TransferSchemaPreparation.Progress(ddl, 0, null, schemaWarnings) };
					try {
						TransferSchemaPreparation.execute(target, schemaPlan, value -> {
							observed[0] = value;
							active[0] = value.attemptedStatement() == null ? null
									: value.planned().get(value.attemptedStatement() - 1).taskId();
						});
					} finally {
						completedDdl = observed[0].completedStatements();
						attemptedDdl = observed[0].attemptedStatement();
					}
				}
				BulkMigrationTargetValidator.validate(target, plan);
				phase = TransferJobExecutionService.Phase.COPY;
				result = BulkMigrationJobExecutor.executePlan(target, plan, new BulkMigrationJobListener() {
					@Override
					public void onTaskStarted(String id, int index, int count) {
						active[0] = id;
					}

					@Override
					public void onTaskCompleted(String id, ChunkedBulkMigrationResult value, int index, int count) {
						active[0] = null;
					}
				}, new ChunkedBulkMigrationListener() {
					@Override
					public void onChunkCompleted(ChunkedBulkMigrationProgress value) {
						progress.put(value.getMigrationId(), value);
					}
				});
				if (verificationRequested) {
					phase = TransferJobExecutionService.Phase.VERIFICATION;
					target.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
					target.setAutoCommit(false);
					try {
						if (target.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE)
							throw new IllegalArgumentException("Target did not retain requested isolation.");
						for (var task : plan.getTasks()) {
							final var targetSource = new JdbcBulkMigrationKeysetSource(target,
									task.getEffectiveTargetTable());
							final var comparison = BulkMigrationVerifier.verify(
									task.getKeysetSource(), targetSource, task.getEffectiveSourceTable().getColumns()
											.stream().map(column -> column.getName()).toList(),
									loaded.configuration().chunkSize());
							verified.put(task.getTaskId(), comparison);
						}
						if (verified.values().stream().anyMatch(value -> !value.isMatch()))
							throw new IllegalArgumentException("Imported data differs from CSV package.");
					} finally {
						target.rollback();
					}
				}
				phase = TransferJobExecutionService.Phase.FINALIZATION;
			}
			final var outcome = new Outcome(review.manifestFingerprint(), review.planFingerprint(),
					TransferJobExecutionService.Phase.COMPLETE, result, verificationRequested, verified, progress, null,
					directory.resolve("import-result.json"),
					new TransferSchemaPreparation.Progress(ddl, completedDdl, attemptedDdl, schemaWarnings));
			writeAudit(outcome);
			return outcome;
		} catch (Exception failure) {
			Outcome outcome = new Outcome(review.manifestFingerprint(), review.planFingerprint(), phase, result,
					verificationRequested, verified, progress, active[0],
					directory == null ? null : directory.resolve("import-result.json"),
					new TransferSchemaPreparation.Progress(ddl, completedDdl, attemptedDdl, schemaWarnings));
			if (outcome.auditFile() != null) {
				try {
					writeAudit(outcome);
				} catch (Exception reportFailure) {
					outcome = new Outcome(outcome.manifestFingerprint(), outcome.planFingerprint(), outcome.phase(),
							result, verificationRequested, verified, progress, active[0], null,
							outcome.schemaPreparation());
				}
			}
			throw new ImportException(outcome);
		}
	}

	private static DataSource binding(Loaded loaded, Map<String, ? extends DataSource> connections) {
		final var target = connections == null ? null : connections.get(loaded.configuration().target());
		if (target == null)
			throw new IllegalArgumentException("Supply only the target runtime connection for CSV import.");
		return target;
	}

	private static Loaded load(Path manifest, String expected) throws Exception {
		final var snapshot = new TransferFilePackageIO().read(manifest, expected);
		final Path directory = snapshot.file().getParent();
		final var configuration = new TransferJobConfigurationIO().read(directory.resolve("transfer.yaml"))
				.configuration();
		if (configuration.mappingFile() != null)
			throw new IllegalArgumentException("Initial CSV import does not support an explicit mapping file.");
		new BulkMigrationJobDraftBuilder().inspect(directory.resolve("source-schema.xml")); // Reject DTDs and embedded
																							// row data.
		final byte[] bytes = BoundedMigrationFile.read(directory.resolve("source-schema.xml"), 16_777_216L,
				"maxSchemaBytes", "Schema");
		final var root = SchemaUtils.readXml(new java.io.ByteArrayInputStream(bytes));
		if (!(root instanceof Schema schema))
			throw new IllegalArgumentException("Package must contain one canonical source Schema.");
		final var expectedTables = new LinkedHashMap<String, Table>();
		final var unique = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Table, Boolean>());
		for (int index = 0; index < configuration.tables().size(); index++) {
			final var table = BulkMigrationJobConfigurationResolver.resolveTable(List.copyOf(schema.getTables()),
					configuration.tables().get(index));
			if (!unique.add(table))
				throw new IllegalArgumentException("Duplicate source table selection.");
			expectedTables.put("export-table-" + (index + 1), table);
		}
		if (expectedTables.size() != snapshot.manifest().tables().size())
			throw new IllegalArgumentException("Package table set differs from its job.");
		final var sources = new LinkedHashMap<String, TransferCsvSource>();
		for (final var member : snapshot.manifest().tables()) {
			final var table = expectedTables.get(member.taskId());
			if (table == null || !Objects.equals(table.getCatalogName(), member.catalog())
					|| !Objects.equals(table.getSchemaName(), member.schema())
					|| !table.getName().equals(member.table())
					|| !table.getColumns().stream().map(column -> column.getName()).toList().equals(member.columns()))
				throw new IllegalArgumentException("Package table/column identities differ from canonical Schema.");
			final var source = new TransferCsvSource(table, directory.resolve(member.file()), configuration.files(),
					"CSV-v1:" + snapshot.fingerprint() + ":" + member.fingerprint(), member.rows());
			source.validate();
			sources.put(member.taskId(), source);
		}
		new TransferFilePackageIO().read(manifest, snapshot.fingerprint());
		return new Loaded(snapshot, configuration, schema, Map.copyOf(sources));
	}

	private static BulkMigrationJobPlan plan(Loaded loaded, Connection connection) throws Exception {
		final var dialect = TransferJobPreparationService.dialect(connection);
		final String schema = loaded.configuration().targetSchema() == null
				? dialect.getCatalogReader().getSchemaReader().getCurrentSchemaName(connection)
				: loaded.configuration().targetSchema();
		if (schema == null || schema.isBlank())
			throw new IllegalArgumentException("Set an exact targetSchema.");
		final var tasks = new ArrayList<BulkMigrationJobTask>();
		for (final var member : loaded.snapshot().manifest().tables()) {
			final var source = loaded.sources().get(member.taskId());
			final var target = new Table(source.getTable().getName()).setCatalogName(connection.getCatalog())
					.setSchemaName(schema);
			source.getTable().getColumns().forEach(column -> target.getColumns().add(column.clone()));
			target.setPrimaryKey(source.getTable().getPrimaryKeyConstraint().getName(),
					source.getTable().getPrimaryKeyConstraint().getColumns().stream()
							.map(ref -> target.getColumns().get(ref.getName()))
							.toArray(com.sqlapp.data.schemas.Column[]::new));
			tasks.add(BulkMigrationJobTask.builder().taskId(member.taskId()).keysetSource(source).targetTable(target)
					.requireEmptyTarget(true)
					.options(ChunkedBulkMigrationOption.builder().migrationId(member.taskId())
							.mode(BulkMigrationMode.INSERT).resume(false).chunkSize(loaded.configuration().chunkSize())
							.build())
					.build());
		}
		return BulkMigrationJobPlanner.plan(tasks);
	}

	private static void writeAudit(Outcome outcome) throws java.io.IOException {
		AtomicMigrationFile.write(outcome.auditFile(),
				temporary -> Files.writeString(temporary,
						new com.sqlapp.util.JsonConverter()
								.toJsonString(Map.of("formatVersion", 1, "outcome", outcome)),
						java.nio.charset.StandardCharsets.UTF_8));
	}
}
