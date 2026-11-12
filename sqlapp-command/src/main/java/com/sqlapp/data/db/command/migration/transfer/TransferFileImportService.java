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
 * Target-only review/import of a completed CSV/TSV/JSONL package; never
 * reconnects to its source.
 */
public final class TransferFileImportService {
	public record Review(Path manifestFile, String manifestFingerprint, DatabaseIdentity targetIdentity,
			String planFingerprint, TransferSchemaPreparation.Plan schemaPreparation, StagingReview staging) {
		public Review(Path manifestFile, String manifestFingerprint, DatabaseIdentity targetIdentity,
				String planFingerprint, TransferSchemaPreparation.Plan schemaPreparation) {
			this(manifestFile, manifestFingerprint, targetIdentity, planFingerprint, schemaPreparation, null);
		}

		public Review(Path manifestFile, String manifestFingerprint, DatabaseIdentity targetIdentity,
				String planFingerprint) {
			this(manifestFile, manifestFingerprint, targetIdentity, planFingerprint,
					new TransferSchemaPreparation.Plan(planFingerprint, List.of()));
		}
	}

	public record StagingReview(TransferStagedReplacement.TargetPlan targetPlan,
			TransferSchemaPreparation.Plan stagingSchema) {
	}

	public record StagingOutcome(TransferStagedReplacement.State state, List<TransferStagedReplacement.Target> targets,
			BulkMigrationJobResult copyResult, Map<String, ChunkedBulkMigrationProgress> progress,
			Map<String, BulkMigrationVerificationResult> verification,
			TransferSchemaPreparation.Progress schemaPreparation, Map<String, Long> committedRows,
			List<String> cleanedStages) {
		public StagingOutcome {
			targets = List.copyOf(targets);
			progress = Map.copyOf(progress);
			verification = Map.copyOf(verification);
			committedRows = Map.copyOf(committedRows);
			cleanedStages = List.copyOf(cleanedStages);
		}
	}

	public record Outcome(String manifestFingerprint, String planFingerprint, TransferJobExecutionService.Phase phase,
			BulkMigrationJobResult copyResult, boolean verificationRequested,
			Map<String, BulkMigrationVerificationResult> verification,
			Map<String, ChunkedBulkMigrationProgress> committedProgress, String stoppedTaskId, Path auditFile,
			TransferSchemaPreparation.Progress schemaPreparation, TransferFailureDiagnostic failureDiagnostic,
			StagingOutcome staging) {
		public Outcome(String manifestFingerprint, String planFingerprint, TransferJobExecutionService.Phase phase,
				BulkMigrationJobResult copyResult, boolean verificationRequested,
				Map<String, BulkMigrationVerificationResult> verification,
				Map<String, ChunkedBulkMigrationProgress> committedProgress, String stoppedTaskId, Path auditFile,
				TransferSchemaPreparation.Progress schemaPreparation, TransferFailureDiagnostic failureDiagnostic) {
			this(manifestFingerprint, planFingerprint, phase, copyResult, verificationRequested, verification,
					committedProgress, stoppedTaskId, auditFile, schemaPreparation, failureDiagnostic, null);
		}

		public Outcome(String manifestFingerprint, String planFingerprint, TransferJobExecutionService.Phase phase,
				BulkMigrationJobResult copyResult, boolean verificationRequested,
				Map<String, BulkMigrationVerificationResult> verification,
				Map<String, ChunkedBulkMigrationProgress> committedProgress, String stoppedTaskId, Path auditFile,
				TransferSchemaPreparation.Progress schemaPreparation) {
			this(manifestFingerprint, planFingerprint, phase, copyResult, verificationRequested, verification,
					committedProgress, stoppedTaskId, auditFile, schemaPreparation, null);
		}

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
			super("File import failed during " + outcome.phase() + ". "
					+ (outcome.failureDiagnostic() == null ? ""
							: outcome.failureDiagnostic().reason() + " "
									+ outcome.failureDiagnostic().technicalDetails() + ". ")
					+ (outcome.staging() == null ? "Committed work may remain; inspect the outcome before retrying. "
							: outcome.staging().state().summary() + " ")
					+ (outcome.auditFile() == null ? "Audit report unavailable." : "Audit report saved."));
			this.outcome = outcome;
		}

