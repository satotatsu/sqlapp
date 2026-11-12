/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.sqlapp.data.db.command.migration.bulk.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.schemas.rowiterator.JdbcDynamicRowIteratorHandler;
import com.sqlapp.jdbc.bulk.*;
import static com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService.*;

/**
 * Verified staging followed by one transactional, dependency-ordered
 * replacement.
 */
public final class TransferStagedReplacement {
	private TransferStagedReplacement() {
	}

	public record Target(String taskId, String catalog, String schema, String table, String stageTable) {
	}

	public record Plan(Review copyReview, List<Target> targets, List<String> applyOrder, String graphFingerprint,
			Map<String, String> existingStructures, List<String> replacementSql) {
		public Plan {
			targets = List.copyOf(targets);
			applyOrder = List.copyOf(applyOrder);
			existingStructures = Map.copyOf(existingStructures);
			replacementSql = List.copyOf(replacementSql);
		}

		TargetPlan targetPlan() {
			return new TargetPlan(targets, applyOrder, graphFingerprint, existingStructures, replacementSql);
		}
	}

	/** Final-table review without assumptions about the staging input. */
	public record TargetPlan(List<Target> targets, List<String> applyOrder, String graphFingerprint,
			Map<String, String> existingStructures, List<String> replacementSql) {
		public TargetPlan {
			targets = List.copyOf(targets);
			applyOrder = List.copyOf(applyOrder);
			existingStructures = Map.copyOf(existingStructures);
			replacementSql = List.copyOf(replacementSql);
		}
	}

	public enum State {
		NOT_STARTED, STAGING_FAILED, STAGE_VERIFIED, APPLYING, ROLLED_BACK, ROLLBACK_FAILED, COMMIT_UNCERTAIN,
		COMMITTED, COMMITTED_STAGE_CLEANUP_FAILED;

		public boolean committed() {
			return this == COMMITTED || this == COMMITTED_STAGE_CLEANUP_FAILED;
		}

		public String summary() {
			return switch (this) {
			case COMMITTED -> "Replacement committed. Staging tables were removed.";
			case COMMITTED_STAGE_CLEANUP_FAILED -> "Replacement committed. Check staging cleanup before running again.";
			case ROLLED_BACK -> "Replacement rolled back. Existing target rows were restored.";
			case COMMIT_UNCERTAIN ->
				"Commit was attempted but not confirmed. Compare the target with the retained staging tables before retrying.";
			case ROLLBACK_FAILED, APPLYING -> "Rollback could not be confirmed. Check the target before retrying.";
			case NOT_STARTED, STAGING_FAILED, STAGE_VERIFIED ->
				"Replacement did not start. Existing target rows were preserved.";
			};
		}
	}

	/**
	 * Stage copy evidence is intentionally separate from final-target committed
	 * progress.
	 */
	public record Status(State state, List<Target> targets, Outcome stageOutcome, Map<String, Long> appliedRows,
			List<String> cleanedStages) {
		public Status {
			targets = List.copyOf(targets);
			appliedRows = Map.copyOf(appliedRows);
			cleanedStages = List.copyOf(cleanedStages);
		}
	}

	private record Graph(List<String> order, String fingerprint) {
	}

	public record TableComparison(Target target, long stagingRows, long targetRows, boolean matchesTarget,
			boolean matchesRecordedStaging) {
	}

	public record Comparison(List<TableComparison> tables) {
		public Comparison {
			tables = List.copyOf(tables);
		}
	}

	/**
	 * Reads a saved result and its sibling review and plan artifacts for read-only
	 * comparison.
	 */
	public static Comparison compareRetainedStage(Path resultReport, DataSource target) {
		return compare(SavedStagingComparison.read(resultReport), target);
	}

	/**
	 * Compares a saved result and includes the fingerprint of the exact input
	 * report read.
	 */
	public static TransferStagingComparisonReportIO.Report compareRetainedStageReport(Path resultReport,
			DataSource target) {
		var evidence = SavedStagingComparison.read(resultReport);
		return new TransferStagingComparisonReportIO.Report(1, evidence.resultFingerprint(), State.COMMIT_UNCERTAIN,
				evidence.targetIdentity(), compare(evidence, target));
	}

	/**
	 * Compares current data in one target snapshot after an unconfirmed commit. The
	 * recorded outcome remains unchanged, including when all rows match.
	 */
	public static Comparison compareRetainedStage(Review review, Outcome outcome, DataSource target) {
		var plan = review.staging();
		var status = outcome.staging();
		var recorded = outcome.verificationResult();
		if (plan == null || status == null || status.state() != State.COMMIT_UNCERTAIN || recorded == null
				|| !recorded.isMatch() || !plan.targets().equals(status.targets())
				|| !review.prepared().job().fingerprint().equals(outcome.prepared().job().fingerprint())
				|| !review.prepared().planFingerprint().equals(outcome.prepared().planFingerprint()))
			throw new TransferSchemaPreparation.ValidationException(
					"Select the retained result of an unconfirmed staged replacement.");
		return compare(new SavedStagingComparison.Evidence(connection -> resolve(review, connection),
				review.prepared().job().fingerprint(), review.prepared().planFingerprint(), review.targetIdentity(),
				plan.targets(), recorded, null), target);
	}

	private static Comparison compare(SavedStagingComparison.Evidence evidence, DataSource target) {
		var recorded = evidence.verification();
		try (var connection = new BoundDataSource(target, evidence.targetIdentity()).getConnection()) {
			connection.setReadOnly(true);
			connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
			connection.setAutoCommit(false);
			// Resolve only the captured table model. The original source is never read.
			var original = evidence.plan().resolve(connection);
			if (!original.getFingerprint().equals(evidence.planFingerprint()))
				throw new TransferSchemaPreparation.ValidationException("The captured replacement plan has changed.");
			recorded.validateAgainst(original);
			if (evidence.targets().size() != original.getTasks().size())
				throw new TransferSchemaPreparation.ValidationException(
						"The recorded staging tables do not match the captured replacement plan.");
			var provider = AtomicTableReplacementProvider.resolve(TransferJobPreparationService.dialect(connection));
			var tables = new ArrayList<Table>();
			for (int i = 0; i < original.getTasks().size(); i++) {
				var task = original.getTasks().get(i);
				var table = task.getEffectiveTargetTable();
				var names = evidence.targets().get(i);
				if (!names.equals(stageTarget(task, evidence.jobFingerprint(), i + 1)))
					throw new TransferSchemaPreparation.ValidationException(
							"The recorded staging tables do not match the captured replacement plan.");
				tables.add(table);
				tables.add(table.clone().setName(names.stageTable()));
			}
			provider.validateTables(connection, tables);
			var comparisons = new ArrayList<TableComparison>();
			for (int i = 0; i < original.getTasks().size(); i++) {
				var task = original.getTasks().get(i);
				var names = evidence.targets().get(i);
				var prior = recorded.getTasks().get(i);
				var result = compareTable(task, names, connection, connection,
						prior.getVerificationResult().getChunkSize());
				comparisons.add(new TableComparison(names, result.getExpectedRows(), result.getActualRows(),
						result.isMatch(), sameStage(prior.getVerificationResult(), result)));
			}
			connection.rollback();
			return new Comparison(comparisons);
		} catch (TransferSchemaPreparation.ValidationException failure) {
			throw failure;
		} catch (Exception failure) {
			var diagnostic = TransferFailureDiagnostic.capture(failure);
			throw new TransferSchemaPreparation.ValidationException(
					"Could not compare retained staging with the current target. "
							+ "Check the reviewed database connection, staging tables and target columns. "
							+ diagnostic.technicalDetails());
		}
	}

	private static boolean sameStage(BulkMigrationVerificationResult recorded,
			BulkMigrationVerificationResult current) {
		// Actual-only chunks may follow the end of staging when the current target is
		// larger.
		var recordedHashes = recorded.getChunks().stream().filter(chunk -> chunk.getExpectedRows() > 0)
				.map(BulkMigrationVerificationChunk::getExpectedHash).toList();
		var currentHashes = current.getChunks().stream().filter(chunk -> chunk.getExpectedRows() > 0)
				.map(BulkMigrationVerificationChunk::getExpectedHash).toList();
		return recorded.getExpectedRows() == current.getExpectedRows() && recordedHashes.equals(currentHashes);
	}