		public Outcome outcome() {
			return outcome;
		}
	}

	private record Loaded(TransferFilePackageIO.Snapshot snapshot, TransferJobConfiguration configuration,
			Schema schema, Map<String, TransferCsvSource> sources, TransferNameMappingProjection.Projection projection,
			Map<String, Table> tables, String mappingFingerprint) {
	}

	public record PackageColumn(String sourceName, String targetName, String sourceType, boolean notNull,
			boolean primaryKey) {
	}

	public record PackageTable(String taskId, String sourceCatalog, String sourceSchema, String sourceTable,
			String targetTable, String file, long recordedRows, boolean fullScanInsert, boolean generatedKeyIfCreated,
			List<PackageColumn> columns) {
		public PackageTable {
			columns = List.copyOf(columns);
		}
	}

	/**
	 * Recorded intent only; no value check, target lookup or execution approval.
	 */
	public record PackageDescription(TransferFilePackageIO.Snapshot snapshot, TransferJobConfiguration configuration,
			String mappingFingerprint, List<PackageTable> tables) {
		public PackageDescription {
			tables = List.copyOf(tables);
		}
	}

	/**
	 * Metadata overview, including unsupported value types, without constructing
	 * row readers.
	 */
	public PackageDescription describePackage(Path manifest, String expectedFingerprint) throws Exception {
		var loaded = load(manifest, expectedFingerprint, null, false, false);
		var tables = new ArrayList<PackageTable>();
		for (var member : loaded.snapshot().manifest().tables()) {
			var source = loaded.tables().get(member.taskId());
			var target = loaded.projection() == null ? source
					: loaded.projection().bySourceName().get(source.getName());
			var key = source.getPrimaryKeyConstraint();
			var keys = key == null ? java.util.Set.<String>of()
					: key.getColumns().stream().map(column -> column.getName())
							.collect(java.util.stream.Collectors.toSet());
			var columns = new ArrayList<PackageColumn>();
			for (int index = 0; index < source.getColumns().size(); index++) {
				var column = source.getColumns().get(index);
				columns.add(new PackageColumn(column.getName(), target.getColumns().get(index).getName(),
						Objects.toString(column.getDataType(), "not modeled"), column.isNotNull(),
						keys.contains(column.getName())));
			}
			tables.add(new PackageTable(member.taskId(), member.catalog(), member.schema(), member.table(),
					target.getName(), member.file(), member.rows(), !TransferCsvSource.supportsImportKeys(source),
					loaded.configuration()
							.schemaMode() == TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY
							&& keys.isEmpty(),
					columns));
		}
		return new PackageDescription(loaded.snapshot(), loaded.configuration(), loaded.mappingFingerprint(), tables);
	}

	/** Complete offline preflight; never borrows a target connection. */
	public void validatePackage(Path manifest, String expectedFingerprint) throws Exception {
		load(manifest, expectedFingerprint);
	}

	public record PreviewCell(String text, boolean sqlNull, boolean truncated) {
		@Override
		public String toString() {
			return text + (truncated ? " … [truncated]" : "");
		}
	}

	/**
	 * Display-only sample; recorded totals are not a full value check or import
	 * approval.
	 */
	public record Preview(TransferFilePackageIO.Snapshot snapshot, TransferFilePackageIO.TableFile table,
			List<String> sourceTypes, List<List<PreviewCell>> rows, int rowLimit, boolean moreRecordedRows) {
		public Preview {
			sourceTypes = List.copyOf(sourceTypes);
			rows = rows.stream().map(List::copyOf).toList();
		}
	}

	/**
	 * Offline typed preview using the import reader; validates integrity but parses
	 * only this sample.
	 */
	public Preview preview(Path manifest, String expectedFingerprint, String taskId) throws Exception {
		return preview(manifest, expectedFingerprint, taskId, 20);
	}

	public Preview preview(Path manifest, String expectedFingerprint, String taskId, int rowLimit) throws Exception {
		if (taskId == null || taskId.isBlank() || rowLimit < 1 || rowLimit > 100)
			throw new IllegalArgumentException("Choose an exact package task and a preview limit from 1 to 100 rows.");
		var loaded = load(manifest, expectedFingerprint, taskId, false);
		var source = loaded.sources().get(taskId);
		if (source == null)
			throw new IllegalArgumentException("The selected task is not in this package. Reopen the package preview.");
		var member = loaded.snapshot().manifest().tables().stream().filter(table -> table.taskId().equals(taskId))
				.findFirst().orElseThrow();
		var sampled = new ArrayList<List<PreviewCell>>();
		var rows = source.iterator(null);
		Throwable primary = null;
		long characters = 0;
		var json = new com.sqlapp.util.JsonConverter();
		try {
			while (sampled.size() < rowLimit && rows.hasNext()) {
				var row = rows.next();
				var cells = new ArrayList<PreviewCell>();
				for (var column : source.getTable().getColumns()) {
					Object value = row.get(column);
					boolean quoted = value instanceof String || TransferCsvTemporalCodec.temporal(column);
					String text = value == null ? "null"
							: TransferCsvTemporalCodec.temporal(column) ? TransferCsvTemporalCodec.encode(column, value)
									: value.toString();
					boolean truncated = text.codePointCount(0, text.length()) > 512;
					if (truncated)
						text = text.substring(0, text.offsetByCodePoints(0, 512));
					if (value != null && quoted)
						text = json.toJsonString(text);
					characters += text.length();
					if (characters > 1_048_576)
						throw new IllegalArgumentException(
								"Preview exceeds its 1 MiB display limit. Choose fewer sample rows.");
					cells.add(new PreviewCell(text, value == null, truncated));
				}
				sampled.add(cells);
			}
		} catch (Exception | Error failure) {
			primary = failure;
			throw failure;
		} finally {
			com.sqlapp.util.iterator.IteratorCloseUtils.close(primary, rows);
		}
		new TransferFilePackageIO().read(manifest, loaded.snapshot().fingerprint());
		return new Preview(loaded.snapshot(), member,
				source.getTable().getColumns().stream().map(column -> column.getDataType().toString()).toList(),
				sampled, rowLimit, member.rows() > sampled.size());
	}

	public Review review(Path manifest, String expectedFingerprint, Map<String, ? extends DataSource> connections)
			throws Exception {
		return review(manifest, expectedFingerprint, connections, null);
	}

	/**
	 * Optional manual draft; the existing review and audit models remain unchanged.
	 */
	public record SqlReview(Review review, String schemaSqlDraft) {
		public SqlReview {
			Objects.requireNonNull(review, "review");
		}
	}

	public SqlReview reviewWithSqlDraft(Path manifest, String expectedFingerprint,
			Map<String, ? extends DataSource> connections) throws Exception {
		final var draft = new java.util.concurrent.atomic.AtomicReference<String>();
		return new SqlReview(review(manifest, expectedFingerprint, connections, draft::set), draft.get());
	}

	private Review review(Path manifest, String expectedFingerprint, Map<String, ? extends DataSource> connections,
			java.util.function.Consumer<String> draft) throws Exception {
		final Loaded loaded = load(manifest, expectedFingerprint);
		try (var target = binding(loaded, connections).getConnection()) {
			target.setReadOnly(true);
			final var plan = plan(loaded, target);
			final var schemaPlan = schemaPlan(loaded, target, plan);
			var staged = staged(loaded) ? stagePlan(loaded, target, plan).review() : null;
			new TransferFilePackageIO().read(manifest, loaded.snapshot().fingerprint());
			if (draft != null) {
				var dialect = TransferJobPreparationService.dialect(target);
				draft.accept(staged == null
						? TransferSchemaSqlScript.render(schemaPlan, dialect,
								loaded.snapshot().manifest().jobFingerprint())
						: TransferStagedReplacement.sqlDraft(staged.stagingSchema(), schemaPlan,
								staged.targetPlan().replacementSql()));
			}
			return new Review(manifest.toAbsolutePath().normalize(), loaded.snapshot().fingerprint(),
					MigrationPlanCommand.databaseIdentity(target), schemaPlan.fingerprint(), schemaPlan, staged);
		} catch (TransferSchemaPreparation.ValidationException failure) {
			throw failure;
		} catch (Exception failure) {
			throw new IllegalArgumentException(
					"Unable to review File import. Check target binding, product, schema, columns, keys and empty tables.");
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
			if (new TransferJobConfigurationIO().read(original.file().getParent().resolve("transfer.yaml"))
					.configuration().mappingFile() != null)
				members.add(TransferNameMappingIO.CAPTURED_FILE);
			original.manifest().tables().forEach(table -> members.add(table.file()));
			for (String name : members)
				Files.copy(original.file().getParent().resolve(name), directory.resolve(name),
						java.nio.file.LinkOption.NOFOLLOW_LINKS);
			final Loaded loaded = load(directory.resolve("manifest.json"), review.manifestFingerprint());
			verificationRequested = loaded.configuration().verify();
			if (staged(loaded) || review.staging() != null)
				return executeStaged(review, loaded, binding(loaded, connections), directory);
			try (var target = binding(loaded, connections).getConnection()) {
				if (!MigrationPlanCommand.databaseIdentity(target).equals(review.targetIdentity()))
					throw new IllegalArgumentException("Target database identity changed.");
				final var plan = plan(loaded, target);
				final var schemaPlan = schemaPlan(loaded, target, plan);
				if (!schemaPlan.fingerprint().equals(review.planFingerprint())
						|| !schemaPlan.equals(review.schemaPreparation()))
					throw new IllegalArgumentException("File import plan changed. Review again.");
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
				result = copyFileRows(target, plan, active, progress);
				if (verificationRequested) {
					phase = TransferJobExecutionService.Phase.VERIFICATION;
					target.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
					target.setAutoCommit(false);
					try {
						if (target.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE)
							throw new IllegalArgumentException("Target did not retain requested isolation.");
						verifyCopiedTables(target, plan, loaded.configuration().chunkSize(), verified);
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
		} catch (ImportException failure) {
			throw failure;
		} catch (Exception failure) {
			Outcome outcome = new Outcome(review.manifestFingerprint(), review.planFingerprint(), phase, result,
					verificationRequested, verified, progress, active[0],
					directory == null ? null : directory.resolve("import-result.json"),
					new TransferSchemaPreparation.Progress(ddl, completedDdl, attemptedDdl, schemaWarnings),
					TransferFailureDiagnostic.capture(failure, phase == TransferJobExecutionService.Phase.VERIFICATION
							&& verified.values().stream().anyMatch(value -> !value.isMatch())));
			if (outcome.auditFile() != null) {
				try {
					writeAudit(outcome);
				} catch (Exception reportFailure) {
					outcome = new Outcome(outcome.manifestFingerprint(), outcome.planFingerprint(), outcome.phase(),
							result, verificationRequested, verified, progress, active[0], null,
							outcome.schemaPreparation(), outcome.failureDiagnostic());
				}
			}
			throw new ImportException(outcome);
		}
	}

	private record StagePlan(StagingReview review, BulkMigrationJobPlan copy) {
	}

	private static boolean staged(Loaded loaded) {
		return loaded.configuration()
				.effectiveTargetPreparation() == TransferJobConfiguration.TargetPreparation.STAGED_REPLACE;
	}

	private static StagePlan stagePlan(Loaded loaded, Connection target, BulkMigrationJobPlan original)
			throws Exception {
		var fingerprint = loaded.snapshot().fingerprint();
		var destination = TransferStagedReplacement.prepareTargets(target, original, fingerprint,
				loaded.configuration());
		var schema = readPackageSchema(loaded.snapshot());
		TransferJobPreparationService.prepareStagingSchema(schema, fingerprint);
		var names = new LinkedHashMap<String, TransferNameMappingIO.TableMapping>();
		var selected = new ArrayList<Table>();
		for (int i = 0; i < original.getTasks().size(); i++) {
			var task = original.getTasks().get(i);
			String sourceName = task.getEffectiveSourceTable().getName();
			names.put(sourceName, new TransferNameMappingIO.TableMapping(destination.targets().get(i).stageTable(),
					task.getColumnMappings()));
			selected.add(schema.getTables().get(sourceName));
		}
		var projection = TransferNameMappingProjection.project(schema, selected,
				new TransferNameMappingIO.Configuration(1, names));
		var stageTasks = new ArrayList<BulkMigrationJobTask>();
		for (int i = 0; i < original.getTasks().size(); i++) {
			var task = original.getTasks().get(i);
			var identity = destination.targets().get(i);
			var table = projection.bySourceName().get(task.getEffectiveSourceTable().getName()).clone()
					.setCatalogName(identity.catalog()).setSchemaName(identity.schema());
			stageTasks.add(BulkMigrationJobTask.builder().taskId(task.getTaskId()).sourceTable(task.getSourceTable())
					.keysetSource(task.getKeysetSource()).fullScan(task.isFullScan()).targetTable(table)
					.columnMappings(task.getColumnMappings()).requireEmptyTarget(true).options(task.getOptions())
					.build());
		}
		var stageCopy = BulkMigrationJobPlanner.plan(stageTasks);
		var stageSchema = TransferSchemaPreparation.plan(target, schema, stageCopy,
				TransferJobConfiguration.SchemaMode.CREATE_MISSING, loaded.configuration().compatibilityPolicy(),
				projection, null, false, true);
		if (stageSchema.tables().stream()
				.anyMatch(table -> table.action() != TransferSchemaPreparation.TableAction.CREATE))
			throw new TransferSchemaPreparation.ValidationException(
					"A staging table for this file package already exists.");
		return new StagePlan(new StagingReview(destination, stageSchema), stageCopy);
	}

	private Outcome executeStaged(Review review, Loaded loaded, DataSource supplied, Path directory) {
		var execution = new TransferStagedReplacement.Finalization(review.schemaPreparation());
		var targetSource = new TransferJobExecutionService.BoundDataSource(supplied, review.targetIdentity());
		var progress = new LinkedHashMap<String, ChunkedBulkMigrationProgress>();
		var verification = new LinkedHashMap<String, BulkMigrationVerificationResult>();
		var stageSchema = new TransferSchemaPreparation.Progress[] {
				new TransferSchemaPreparation.Progress(List.of(), 0, null) };
		var active = new String[] { null };
		BulkMigrationJobResult stageResult = null;
		TransferFailureDiagnostic diagnostic = null;
		boolean failed = false;
		com.sqlapp.jdbc.bulk.BulkMigrationJobPlan originalPlan = null;
		try {
			if (!staged(loaded) || review.staging() == null)
				throw new IllegalArgumentException("Staged file import requires a fresh staging review.");
			try (var stable = targetSource.getConnection()) {
				stable.setReadOnly(true);
				var original = plan(loaded, stable);
				originalPlan = original;
				var finalSchema = schemaPlan(loaded, stable, original);
				var stage = stagePlan(loaded, stable, original);
				if (!finalSchema.equals(review.schemaPreparation())
						|| !finalSchema.fingerprint().equals(review.planFingerprint())
						|| !stage.review().equals(review.staging()))
					throw new TransferStagedReplacement.TargetChanged();
				new TransferFilePackageIO().read(loaded.snapshot().file(), review.manifestFingerprint());
				stable.setReadOnly(false);
				stable.setAutoCommit(true);
				execution.phase = TransferJobExecutionService.Phase.SCHEMA_PREPARATION;
				execution.state = TransferStagedReplacement.State.STAGING_FAILED;
				TransferSchemaPreparation.execute(stable, stage.review().stagingSchema(),
						value -> stageSchema[0] = value);
				BulkMigrationTargetValidator.validate(stable, stage.copy());
				execution.phase = TransferJobExecutionService.Phase.COPY;
				stageResult = copyFileRows(stable, stage.copy(), active, progress);
				execution.phase = TransferJobExecutionService.Phase.VERIFICATION;
				stable.setReadOnly(true);
				stable.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
				stable.setAutoCommit(false);
				Throwable primary = null;
				try {
					verifyCopiedTables(stable, stage.copy(), loaded.configuration().chunkSize(), verification);
					execution.state = TransferStagedReplacement.State.STAGE_VERIFIED;
					TransferStagedReplacement.applyVerifiedStage(targetSource, stable, original,
							stage.review().targetPlan(), loaded.configuration(), finalSchema,
							() -> new TransferFilePackageIO().read(loaded.snapshot().file(),
									review.manifestFingerprint()),
							execution);
				} catch (Exception | LinkageError failure) {
					primary = failure;
					throw failure;
				} finally {
					try {
						stable.rollback();
					} catch (java.sql.SQLException rollbackFailure) {
						if (primary == null)
							throw rollbackFailure;
						primary.addSuppressed(rollbackFailure);
					}
				}
			}
			TransferStagedReplacement.cleanupStages(targetSource, review.staging().targetPlan(), execution);
			execution.phase = TransferJobExecutionService.Phase.COMPLETE;
		} catch (Exception | LinkageError failure) {
			failed = true;
			if (execution.state == TransferStagedReplacement.State.COMMITTED)
				execution.state = TransferStagedReplacement.State.COMMITTED_STAGE_CLEANUP_FAILED;
			diagnostic = failure instanceof TransferStagedReplacement.TargetChanged
					? new TransferFailureDiagnostic(TransferFailureDiagnostic.Kind.REPLACEMENT_TARGET_CHANGED, null,
							null)
					: TransferFailureDiagnostic.capture(failure,
							verification.values().stream().anyMatch(value -> !value.isMatch())
									|| execution.verified != null && !execution.verified.isMatch());
		}
		var finalVerification = new LinkedHashMap<String, BulkMigrationVerificationResult>();
		if (execution.verified != null)
			execution.verified.getTasks()
					.forEach(task -> finalVerification.put(task.getTaskId(), task.getVerificationResult()));
		var status = new StagingOutcome(execution.state,
				review.staging() == null ? List.of() : review.staging().targetPlan().targets(), stageResult, progress,
				verification, stageSchema[0], execution.state.committed() ? execution.rows : Map.of(),
				execution.cleaned);
		var stopped = failed ? execution.activeTask == null ? active[0] : execution.activeTask : null;
		var audit = directory.resolve("import-result.json");
		var outcome = new Outcome(review.manifestFingerprint(), review.planFingerprint(), execution.phase, null, true,
				finalVerification, Map.of(), stopped, audit, execution.schema, diagnostic, status);
		try {
			if (execution.state == TransferStagedReplacement.State.COMMIT_UNCERTAIN && originalPlan != null)
				TransferJobReportIO.write(audit,
						Map.of("formatVersion", 1, "outcome", outcome, "targetIdentity", review.targetIdentity(),
								"copyPlanFingerprint", originalPlan.getFingerprint(), "taskIds",
								originalPlan.getTaskIds()));
			else
				writeAudit(outcome);
		} catch (Exception failure) {
			throw new ImportException(new Outcome(outcome.manifestFingerprint(), outcome.planFingerprint(),
					failed ? outcome.phase() : TransferJobExecutionService.Phase.FINALIZATION, null, true,
					finalVerification, Map.of(), stopped, null, execution.schema, diagnostic, status));
		}
		if (failed)
			throw new ImportException(outcome);
		return outcome;
	}

	static SavedStagingComparison.PlanResolver comparisonPlan(Path manifest, String fingerprint) throws Exception {
		var loaded = load(manifest, fingerprint, null, false);
		if (!staged(loaded))
			throw new IllegalArgumentException("The captured package did not request staged replacement.");
		return connection -> plan(loaded, connection);
	}

	private static BulkMigrationJobResult copyFileRows(Connection target, BulkMigrationJobPlan plan, String[] active,
			Map<String, ChunkedBulkMigrationProgress> progress) throws java.sql.SQLException {
		return BulkMigrationJobExecutor.executePlan(target, plan, new BulkMigrationJobListener() {
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
	}

	/** Verify a file copy against a caller-owned stable target snapshot. */
	private static void verifyCopiedTables(Connection target, BulkMigrationJobPlan plan, int chunkSize,
			Map<String, BulkMigrationVerificationResult> verified) throws Exception {
		for (var task : plan.getTasks()) {
			var columns = task.getEffectiveSourceTable().getColumns().stream().map(column -> column.getName()).toList();
			var actualColumns = columns.stream().map(name -> task.getColumnMappings().getOrDefault(name, name))
					.toList();
			if (task.isFullScan()) {
				var actual = task.getEffectiveTargetTable().clone();
				actual.setRowIteratorHandler(
						new com.sqlapp.data.schemas.rowiterator.JdbcDynamicRowIteratorHandler(target));
				verified.put(task.getTaskId(),
						BulkMigrationUnorderedVerifier.verify(task.getEffectiveSourceTable(),
								task.getEffectiveSourceTable().getRows().iterator(), actual,
								actual.getRows().iterator(), columns, actualColumns, chunkSize));
				continue;
			}
			final var targetSource = new JdbcBulkMigrationKeysetSource(target, task.getEffectiveTargetTable());
			final var comparison = BulkMigrationVerifier.verify(task.getKeysetSource(), targetSource, columns,
					actualColumns, chunkSize);
			verified.put(task.getTaskId(), comparison);
		}
		if (verified.values().stream().anyMatch(value -> !value.isMatch()))
			throw new IllegalArgumentException("Imported data differs from file package.");
	}

	private static DataSource binding(Loaded loaded, Map<String, ? extends DataSource> connections) {
		final var target = connections == null ? null : connections.get(loaded.configuration().target());
		if (target == null)
			throw new IllegalArgumentException("Supply only the target runtime connection for File import.");
		return target;
	}

	private static Loaded load(Path manifest, String expected) throws Exception {
		return load(manifest, expected, null, true);
	}

	private static Loaded load(Path manifest, String expected, String sampleTask, boolean validateRows)
			throws Exception {
		return load(manifest, expected, sampleTask, validateRows, true);
	}

	private static Loaded load(Path manifest, String expected, String sampleTask, boolean validateRows, boolean readers)
			throws Exception {
		final var snapshot = new TransferFilePackageIO().read(manifest, expected);
		final Path directory = snapshot.file().getParent();
		final var job = new TransferJobConfigurationIO().read(directory.resolve("transfer.yaml"));
		if (!job.fingerprint().equals(snapshot.manifest().jobFingerprint()))
			throw new IllegalArgumentException("Package definition changed.");
		final var configuration = job.configuration();
		final var mapping = TransferFilePackageIO.mapping(job);
		var inspected = new BulkMigrationJobDraftBuilder().inspect(directory.resolve("source-schema.xml"));
		if (!inspected.fingerprint().equals(snapshot.manifest().schemaFingerprint()))
			throw new IllegalArgumentException("Package Schema changed.");
		final var schema = readPackageSchema(snapshot);
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
		final var projection = mapping == null ? null
				: TransferNameMappingProjection.project(schema, List.copyOf(expectedTables.values()),
						mapping.configuration());
		final var sources = new LinkedHashMap<String, TransferCsvSource>();
		for (final var member : snapshot.manifest().tables()) {
			final var table = expectedTables.get(member.taskId());
			if (table == null || !Objects.equals(table.getCatalogName(), member.catalog())
					|| !Objects.equals(table.getSchemaName(), member.schema())
					|| !table.getName().equals(member.table())
					|| !table.getColumns().stream().map(column -> column.getName()).toList().equals(member.columns()))
				throw new IllegalArgumentException("Package table/column identities differ from canonical Schema.");
			if (!readers || sampleTask != null && !sampleTask.equals(member.taskId()))
				continue;
			final var source = new TransferCsvSource(table, directory.resolve(member.file()), configuration.files(),
					configuration.files().format() + "-v1:" + snapshot.fingerprint() + ":" + member.fingerprint(),
					member.rows(), false);
			if (validateRows)
				source.validate();
			sources.put(member.taskId(), source);
		}
		if (readers)
			new TransferFilePackageIO().read(manifest, snapshot.fingerprint());
		return new Loaded(snapshot, configuration, schema, Map.copyOf(sources), projection, Map.copyOf(expectedTables),
				mapping == null ? null : mapping.fingerprint());
	}

	private static Schema readPackageSchema(TransferFilePackageIO.Snapshot snapshot) throws java.io.IOException {
		final byte[] bytes = BoundedMigrationFile.read(snapshot.file().getParent().resolve("source-schema.xml"),
				16_777_216L, "maxSchemaBytes", "Schema");
		if (!("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes))
				.equals(snapshot.manifest().schemaFingerprint()))
			throw new IllegalArgumentException("Package Schema changed.");
		final var root = SchemaUtils.readXml(new java.io.ByteArrayInputStream(bytes));
		if (!(root instanceof Schema schema))
			throw new IllegalArgumentException("Package must contain one canonical source Schema.");
		return schema;
	}

	private static BulkMigrationJobPlan plan(Loaded loaded, Connection connection) throws Exception {
		final var dialect = TransferJobPreparationService.dialect(connection);
		TransferJobPreparationService.validateInsertMethod(dialect, loaded.configuration());
		final String schema = loaded.configuration().targetSchema() == null
				? dialect.getCatalogReader().getSchemaReader().getCurrentSchemaName(connection)
				: loaded.configuration().targetSchema();
		if (schema == null || schema.isBlank())
			throw new IllegalArgumentException("Set an exact targetSchema.");
		final var tasks = new ArrayList<BulkMigrationJobTask>();
		for (final var member : loaded.snapshot().manifest().tables()) {
			final var source = loaded.sources().get(member.taskId());
			final var mapped = loaded.projection() == null ? source.getTable()
					: loaded.projection().bySourceName().get(source.getTable().getName());
			final var target = new Table(mapped.getName()).setCatalogName(connection.getCatalog())
					.setSchemaName(schema);
			mapped.getColumns().forEach(column -> target.getColumns().add(column.clone()));
			if (mapped.getPrimaryKeyConstraint() != null)
				target.setPrimaryKey(mapped.getPrimaryKeyConstraint().getName(),
						mapped.getPrimaryKeyConstraint().getColumns().stream()
								.map(ref -> target.getColumns().get(ref.getName()))
								.toArray(com.sqlapp.data.schemas.Column[]::new));
			var task = BulkMigrationJobTask.builder().taskId(member.taskId()).targetTable(target)
					.requireEmptyTarget(true)
					.options(ChunkedBulkMigrationOption.builder().migrationId(member.taskId())
							.mode(BulkMigrationMode.INSERT).resume(false).chunkSize(loaded.configuration().chunkSize())
							.bulkOption(
									BulkOption.builder().insertMethod(loaded.configuration().effectiveInsertMethod())
											.keepIdentity(true).keepNulls(true).build())
							.build());
			var columns = new LinkedHashMap<String, String>();
			for (int index = 0; index < source.getTable().getColumns().size(); index++) {
				String original = source.getTable().getColumns().get(index).getName();
				String renamed = mapped.getColumns().get(index).getName();
				if (!original.equals(renamed))
					columns.put(original, renamed);
			}
			task.columnMappings(columns);
			if (TransferCsvSource.supportsImportKeys(source.getTable()))
				task.keysetSource(source);
			else {
				// Keep the canonical table identity: FK dependency ordering compares related
				// tables.
				var input = source.getTable();
				input.setRowIteratorHandler(rows -> source.iterator(null));
				task.sourceTable(input).fullScan(true);
			}
			tasks.add(task.build());
		}
		return BulkMigrationJobPlanner.plan(tasks);
	}

	private static TransferSchemaPreparation.Plan schemaPlan(Loaded loaded, Connection target,
			BulkMigrationJobPlan plan) throws Exception {
		var schema = TransferSchemaPreparation.plan(target, loaded.schema(), plan, loaded.configuration().schemaMode(),
				loaded.configuration().compatibilityPolicy(), loaded.projection(),
				loaded.configuration().effectiveGeneratedKeyColumn(), staged(loaded));
		var warnings = new ArrayList<>(schema.warnings());
		for (var task : plan.getTasks())
			if (task.isFullScan())
				TransferSchemaPreparation.compatibility(loaded.configuration().compatibilityPolicy(), warnings,
						"[FILE_FULL_SCAN_INSERT] Table " + task.getEffectiveSourceTable().getName()
								+ " uses non-resumable INSERT without portable numeric-key ordering. Duplicates are preserved; verification compares unordered row hashes including multiplicity. Hash-sort chunk positions are not file row positions. MERGE, resume and key-based repair are unavailable.");
		return warnings.equals(schema.warnings()) ? schema
				: TransferSchemaPreparation.withTables(TransferSchemaPreparation.result(schema.fingerprint(),
						schema.statements(), warnings, loaded.configuration().schemaMode()), schema.tables());
	}

	private static void writeAudit(Outcome outcome) throws java.io.IOException {
		AtomicMigrationFile.write(outcome.auditFile(),
				temporary -> Files.writeString(temporary,
						new com.sqlapp.util.JsonConverter()
								.toJsonString(Map.of("formatVersion", 1, "outcome", outcome)),
						java.nio.charset.StandardCharsets.UTF_8));
	}
}