	static final class TargetChanged extends IllegalStateException {
		private static final long serialVersionUID = 1L;
	}

	static Plan prepare(Review review, Map<String, ? extends DataSource> connections) throws java.io.IOException {
		var cfg = review.prepared().job().configuration();
		var mappings = new LinkedHashMap<String, TransferNameMappingIO.TableMapping>();
		try {
			final TargetPlan plan;
			try (var source = connections.get(cfg.source()).getConnection();
					var target = connections.get(cfg.target()).getConnection()) {
				source.setReadOnly(true);
				target.setReadOnly(true);
				var original = resolve(review, source);
				plan = prepareTargets(target, original, review.prepared().job().fingerprint(), cfg);
				for (int index = 0; index < original.getTasks().size(); index++) {
					var task = original.getTasks().get(index);
					mappings.put(task.getEffectiveSourceTable().getName(), new TransferNameMappingIO.TableMapping(
							plan.targets().get(index).stageTable(), task.getColumnMappings()));
				}
			}
			Path directory = Files.createDirectory(review.prepared().directory().resolve("staging"));
			var mappingIO = new TransferNameMappingIO();
			mappingIO.write(directory.resolve("names.yaml"),
					mappingIO.format(new TransferNameMappingIO.Configuration(1, mappings)), null);
			var staged = new TransferJobConfiguration(1, cfg.source(), cfg.target(), cfg.tables(),
					TransferJobConfiguration.SchemaMode.CREATE_MISSING, cfg.sourceSchema(),
					plan.targets().getFirst().schema(), "names.yaml", cfg.chunkSize(), true,
					TransferJobConfiguration.DataMode.JDBC_DIRECT, null, cfg.compatibilityPolicy(), false, null,
					cfg.insertMethod(), null);
			var io = new TransferJobConfigurationIO();
			Path job = directory.resolve("transfer.yaml");
			io.write(job, io.format(staged), null);
			var copy = new TransferJobExecutionService().reviewBuffer(job, connections, directory);
			if (copy.prepared().schemaPreparation().tables().stream()
					.anyMatch(t -> t.action() != TransferSchemaPreparation.TableAction.CREATE))
				throw stageNameCollision();
			return new Plan(copy, plan.targets(), plan.applyOrder(), plan.graphFingerprint(), plan.existingStructures(),
					plan.replacementSql());
		} catch (java.io.IOException | TransferJobPreparationService.PreparationException
				| TransferSchemaPreparation.ValidationException failure) {
			throw failure;
		} catch (Exception failure) {
			if (failure instanceof IllegalArgumentException invalid)
				throw new TransferSchemaPreparation.ValidationException(invalid.getMessage());
			throw new TransferSchemaPreparation.ValidationException(
					"Staged replacement review failed. Check transaction/locking support and selected target foreign keys.");
		}
	}

	static TargetPlan prepareTargets(Connection target, BulkMigrationJobPlan original, String inputFingerprint,
			TransferJobConfiguration configuration) throws SQLException {
		var dialect = TransferJobPreparationService.dialect(target);
		var provider = AtomicTableReplacementProvider.resolve(dialect);
		provider.validate(target);
		if (!BulkInsertResolver.resolve(dialect, configuration.effectiveInsertMethod())
				.supportsCallerTransactionAtomicity())
			throw new IllegalArgumentException(
					"The selected insert method cannot participate in atomic replacement. Choose JDBC_BATCH.");
		var targets = new ArrayList<Target>();
		int ordinal = 0;
		for (var task : original.getTasks()) {
			var stage = stageTarget(task, inputFingerprint, ++ordinal);
			if (original.getTasks().stream()
					.anyMatch(t -> t.getEffectiveTargetTable().getName().equalsIgnoreCase(stage.stageTable())))
				throw new IllegalArgumentException("Staging name collides with a selected target table.");
			targets.add(stage);
		}
		validateStageNamesAvailable(target, targets);
		var structures = structures(target, targets, false);
		var graph = graph(target, original, targets, structures.keySet());
		var tables = original.getTasks().stream().filter(task -> structures.containsKey(task.getTaskId()))
				.map(BulkMigrationJobTask::getEffectiveTargetTable).toList();
		provider.validateTables(target, tables);
		var sql = new ArrayList<>(provider.lockSql(dialect,
				original.getTasks().stream().map(BulkMigrationJobTask::getEffectiveTargetTable).toList()));
		for (String id : graph.order().reversed())
			sql.addAll(sql(dialect, task(original, id).getEffectiveTargetTable(), SqlType.DELETE_ALL));
		return new TargetPlan(targets, graph.order(), graph.fingerprint(), structures, sql);
	}

	private static void validateStageNamesAvailable(Connection connection, List<Target> targets) throws SQLException {
		var names = targets.stream().map(Target::stageTable).collect(java.util.stream.Collectors.toSet());
		var first = targets.getFirst();
		try (var existing = connection.getMetaData().getTables(first.catalog(), first.schema(), "SQLAPP_STG_%", null)) {
			while (existing.next()) {
				if (Objects.equals(first.schema(), existing.getString("TABLE_SCHEM"))
						&& names.contains(existing.getString("TABLE_NAME")))
					throw stageNameCollision();
			}
		}
	}

	private static TransferSchemaPreparation.ValidationException stageNameCollision() {
		return new TransferSchemaPreparation.ValidationException(
				"A staging table for this job already exists. Inspect the previous result and retained staging tables before removing them or choosing a different target schema.");
	}

	static String sqlDraft(Review review, Plan plan) {
		return sqlDraft(plan.copyReview().prepared().schemaPreparation(), review.prepared().schemaPreparation(),
				plan.replacementSql());
	}

	static String sqlDraft(TransferSchemaPreparation.Plan stage, TransferSchemaPreparation.Plan target,
			List<String> replacementSql) {
		var text = new StringBuilder(
				"-- Staging tables are loaded and verified first; existing target rows remain intact.\n");
		stage.statements().forEach(s -> text.append(commentSql(s.sql())).append("\n"));
		text.append("-- After staging verification: create missing final tables.\n");
		target.statements().forEach(s -> text.append(commentSql(s.sql())).append("\n"));
		text.append(
				"-- One SERIALIZABLE transaction: locks, DELETE child-first, streamed INSERT parent-first, verification, COMMIT.\n");
		replacementSql.forEach(s -> text.append(commentSql(s)).append("\n"));
		return text.append(
				"-- Row INSERTs are streamed by the selected insert method. Roll back on failure before COMMIT.\n")
				.toString();
	}

	static Outcome execute(Review review, Map<String, ? extends DataSource> supplied) {
		var staging = review.staging();
		var plan = staging.targetPlan();
		var current = review.prepared();
		var execution = new Finalization(current.schemaPreparation());
		Outcome copied = null;
		try {
			checkArtifacts(review);
			var bindings = bindings(current.job().configuration(), supplied, review.sourceIdentity(),
					review.targetIdentity());
			current = revalidate(review, bindings);
			BulkMigrationJobPlan original;
			try (var source = bindings.source().getConnection()) {
				original = resolve(review, source);
			}
			try (var target = bindings.target().getConnection()) {
				checkTarget(target, original, plan, false);
			}
			execution.phase = Phase.COPY;
			try {
				copied = new TransferJobExecutionService().execute(staging.copyReview(), bindings.connections());
			} catch (ExecutionException failure) {
				copied = failure.outcome();
				execution.activeTask = copied.stoppedTaskId();
				execution.state = State.STAGING_FAILED;
				throw failure;
			}
			execution.state = State.STAGE_VERIFIED;
			execution.phase = Phase.VERIFICATION;
			try (var source = bindings.source().getConnection(); var stable = bindings.target().getConnection()) {
				source.setReadOnly(true);
				source.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
				source.setAutoCommit(false);
				stable.setReadOnly(true);
				stable.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
				stable.setAutoCommit(false);
				// Verify the same stable stage snapshot that will be streamed to final tables.
				var stagePlan = resolve(staging.copyReview(), source);
				if (!ExecuteBulkMigrationJobCommand.verify(stagePlan, stable, current.job().configuration().chunkSize())
						.isMatch())
					throw new IllegalStateException(
							"Staging contents no longer match the source; final target rows were not changed.");
				source.rollback();
				checkArtifacts(review);
				checkArtifacts(staging.copyReview());
				applyVerifiedStage(bindings.target(), stable, original, plan, current.job().configuration(),
						current.schemaPreparation(), () -> {
							checkArtifacts(review);
							checkArtifacts(review.staging().copyReview());
						}, execution);
				stable.rollback();
			}
			cleanupStages(bindings.target(), plan, execution);
			return outcome(current, Phase.COMPLETE, execution.verified, execution.schema, null, plan, execution.state,
					copied, execution.rows, execution.cleaned, null);
		} catch (Exception | LinkageError failure) {
			if (execution.state == State.COMMITTED)
				execution.state = State.COMMITTED_STAGE_CLEANUP_FAILED;
			var diagnostic = execution.state == State.STAGING_FAILED && copied != null ? copied.failureDiagnostic()
					: failure instanceof TargetChanged
							? new TransferFailureDiagnostic(TransferFailureDiagnostic.Kind.REPLACEMENT_TARGET_CHANGED,
									null, null)
							: TransferFailureDiagnostic.capture(failure,
									execution.verified != null && !execution.verified.isMatch());
			throw failed(outcome(current, execution.phase, execution.verified, execution.schema, diagnostic, plan,
					execution.state, copied, execution.rows, execution.cleaned, execution.activeTask));
		}
	}

	/**
	 * Mutable progress for a single application attempt, retained on every failure
	 * path.
	 */
	static final class Finalization {
		State state = State.NOT_STARTED;
		Phase phase = Phase.REVALIDATION;
		String activeTask;
		BulkMigrationJobVerificationResult verified;
		final Map<String, Long> rows = new LinkedHashMap<>();
		final List<String> cleaned = new ArrayList<>();
		TransferSchemaPreparation.Progress schema;

		Finalization(TransferSchemaPreparation.Plan schemaPlan) {
			schema = new TransferSchemaPreparation.Progress(schemaPlan.statements(), 0, null);
		}
	}

	@FunctionalInterface
	interface ArtifactCheck {
		void check() throws Exception;
	}

	/** Apply already-verified staging data under one target transaction. */
	static void applyVerifiedStage(DataSource targetSource, Connection stable, BulkMigrationJobPlan original,
			TargetPlan plan, TransferJobConfiguration configuration, TransferSchemaPreparation.Plan schemaPlan,
			ArtifactCheck checkArtifacts, Finalization execution) throws Exception {
		if (execution.state != State.STAGE_VERIFIED || stable.getAutoCommit()
				|| stable.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE)
			throw new IllegalStateException(
					"Replacement requires a verified staging snapshot in a SERIALIZABLE transaction.");
		try (var target = targetSource.getConnection()) {
			target.setReadOnly(false);
			var dialect = TransferJobPreparationService.dialect(target);
			var provider = AtomicTableReplacementProvider.resolve(dialect);
			provider.validate(target);
			checkTarget(target, original, plan, false);
			execution.phase = Phase.SCHEMA_PREPARATION;
			target.setAutoCommit(true);
			TransferSchemaPreparation.execute(target, schemaPlan, value -> execution.schema = value);
			target.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
			target.setAutoCommit(false);
			if (target.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE)
				throw new IllegalStateException("Target did not retain SERIALIZABLE isolation.");
			boolean commitAttempted = false;
			try {
				for (String lock : provider.lockSql(dialect,
						original.getTasks().stream().map(BulkMigrationJobTask::getEffectiveTargetTable).toList()))
					executeSql(target, lock);
				checkTarget(target, original, plan, true);
				provider.validateTables(target,
						original.getTasks().stream().map(BulkMigrationJobTask::getEffectiveTargetTable).toList());
				checkArtifacts.check();
				execution.phase = Phase.FINALIZATION;
				execution.state = State.APPLYING;
				for (String id : plan.applyOrder().reversed()) {
					execution.activeTask = id;
					for (String delete : sql(dialect, task(original, id).getEffectiveTargetTable(), SqlType.DELETE_ALL))
						executeSql(target, delete);
				}
				for (String id : plan.applyOrder()) {
					execution.activeTask = id;
					var finalTable = task(original, id).getEffectiveTargetTable().clone();
					var stage = stageTable(finalTable, target(plan, id), stable);
					finalTable.setRowIteratorHandler(ignored -> stage.getRows().iterator());
					var option = BulkOption.builder().batchSize(configuration.chunkSize()).keepIdentity(true)
							.keepNulls(true).insertMethod(configuration.effectiveInsertMethod()).build();
					if (!BulkInsertResolver.resolve(target, option).supportsCallerTransactionAtomicity())
						throw new IllegalArgumentException(
								"Insert transport does not support the replacement transaction.");
					BulkInsertResolver.execute(target, finalTable, option);
				}
				var results = new ArrayList<BulkMigrationJobTaskVerificationResult>();
				for (var task : original.getTasks()) {
					execution.activeTask = task.getTaskId();
					var result = compareTable(task, target(plan, task.getTaskId()), stable, target,
							configuration.chunkSize());
					results.add(
							new BulkMigrationJobTaskVerificationResult(task.getTaskId(), result.getColumns(), result));
				}
				execution.verified = new BulkMigrationJobVerificationResult(original.getFingerprint(), results)
						.validateAgainst(original);
				execution.activeTask = results.stream().filter(result -> !result.getVerificationResult().isMatch())
						.map(BulkMigrationJobTaskVerificationResult::getTaskId).findFirst().orElse(null);
				if (!execution.verified.isMatch())
					throw new IllegalStateException("Final target verification failed; rolling back replacement.");
				for (var result : results)
					execution.rows.put(result.getTaskId(), result.getVerificationResult().getActualRows());
				commitAttempted = true;
				target.commit();
				execution.state = State.COMMITTED;
			} catch (Exception | LinkageError failure) {
				if (commitAttempted)
					execution.state = State.COMMIT_UNCERTAIN;
				try {
					target.rollback();
					if (execution.state == State.APPLYING)
						execution.state = State.ROLLED_BACK;
				} catch (SQLException rollback) {
					if (execution.state == State.APPLYING)
						execution.state = State.ROLLBACK_FAILED;
				}
				throw failure;
			}
		}
	}

	static void cleanupStages(DataSource targetSource, TargetPlan plan, Finalization execution) throws SQLException {
		if (execution.state != State.COMMITTED)
			throw new IllegalStateException("Automatic staging cleanup requires a confirmed commit.");
		// Only clean owned stages after a confirmed successful commit.
		try (var target = targetSource.getConnection()) {
			target.setReadOnly(false);
			target.setAutoCommit(true);
			var dialect = TransferJobPreparationService.dialect(target);
			for (var table : plan.targets().reversed()) {
				execution.activeTask = table.taskId();
				var stage = new Table(table.stageTable()).setSchemaName(table.schema()).setCatalogName(table.catalog());
				for (String drop : sql(dialect, stage, SqlType.DROP))
					executeSql(target, drop);
				execution.cleaned.add(table.stageTable());
			}
		} catch (Exception failure) {
			execution.state = State.COMMITTED_STAGE_CLEANUP_FAILED;
			throw failure;
		}
	}

	static void checkTarget(Connection connection, BulkMigrationJobPlan original, TargetPlan plan, boolean locked)
			throws SQLException {
		try {
			var structures = structures(connection, plan.targets(), locked);
			var expected = plan.existingStructures();
			if ((!locked && !structures.equals(expected))
					|| (locked && !structures.entrySet().containsAll(expected.entrySet()))
					|| !graph(connection, original, plan.targets(), structures.keySet()).fingerprint()
							.equals(plan.graphFingerprint()))
				throw new TargetChanged();
		} catch (IllegalArgumentException changed) {
			// A new external reference or cycle also invalidates the reviewed target.
			throw new TargetChanged();
		}
	}

	private static String commentSql(String sql) {
		return sql.lines().map(line -> "-- " + line).collect(java.util.stream.Collectors.joining("\n"));
	}

	private static Outcome outcome(TransferJobPreparationService.Prepared current, Phase phase,
			BulkMigrationJobVerificationResult verified, TransferSchemaPreparation.Progress schema,
			TransferFailureDiagnostic diagnostic, TargetPlan plan, State state, Outcome copied, Map<String, Long> rows,
			List<String> cleaned, String stoppedTask) {
		return new Outcome(current, phase, null, verified, List.of(), Map.of(), stoppedTask, schema, diagnostic,
				new Status(state, plan.targets(), copied, state.committed() ? rows : Map.of(), cleaned));
	}

	private static BulkMigrationJobPlan resolve(Review review, Connection source) {
		return resolve(review.prepared().dataJob(), source);
	}

	static BulkMigrationJobPlan resolve(BulkMigrationJobConfigurationEditorIO.Snapshot dataJob, Connection source) {
		return new BulkMigrationJobConfigurationResolver()
				.resolveJob(dataJob.file().toFile(), source, dataJob.fingerprint(), 1_048_576L, 16_777_216L).plan();
	}

	private static BulkMigrationJobTask task(BulkMigrationJobPlan plan, String id) {
		return plan.getTasks().stream().filter(t -> t.getTaskId().equals(id)).findFirst().orElseThrow();
	}

	private static Target target(TargetPlan plan, String id) {
		return plan.targets().stream().filter(t -> t.taskId().equals(id)).findFirst().orElseThrow();
	}

	private static Target stageTarget(BulkMigrationJobTask task, String jobFingerprint, int ordinal) {
		var table = task.getEffectiveTargetTable();
		String stage = "SQLAPP_STG_" + hash(jobFingerprint).substring(0, 16) + "_" + ordinal;
		return new Target(task.getTaskId(), table.getCatalogName(), table.getSchemaName(), table.getName(), stage);
	}

	private static BulkMigrationVerificationResult compareTable(BulkMigrationJobTask task, Target names,
			Connection stageConnection, Connection targetConnection, int chunkSize) throws SQLException {
		var actual = task.getEffectiveTargetTable().clone();
		var stage = stageTable(actual, names, stageConnection);
		actual.setRowIteratorHandler(new JdbcDynamicRowIteratorHandler(targetConnection));
		var columns = BulkMigrationVerificationColumns.resolve(actual, task.getOptions().getMode(),
				task.getOptions().getBulkOption(), task.getOptions().getBulkUpsertOption());
		return BulkMigrationUnorderedVerifier.verify(stage, stage.getRows().iterator(), actual,
				actual.getRows().iterator(), columns, columns, chunkSize);
	}

	private static Table stageTable(Table template, Target target, Connection connection) {
		var stage = template.clone().setName(target.stageTable());
		stage.setRowIteratorHandler(new JdbcDynamicRowIteratorHandler(connection));
		return stage;
	}

	private static List<String> sql(Dialect dialect, Table table, SqlType type) {
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var statements = registry.createSql(table, type).stream().map(s -> s.getSqlText()).toList();
		if (statements.isEmpty())
			throw new IllegalArgumentException("No SQL factory for staged replacement operation " + type);
		return statements;
	}

	private static void executeSql(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static String hash(Object value) {
		try {
			return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
					.digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	private static Map<String, String> structures(Connection connection, List<Target> targets, boolean requireAll)
			throws SQLException {
		var result = new LinkedHashMap<String, String>();
		for (var target : targets) {
			var rows = new ArrayList<String>();
			try (var columns = connection.getMetaData().getColumns(target.catalog(), target.schema(), target.table(),
					null)) {
				while (columns.next()) {
					if (!target.table().equals(columns.getString("TABLE_NAME"))
							|| !Objects.equals(target.schema(), columns.getString("TABLE_SCHEM")))
						continue;
					rows.add(Arrays.asList(columns.getString("COLUMN_NAME"), columns.getInt("DATA_TYPE"),
							columns.getString("TYPE_NAME"), columns.getLong("COLUMN_SIZE"),
							columns.getInt("DECIMAL_DIGITS"), columns.getInt("NULLABLE"),
							columns.getString("COLUMN_DEF"), columns.getString("IS_AUTOINCREMENT"),
							columns.getString("IS_GENERATEDCOLUMN")).toString());
				}
			}
			if (rows.isEmpty()) {
				if (requireAll)
					throw new IllegalArgumentException("Final target table is missing.");
				continue;
			}
			try (var keys = connection.getMetaData().getPrimaryKeys(target.catalog(), target.schema(),
					target.table())) {
				while (keys.next())
					rows.add("PK:" + keys.getInt("KEY_SEQ") + ":" + keys.getString("COLUMN_NAME"));
			}
			Collections.sort(rows);
			result.put(target.taskId(), hash(rows));
		}
		return result;
	}

	/**
	 * JDBC has no portable set-based FK API; one exported-keys request per selected
	 * parent is required here.
	 */
	private static Graph graph(Connection connection, BulkMigrationJobPlan original, List<Target> targets,
			Set<String> existing) throws SQLException {
		var schema = new Schema(targets.getFirst().schema());
		var tables = new LinkedHashMap<String, Table>();
		for (var task : original.getTasks()) {
			var table = task.getEffectiveTargetTable().clone();
			table.getConstraints().clear();
			table.getChildRelations().clear();
			schema.getTables().add(table);
			tables.put(task.getTaskId(), table);
		}
		var edges = new TreeSet<String>();
		for (var parent : targets) {
			try (var keys = connection.getMetaData().getExportedKeys(parent.catalog(), parent.schema(),
					parent.table())) {
				while (keys.next()) {
					String childSchema = keys.getString("FKTABLE_SCHEM");
					String childName = keys.getString("FKTABLE_NAME");
					String childCatalog = keys.getString("FKTABLE_CAT");
					var child = targets.stream()
							.filter(t -> t.table().equals(childName) && Objects.equals(t.schema(), childSchema)
									&& (childCatalog == null || t.catalog() == null
											|| Objects.equals(t.catalog(), childCatalog)))
							.findFirst().orElseThrow(() -> new IllegalArgumentException(
									"Staged replacement would affect an unselected referencing table. Include the referencing table or choose a separate destination schema."));
					addEdge(tables, child.taskId(), parent.taskId(), keys.getString("FKCOLUMN_NAME"),
							keys.getString("PKCOLUMN_NAME"), edges);
				}
			}
		}
		// Missing tables will receive the reviewed Schema model's relationships during
		// CREATE.
		for (var childTask : original.getTasks()) {
			if (existing.contains(childTask.getTaskId()))
				continue;
			for (var fk : childTask.getEffectiveSourceTable().getConstraints().getForeignKeyConstraints()) {
				var parentTask = original.getTasks().stream()
						.filter(t -> SchemaUtils.isSameTable(t.getEffectiveSourceTable(), fk.getRelatedTable()))
						.findFirst();
				if (parentTask.isEmpty())
					throw new IllegalArgumentException(
							"Selected tables must include all foreign-key parents for staged replacement.");
				for (int i = 0; i < fk.getColumns().size(); i++)
					addEdge(tables, childTask.getTaskId(), parentTask.get().getTaskId(),
							childTask.getTargetColumnName(fk.getColumns().get(i).getName()),
							parentTask.get().getTargetColumnName(fk.getRelatedColumns().get(i).getName()), edges);
			}
		}
		var tasks = new ArrayList<BulkMigrationJobTask>();
		for (var entry : tables.entrySet())
			tasks.add(BulkMigrationJobTask.builder().taskId(entry.getKey()).sourceTable(entry.getValue()).fullScan(true)
					.requireEmptyTarget(true)
					.options(ChunkedBulkMigrationOption.builder().migrationId(entry.getKey())
							.mode(BulkMigrationMode.INSERT).resume(false)
							.checkpointMode(BulkMigrationCheckpointMode.CUSTOM).build())
					.build());
		var ordered = BulkMigrationJobPlanner.plan(tasks);
		return new Graph(ordered.getTaskIds(), hash(edges));
	}

	private static void addEdge(Map<String, Table> tables, String childId, String parentId, String childColumn,
			String parentColumn, Set<String> edges) {
		String edge = Arrays.asList(childId, parentId, childColumn, parentColumn).toString();
		if (!edges.add(edge))
			return;
		var child = tables.get(childId);
		var parent = tables.get(parentId);
		var c = child.getColumns().get(childColumn);
		var p = parent.getColumns().get(parentColumn);
		if (c == null || p == null)
			throw new IllegalArgumentException("Target foreign-key columns are outside the selected copy model.");
		child.getConstraints().addForeignKeyConstraint("STG_FK_" + hash(edge).substring(0, 16), c, p);
	}
}
