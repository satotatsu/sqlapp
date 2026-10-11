/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import com.sqlapp.data.db.command.migration.verification.MigrationCutoverAssessor;
import com.sqlapp.data.db.command.migration.verification.MigrationCutoverArtifactService;
import com.sqlapp.data.db.command.migration.verification.MigrationCutoverEvidence;
import com.sqlapp.data.db.command.migration.verification.MigrationCutoverEvidenceIO;
import com.sqlapp.data.db.command.migration.verification.MigrationCutoverReport;
import com.sqlapp.data.db.command.migration.verification.MigrationCutoverReportIO;
import com.sqlapp.data.db.command.migration.verification.MigrationDataTestPlanner;
import com.sqlapp.data.db.command.migration.verification.MigrationDataTestResult;
import com.sqlapp.data.db.command.migration.verification.MigrationDataTestRunner;
import com.sqlapp.data.db.command.migration.verification.MigrationNodeManifestIO;
import com.sqlapp.data.db.command.migration.schema.MigrationSchemaDriftAssessor;
import com.sqlapp.data.db.command.migration.verification.MigrationTransformationTestResult;
import com.sqlapp.data.db.command.migration.verification.MigrationTransformationTestRunner;
import com.sqlapp.data.db.command.migration.schema.Status;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationDataTest;
import com.sqlapp.data.schemas.migration.MigrationFreshnessCheck;
import com.sqlapp.data.schemas.migration.MigrationNodeManifest;
import com.sqlapp.data.schemas.migration.MigrationNodeSelection;
import com.sqlapp.data.schemas.migration.MigrationNodeStateSelector;
import com.sqlapp.data.schemas.migration.MigrationTransformationTest;
import com.sqlapp.data.schemas.migration.SchemaCompatibilityReport;
import com.sqlapp.jdbc.bulk.BulkMigrationJobExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobCheckpointManager;
import com.sqlapp.jdbc.bulk.BulkMigrationJobCheckpointResetResult;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseManager;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLifecycle;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatusInspector;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkMigrationIncrementalStrategy;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceRecoveryResult;
import com.sqlapp.jdbc.bulk.BulkMigrationRetryOption;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.CompositeBulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.DurableBulkMigrationJobLifecycle;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationJobLeaseStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationMaintenanceStateStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;

import lombok.Builder;

/**
 * Simple, safe facade for the usual execute, inspect, verify, and repair flow.
 * Advanced callers can continue to use the underlying bulk APIs directly.
 */
public final class BulkMigration {
	private final DataSource source;
	private final DataSource target;
	private final String jobId;
	private final List<Table> tables;
	private final BulkMigrationMode mode;
	private final BulkMigrationIncrementalStrategy incrementalStrategy;
	private final int chunkSize;
	private final boolean resume;
	private final String sourceFingerprint;
	private final String targetFingerprint;
	private final String checkpointTableName;
	private final BulkUpsertOption upsertOption;
	private final Map<String, BulkMigrationTableOption> tableOptions;
	private final BulkOption bulkOption;
	private final BulkMigrationRetryOption retryOption;
	private final BulkMigrationJobListener jobListener;
	private final ChunkedBulkMigrationListener chunkListener;
	private final BulkMigrationCheckpointMode checkpointMode;
	private final Path checkpointDirectory;
	private final BulkMigrationCheckpointStore checkpointStore;
	private final BulkMigrationJobLeaseConfiguration leaseConfiguration;
	private final BulkMigrationJobLifecycle lifecycle;
	private final Path maintenanceDirectory;
	private final String maintenanceTableName;
	private final Path operationalReportFile;
	private final Path executionReportFile;
	private final Long maxExecutionReportFileSizeBytes;
	private final Path verificationReportFile;
	private final Path repairPlanOnMismatchFile;
	private final int maxReportedMismatches;
	private final BulkMigrationVerificationIsolation verificationIsolation;
	private final Integer verificationChunkSize;
	private String operationalReportFingerprint;
	private String executionReportFingerprint;
	private BulkMigrationJobExecutionReport executionReport;
	private String verificationReportFingerprint;
	private String repairPlanReportFingerprint;
	private String approvedOperationalReportFingerprint;
	private String approvedRepairPlanReportFingerprint;
	private String nodeManifestFingerprint;
	private String approvedNodeManifestFingerprint;
	private String cutoverReportFingerprint;
	private String approvedCutoverReportFingerprint;
	private String approvedVerificationReportFingerprint;
	private String cutoverEvidenceFingerprint;
	private String approvedCutoverEvidenceFingerprint;

	public String getOperationalReportFingerprint() {
		return operationalReportFingerprint;
	}

	public String getExecutionReportFingerprint() {
		return executionReportFingerprint;
	}

	public BulkMigrationJobExecutionReport getExecutionReport() {
		return executionReport;
	}

	public String getVerificationReportFingerprint() {
		return verificationReportFingerprint;
	}

	public String getRepairPlanReportFingerprint() {
		return repairPlanReportFingerprint;
	}

	public String getApprovedOperationalReportFingerprint() {
		return approvedOperationalReportFingerprint;
	}

	public String getApprovedRepairPlanReportFingerprint() {
		return approvedRepairPlanReportFingerprint;
	}

	public String getNodeManifestFingerprint() {
		return nodeManifestFingerprint;
	}

	public String getApprovedNodeManifestFingerprint() {
		return approvedNodeManifestFingerprint;
	}

	public String getCutoverReportFingerprint() {
		return cutoverReportFingerprint;
	}

	public String getApprovedCutoverReportFingerprint() {
		return approvedCutoverReportFingerprint;
	}

	public String getApprovedVerificationReportFingerprint() {
		return approvedVerificationReportFingerprint;
	}

	public String getCutoverEvidenceFingerprint() {
		return cutoverEvidenceFingerprint;
	}

	public String getApprovedCutoverEvidenceFingerprint() {
		return approvedCutoverEvidenceFingerprint;
	}

	/** Creates a migration with the safe defaults for every table in the Schema. */
	public static BulkMigration of(final DataSource source, final DataSource target, final Schema schema) {
		return builder().source(Objects.requireNonNull(source, "source"))
				.target(Objects.requireNonNull(target, "target")).schema(Objects.requireNonNull(schema, "schema"))
				.build();
	}

	@Builder
	private BulkMigration(final DataSource source, final DataSource target, final Schema schema,
			final List<String> tableNames, final String jobId, final BulkMigrationMode mode,
			final BulkMigrationIncrementalStrategy incrementalStrategy, final Integer chunkSize, final Boolean resume,
			final String sourceFingerprint, final String targetFingerprint, final String checkpointTableName,
			final BulkUpsertOption upsertOption, final Map<String, BulkMigrationTableOption> tableOptions,
			final BulkOption bulkOption, final BulkMigrationRetryOption retryOption,
			final BulkMigrationJobListener jobListener, final ChunkedBulkMigrationListener chunkListener,
			final BulkMigrationCheckpointMode checkpointMode, final Path checkpointDirectory,
			final BulkMigrationCheckpointStore checkpointStore,
			final BulkMigrationJobLeaseConfiguration leaseConfiguration, final BulkMigrationJobLifecycle lifecycle,
			final Path maintenanceDirectory, final String maintenanceTableName, final Path operationalReportFile,
			final Path executionReportFile, final Long maxExecutionReportFileSizeBytes,
			final Path verificationReportFile, final Path repairPlanOnMismatchFile, final Integer maxReportedMismatches,
			final BulkMigrationVerificationIsolation verificationIsolation, final Integer verificationChunkSize) {
		this.source = Objects.requireNonNull(source, "source");
		this.target = Objects.requireNonNull(target, "target");
		this.jobId = jobId;
		this.tables = resolveTables(Objects.requireNonNull(schema, "schema"), tableNames);
		this.incrementalStrategy = incrementalStrategy == null
				? BulkMigrationIncrementalStrategy.fromMode(mode == null ? BulkMigrationMode.UPSERT : mode)
				: incrementalStrategy;
		if (!this.incrementalStrategy.isImplemented()) {
			throw new IllegalArgumentException("Incremental strategy is not implemented: " + this.incrementalStrategy);
		}
		this.mode = incrementalStrategy == null ? (mode == null ? BulkMigrationMode.UPSERT : mode)
				: incrementalStrategy.mode();
		this.chunkSize = chunkSize == null ? 10_000 : chunkSize;
		this.resume = resume != null && resume;
		this.sourceFingerprint = sourceFingerprint;
		this.targetFingerprint = targetFingerprint;
		this.checkpointTableName = checkpointTableName == null || checkpointTableName.isBlank()
				? "SQLAPP_BULK_MIGRATION_CHECKPOINT"
				: checkpointTableName;
		this.upsertOption = upsertOption == null ? BulkUpsertOption.defaults() : upsertOption;
		this.tableOptions = resolveTableOptions(this.tables, tableOptions);
		this.bulkOption = bulkOption == null ? BulkOption.defaults() : bulkOption;
		this.retryOption = retryOption == null ? BulkMigrationRetryOption.none() : retryOption;
		this.jobListener = jobListener == null ? BulkMigrationJobListener.NO_OP : jobListener;
		this.chunkListener = chunkListener == null ? ChunkedBulkMigrationListener.NO_OP : chunkListener;
		this.checkpointMode = checkpointMode == null ? BulkMigrationCheckpointMode.DATABASE : checkpointMode;
		this.checkpointDirectory = checkpointDirectory == null ? null
				: checkpointDirectory.toAbsolutePath().normalize();
		this.checkpointStore = checkpointStore;
		this.leaseConfiguration = leaseConfiguration;
		this.lifecycle = lifecycle == null ? BulkMigrationJobLifecycle.NO_OP : lifecycle;
		this.maintenanceDirectory = maintenanceDirectory == null ? null
				: maintenanceDirectory.toAbsolutePath().normalize();
		this.maintenanceTableName = maintenanceTableName;
		this.operationalReportFile = operationalReportFile == null ? null
				: operationalReportFile.toAbsolutePath().normalize();
		this.executionReportFile = executionReportFile == null ? null
				: executionReportFile.toAbsolutePath().normalize();
		if (maxExecutionReportFileSizeBytes != null && maxExecutionReportFileSizeBytes <= 0) {
			throw new IllegalArgumentException("maxExecutionReportFileSizeBytes must be greater than zero");
		}
		this.maxExecutionReportFileSizeBytes = maxExecutionReportFileSizeBytes;
		this.verificationReportFile = verificationReportFile == null ? null
				: verificationReportFile.toAbsolutePath().normalize();
		this.repairPlanOnMismatchFile = repairPlanOnMismatchFile == null ? null
				: repairPlanOnMismatchFile.toAbsolutePath().normalize();
		this.maxReportedMismatches = maxReportedMismatches == null
				? BulkMigrationVerificationReportIO.DEFAULT_MAX_REPORTED_MISMATCHES
				: maxReportedMismatches;
		this.verificationIsolation = verificationIsolation == null ? BulkMigrationVerificationIsolation.DEFAULT
				: verificationIsolation;
		this.verificationChunkSize = verificationChunkSize;
		validate();
	}

	/** Convenience aliases for the common builder inputs. */
	public static class BulkMigrationBuilder {
		public BulkMigrationBuilder fileCheckpoints(final Path directory) {
			this.checkpointMode = BulkMigrationCheckpointMode.FILE;
			this.checkpointDirectory = directory;
			this.checkpointStore = null;
			return this;
		}

		public BulkMigrationBuilder customCheckpointStore(final BulkMigrationCheckpointStore store) {
			this.checkpointMode = BulkMigrationCheckpointMode.CUSTOM;
			this.checkpointStore = store;
			this.checkpointDirectory = null;
			return this;
		}

		/** Prevents concurrent execution using a target-database lease. */
		public BulkMigrationBuilder databaseLease(final String ownerId) {
			this.leaseConfiguration = BulkMigrationJobLeaseConfiguration.database(ownerId);
			return this;
		}

		/**
		 * Prevents concurrent execution using a lease file outside the target database.
		 */
		public BulkMigrationBuilder fileLease(final String ownerId, final Path directory) {
			this.leaseConfiguration = BulkMigrationJobLeaseConfiguration.file(ownerId, directory);
			return this;
		}

		/** Records lifecycle recovery state in a shared directory. */
		public BulkMigrationBuilder fileMaintenance(final Path directory) {
			this.maintenanceDirectory = Objects.requireNonNull(directory, "directory");
			this.maintenanceTableName = null;
			return this;
		}

		/** Records lifecycle recovery state on a dedicated target connection. */
		public BulkMigrationBuilder databaseMaintenance() {
			return databaseMaintenance(JdbcBulkMigrationMaintenanceStateStore.DEFAULT_TABLE_NAME);
		}

		/** Records lifecycle recovery state in the specified target table. */
		public BulkMigrationBuilder databaseMaintenance(final String tableName) {
			this.maintenanceTableName = Objects.requireNonNull(tableName, "tableName");
			this.maintenanceDirectory = null;
			return this;
		}

		/** Writes an atomic JSON operational report at job and task boundaries. */
		public BulkMigrationBuilder operationalReport(final Path file) {
			this.operationalReportFile = Objects.requireNonNull(file, "file");
			return this;
		}

		/** Writes the committed migration result immediately after execution. */
		public BulkMigrationBuilder executionReport(final Path file) {
			this.executionReportFile = Objects.requireNonNull(file, "file");
			return this;
		}

		/** Writes the committed result with an explicit output-size bound. */
		public BulkMigrationBuilder executionReport(final Path file, final long maxFileSizeBytes) {
			this.executionReportFile = Objects.requireNonNull(file, "file");
			this.maxExecutionReportFileSizeBytes = maxFileSizeBytes;
			return this;
		}

		/** Writes the result of each explicit verification as bounded JSON. */
		public BulkMigrationBuilder verificationReport(final Path file) {
			this.verificationReportFile = Objects.requireNonNull(file, "file");
			return this;
		}

		/** Writes verification JSON while limiting retained mismatch details. */
		public BulkMigrationBuilder verificationReport(final Path file, final int maxMismatches) {
			this.verificationReportFile = Objects.requireNonNull(file, "file");
			this.maxReportedMismatches = maxMismatches;
			return this;
		}

		/**
		 * Writes a reviewable repair plan when {@link BulkMigration#run()} mismatches.
		 */
		public BulkMigrationBuilder repairPlanOnMismatch(final Path file) {
			this.repairPlanOnMismatchFile = Objects.requireNonNull(file, "file");
			return this;
		}

		public BulkMigrationBuilder tableOption(final String tableName, final BulkMigrationTableOption option) {
			if (this.tableOptions == null) {
				this.tableOptions = new LinkedHashMap<>();
			}
			this.tableOptions.put(tableName, option);
			return this;
		}

		public BulkMigrationBuilder tables(final String... names) {
			this.tableNames = names == null ? null : List.of(names);
			return this;
		}

		public BulkMigrationBuilder fingerprints(final String source, final String target) {
			this.sourceFingerprint = source;
			this.targetFingerprint = target;
			return this;
		}
	}

	public BulkMigrationJobResult execute() throws SQLException {
		clearPreviousExecutionReport();
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			if (maintenanceTableName == null) {
				return execute(sourceConnection, targetConnection, null);
			}
			try (Connection maintenanceConnection = target.getConnection()) {
				maintenanceConnection.setAutoCommit(true);
				return execute(sourceConnection, targetConnection, maintenanceConnection);
			}
		}
	}

	private BulkMigrationJobResult execute(final Connection sourceConnection, final Connection targetConnection,
			final Connection maintenanceConnection) throws SQLException {
		final BulkMigrationJobPlan plan = plan(sourceConnection, targetConnection, false, false, maintenanceConnection);
		final BulkMigrationJobResult result = executePlan(targetConnection, plan);
		writeExecutionReport(plan, result);
		return result;
	}

	private void clearPreviousExecutionReport() {
		executionReport = null;
		executionReportFingerprint = null;
		if (executionReportFile == null) {
			return;
		}
		try {
			Files.deleteIfExists(executionReportFile);
		} catch (IOException e) {
			throw new com.sqlapp.exceptions.CommandException(
					"Failed to clear previous bulk migration execution report: " + executionReportFile, e);
		}
	}

	private void writeExecutionReport(final BulkMigrationJobPlan plan, final BulkMigrationJobResult result) {
		if (executionReportFile == null) {
			return;
		}
		try {
			final var io = new BulkMigrationJobExecutionReportIO();
			final var snapshot = io.writeSnapshot(executionReportFile, io.fromResult(plan, result, null),
					maxExecutionReportFileSizeBytes);
			executionReport = snapshot.report();
			executionReportFingerprint = snapshot.fingerprint();
		} catch (RuntimeException failure) {
			throw new BulkMigrationExecutionReportException(result, failure);
		}
	}

	private BulkMigrationJobResult executePlan(final Connection targetConnection, final BulkMigrationJobPlan plan)
			throws SQLException {
		final BulkMigrationJobListener executionListener = executionListener(plan);
		if (leaseConfiguration == null) {
			return BulkMigrationJobExecutor.executePlan(targetConnection, plan, executionListener, chunkListener);
		}
		if (leaseConfiguration.mode() == BulkMigrationJobLeaseMode.FILE) {
			final BulkMigrationJobLeaseManager manager = BulkMigrationJobLeaseManagerFactory.create(null,
					leaseConfiguration);
			return BulkMigrationJobExecutor.executePlan(targetConnection, plan, executionListener, chunkListener,
					manager);
		}
		try (Connection leaseConnection = target.getConnection()) {
			leaseConnection.setAutoCommit(true);
			final BulkMigrationJobLeaseManager manager = BulkMigrationJobLeaseManagerFactory.create(leaseConnection,
					leaseConfiguration);
			return BulkMigrationJobExecutor.executePlan(targetConnection, plan, executionListener, chunkListener,
					manager);
		}
	}

	/** Returns per-table fingerprints and FK dependency IDs without executing. */
	public MigrationNodeManifest nodeManifest() throws SQLException {
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			return plan(sourceConnection, targetConnection, true).getNodeManifest();
		}
	}

	/** Writes the current node state as an atomic, reviewable JSON artifact. */
	public MigrationNodeManifest writeNodeManifest(final Path file) throws SQLException {
		final MigrationNodeManifest manifest = nodeManifest();
		final var snapshot = new MigrationNodeManifestIO().writeSnapshot(file, manifest, null);
		nodeManifestFingerprint = snapshot.fingerprint();
		return snapshot.manifest();
	}

	/** Executes changes relative to a previously persisted node manifest. */
	public StateExecution executeModified(final Path previousManifest) throws SQLException {
		return executeModified(previousManifest, null, null);
	}

	/** Adds optional SHA-256 and bounded-read checks to state-aware execution. */
	public StateExecution executeModified(final Path previousManifest, final String expectedManifestFingerprint,
			final Long maxNodeManifestFileSizeBytes) throws SQLException {
		if (expectedManifestFingerprint != null && !expectedManifestFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new IllegalArgumentException("expectedManifestFingerprint must be a lowercase SHA-256 value");
		}
		final var snapshot = new MigrationNodeManifestIO().readSnapshot(previousManifest, maxNodeManifestFileSizeBytes);
		if (expectedManifestFingerprint != null && !expectedManifestFingerprint.equals(snapshot.fingerprint())) {
			throw new IllegalArgumentException("Migration node manifest fingerprint does not match");
		}
		approvedNodeManifestFingerprint = snapshot.fingerprint();
		return executeModified(snapshot.manifest());
	}

	/** Executes changed nodes and atomically writes the state for the next run. */
	public StateExecution executeModifiedAndWriteManifest(final Path previousManifest, final Path currentManifest)
			throws SQLException {
		return executeModifiedAndWriteManifest(previousManifest, currentManifest, null, null);
	}

	/**
	 * Adds input SHA-256 and bounded-read checks to the state rollover operation.
	 */
	public StateExecution executeModifiedAndWriteManifest(final Path previousManifest, final Path currentManifest,
			final String expectedPreviousManifestFingerprint, final Long maxPreviousManifestFileSizeBytes)
			throws SQLException {
		final StateExecution execution = executeModified(previousManifest, expectedPreviousManifestFingerprint,
				maxPreviousManifestFileSizeBytes);
		final var snapshot = new MigrationNodeManifestIO()
				.writeSnapshot(Objects.requireNonNull(currentManifest, "currentManifest"), execution.current(), null);
		nodeManifestFingerprint = snapshot.fingerprint();
		return execution;
	}

	/** Assesses watermark lag and verification recency for a cutover decision. */
	public MigrationCutoverReport assessCutover(final List<MigrationFreshnessCheck> checks,
			final Instant lastVerifiedAt, final Duration maximumVerificationAge) throws SQLException {
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			return MigrationCutoverAssessor.assess(sourceConnection, targetConnection, List.copyOf(checks),
					lastVerifiedAt, maximumVerificationAge, Instant.now());
		}
	}

	/** Assesses cutover readiness and atomically writes the measured evidence. */
	public MigrationCutoverReport assessCutover(final List<MigrationFreshnessCheck> checks,
			final Instant lastVerifiedAt, final Duration maximumVerificationAge, final Path reportFile)
			throws SQLException {
		return assessCutover(checks, lastVerifiedAt, maximumVerificationAge, reportFile, null);
	}

	/** Adds an optional output-size limit to persisted cutover evidence. */
	public MigrationCutoverReport assessCutover(final List<MigrationFreshnessCheck> checks,
			final Instant lastVerifiedAt, final Duration maximumVerificationAge, final Path reportFile,
			final Long maxCutoverReportFileSizeBytes) throws SQLException {
		final MigrationCutoverReport report = assessCutover(checks, lastVerifiedAt, maximumVerificationAge);
		final var snapshot = new MigrationCutoverReportIO()
				.writeSnapshot(Objects.requireNonNull(reportFile, "reportFile"), report, maxCutoverReportFileSizeBytes);
		cutoverReportFingerprint = snapshot.fingerprint();
		return snapshot.report();
	}

	/** Uses a matching successful verification artifact as cutover evidence. */
	public MigrationCutoverReport assessCutover(final List<MigrationFreshnessCheck> checks,
			final Path verificationReport, final Duration maximumVerificationAge, final Path cutoverReport)
			throws SQLException {
		return assessCutover(checks, verificationReport, null, maximumVerificationAge, null, cutoverReport, null);
	}

	/** Adds SHA-256 and size checks to verification-bound cutover assessment. */
	public MigrationCutoverReport assessCutover(final List<MigrationFreshnessCheck> checks,
			final Path verificationReport, final String expectedVerificationReportFingerprint,
			final Duration maximumVerificationAge, final Long maxVerificationReportFileSizeBytes,
			final Path cutoverReport, final Long maxCutoverReportFileSizeBytes) throws SQLException {
		if (expectedVerificationReportFingerprint != null
				&& !expectedVerificationReportFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new IllegalArgumentException(
					"expectedVerificationReportFingerprint must be a lowercase SHA-256 value");
		}
		final var verificationSnapshot = new BulkMigrationVerificationReportIO().readSnapshot(
				Objects.requireNonNull(verificationReport, "verificationReport"), maxVerificationReportFileSizeBytes);
		if (expectedVerificationReportFingerprint != null
				&& !expectedVerificationReportFingerprint.equals(verificationSnapshot.fingerprint())) {
			throw new IllegalArgumentException("Bulk migration verification report fingerprint does not match");
		}
		final var verification = verificationSnapshot.report();
		if (!verification.match()) {
			throw new IllegalStateException("Bulk migration verification report is not a successful match");
		}
		final MigrationCutoverReport assessed;
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			final BulkMigrationJobPlan currentPlan = plan(sourceConnection, targetConnection, true);
			if (!currentPlan.getFingerprint().equals(verification.planFingerprint())) {
				throw new IllegalArgumentException(
						"Bulk migration verification report does not match the current migration plan");
			}
			assessed = MigrationCutoverAssessor.assess(sourceConnection, targetConnection, List.copyOf(checks),
					verification.generatedAt(), maximumVerificationAge, Instant.now());
		}
		final var cutoverSnapshot = new MigrationCutoverReportIO().writeSnapshot(
				Objects.requireNonNull(cutoverReport, "cutoverReport"), assessed, maxCutoverReportFileSizeBytes);
		approvedVerificationReportFingerprint = verificationSnapshot.fingerprint();
		cutoverReportFingerprint = cutoverSnapshot.fingerprint();
		return cutoverSnapshot.report();
	}

	/** Validates an exact, recent READY decision for an external cutover gate. */
	public MigrationCutoverReport approveCutover(final Path reportFile, final String expectedReportFingerprint,
			final Duration maximumReportAge, final Long maxCutoverReportFileSizeBytes) {
		if (expectedReportFingerprint == null || !expectedReportFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new IllegalArgumentException("expectedReportFingerprint must be a lowercase SHA-256 value");
		}
		if (maximumReportAge == null || maximumReportAge.isZero() || maximumReportAge.isNegative()) {
			throw new IllegalArgumentException("maximumReportAge must be greater than zero");
		}
		final var snapshot = new MigrationCutoverReportIO()
				.readSnapshot(Objects.requireNonNull(reportFile, "reportFile"), maxCutoverReportFileSizeBytes);
		if (!expectedReportFingerprint.equals(snapshot.fingerprint())) {
			throw new IllegalArgumentException("Migration cutover report fingerprint does not match");
		}
		final MigrationCutoverReport report = snapshot.report();
		final Instant now = Instant.now();
		if (report.assessedAt().isAfter(now)) {
			throw new IllegalArgumentException("Migration cutover report assessedAt is in the future");
		}
		try {
			if (report.assessedAt().plus(maximumReportAge).isBefore(now)) {
				throw new IllegalArgumentException("Migration cutover report has expired");
			}
		} catch (java.time.DateTimeException | ArithmeticException e) {
			throw new IllegalArgumentException("maximumReportAge is outside the supported time range", e);
		}
		if (report.status() != MigrationCutoverReport.Status.READY) {
			throw new IllegalStateException("Migration cutover report is not READY: " + report.status());
		}
		approvedCutoverReportFingerprint = snapshot.fingerprint();
		return report;
	}

	/**
	 * Assesses cutover and writes a portable link to both input and output reports.
	 */
	public MigrationCutoverEvidence assessCutoverAndWriteEvidence(final List<MigrationFreshnessCheck> checks,
			final Path verificationReport, final Duration maximumVerificationAge, final Path cutoverReport,
			final Path evidenceFile) throws SQLException {
		return assessCutoverAndWriteEvidence(checks, verificationReport, null, maximumVerificationAge, null,
				cutoverReport, null, evidenceFile, null);
	}

	/**
	 * Adds exact-input SHA-256 and bounded-file checks to cutover evidence
	 * creation.
	 */
	public MigrationCutoverEvidence assessCutoverAndWriteEvidence(final List<MigrationFreshnessCheck> checks,
			final Path verificationReport, final String expectedVerificationReportFingerprint,
			final Duration maximumVerificationAge, final Long maxVerificationReportFileSizeBytes,
			final Path cutoverReport, final Long maxCutoverReportFileSizeBytes, final Path evidenceFile,
			final Long maxCutoverEvidenceFileSizeBytes) throws SQLException {
		final MigrationCutoverReport cutover = assessCutover(checks, verificationReport,
				expectedVerificationReportFingerprint, maximumVerificationAge, maxVerificationReportFileSizeBytes,
				cutoverReport, maxCutoverReportFileSizeBytes);
		final var verificationSnapshot = new BulkMigrationVerificationReportIO().readSnapshot(verificationReport,
				maxVerificationReportFileSizeBytes);
		final var cutoverSnapshot = new MigrationCutoverReportIO().readSnapshot(cutoverReport,
				maxCutoverReportFileSizeBytes);
		if (!verificationSnapshot.fingerprint().equals(approvedVerificationReportFingerprint)
				|| !cutoverSnapshot.fingerprint().equals(cutoverReportFingerprint)) {
			throw new IllegalStateException("Cutover reports changed while creating linked evidence");
		}
		final var evidence = new MigrationCutoverEvidence(MigrationCutoverEvidence.CURRENT_FORMAT_VERSION,
				Instant.now(), verificationSnapshot.report().planFingerprint(), verificationSnapshot.fingerprint(),
				verificationSnapshot.report().generatedAt(), cutoverSnapshot.fingerprint(), cutover.assessedAt(),
				cutover.status());
		final var snapshot = new MigrationCutoverEvidenceIO().writeSnapshot(
				Objects.requireNonNull(evidenceFile, "evidenceFile"), evidence, maxCutoverEvidenceFileSizeBytes);
		cutoverEvidenceFingerprint = snapshot.fingerprint();
		return snapshot.evidence();
	}

	/**
	 * Approves an exact evidence bundle only when both referenced reports still
	 * match.
	 */
	public MigrationCutoverEvidence approveCutoverEvidence(final Path evidenceFile,
			final String expectedEvidenceFingerprint, final Path verificationReport, final Path cutoverReport,
			final Duration maximumReportAge) {
		return approveCutoverEvidence(evidenceFile, expectedEvidenceFingerprint, verificationReport, cutoverReport,
				maximumReportAge, null, null, null);
	}

	/** Adds independent bounded-file checks to linked cutover approval. */
	public MigrationCutoverEvidence approveCutoverEvidence(final Path evidenceFile,
			final String expectedEvidenceFingerprint, final Path verificationReport, final Path cutoverReport,
			final Duration maximumReportAge, final Long maxCutoverEvidenceFileSizeBytes,
			final Long maxVerificationReportFileSizeBytes, final Long maxCutoverReportFileSizeBytes) {
		if (expectedEvidenceFingerprint == null || !expectedEvidenceFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new IllegalArgumentException("expectedEvidenceFingerprint must be a lowercase SHA-256 value");
		}
		final var inspected = new MigrationCutoverArtifactService().approve(evidenceFile, expectedEvidenceFingerprint,
				verificationReport, cutoverReport, maximumReportAge, maxCutoverEvidenceFileSizeBytes,
				maxVerificationReportFileSizeBytes, maxCutoverReportFileSizeBytes);
		final CutoverPackageInspection inspection = toInspection(inspected);
		approvedVerificationReportFingerprint = inspection.evidence().verificationReportFingerprint();
		approvedCutoverReportFingerprint = inspection.evidence().cutoverReportFingerprint();
		approvedCutoverEvidenceFingerprint = inspection.evidenceFingerprint();
		return inspection.evidence();
	}

	/**
	 * Creates a self-contained cutover package and publishes it as one directory.
	 */
	public CutoverPackage assessCutoverPackage(final List<MigrationFreshnessCheck> checks,
			final Path verificationReport, final Duration maximumVerificationAge, final Path packageDirectory)
			throws SQLException {
		return assessCutoverPackage(checks, verificationReport, null, maximumVerificationAge, null, packageDirectory,
				null, null);
	}

	/**
	 * Adds verification SHA-256 and independent file-size limits to package
	 * publication.
	 */
	public CutoverPackage assessCutoverPackage(final List<MigrationFreshnessCheck> checks,
			final Path verificationReport, final String expectedVerificationReportFingerprint,
			final Duration maximumVerificationAge, final Long maxVerificationReportFileSizeBytes,
			final Path packageDirectory, final Long maxCutoverReportFileSizeBytes,
			final Long maxCutoverEvidenceFileSizeBytes) throws SQLException {
		final Path sourceVerification = Objects.requireNonNull(verificationReport, "verificationReport")
				.toAbsolutePath().normalize();
		final CutoverPackage[] result = new CutoverPackage[1];
		final String previousApprovedVerification = approvedVerificationReportFingerprint;
		final String previousCutover = cutoverReportFingerprint;
		final String previousEvidence = cutoverEvidenceFingerprint;
		try {
			AtomicMigrationDirectory.writeNew(Objects.requireNonNull(packageDirectory, "packageDirectory"), staging -> {
				final Path packagedVerification = staging
						.resolve(MigrationCutoverArtifactService.VERIFICATION_REPORT_FILE);
				Files.copy(sourceVerification, packagedVerification, StandardCopyOption.COPY_ATTRIBUTES);
				final Path packagedCutover = staging.resolve(MigrationCutoverArtifactService.CUTOVER_REPORT_FILE);
				final Path packagedEvidence = staging.resolve(MigrationCutoverArtifactService.EVIDENCE_FILE);
				final MigrationCutoverEvidence evidence = assessCutoverAndWriteEvidence(checks, packagedVerification,
						expectedVerificationReportFingerprint, maximumVerificationAge,
						maxVerificationReportFileSizeBytes, packagedCutover, maxCutoverReportFileSizeBytes,
						packagedEvidence, maxCutoverEvidenceFileSizeBytes);
				new MigrationCutoverArtifactService().validatePackageDirectory(staging);
				result[0] = new CutoverPackage(evidence, cutoverEvidenceFingerprint);
			});
			return result[0];
		} catch (SQLException e) {
			restoreCutoverFingerprints(previousApprovedVerification, previousCutover, previousEvidence);
			throw e;
		} catch (IOException e) {
			restoreCutoverFingerprints(previousApprovedVerification, previousCutover, previousEvidence);
			throw new com.sqlapp.exceptions.CommandException("Failed to publish migration cutover package", e);
		} catch (RuntimeException e) {
			restoreCutoverFingerprints(previousApprovedVerification, previousCutover, previousEvidence);
			throw e;
		} catch (Exception e) {
			restoreCutoverFingerprints(previousApprovedVerification, previousCutover, previousEvidence);
			throw new com.sqlapp.exceptions.CommandException("Failed to publish migration cutover package", e);
		}
	}

	private void restoreCutoverFingerprints(final String approvedVerification, final String cutover,
			final String evidence) {
		approvedVerificationReportFingerprint = approvedVerification;
		cutoverReportFingerprint = cutover;
		cutoverEvidenceFingerprint = evidence;
	}

	/** Reads and cross-checks a package for review without approving it. */
	public CutoverPackageInspection inspectCutoverPackage(final Path packageDirectory) {
		return inspectCutoverPackage(packageDirectory, null, null, null);
	}

	/** Adds independent bounded-file checks to package inspection. */
	public CutoverPackageInspection inspectCutoverPackage(final Path packageDirectory,
			final Long maxCutoverEvidenceFileSizeBytes, final Long maxVerificationReportFileSizeBytes,
			final Long maxCutoverReportFileSizeBytes) {
		return toInspection(new MigrationCutoverArtifactService().inspectPackage(packageDirectory,
				maxCutoverEvidenceFileSizeBytes, maxVerificationReportFileSizeBytes, maxCutoverReportFileSizeBytes));
	}

	/** Validates all reports in a package without opening database connections. */
	public MigrationCutoverEvidence approveCutoverPackage(final Path packageDirectory,
			final String expectedEvidenceFingerprint, final Duration maximumReportAge) {
		return approveCutoverPackage(packageDirectory, expectedEvidenceFingerprint, maximumReportAge, null, null, null);
	}

	/** Adds independent bounded-file checks to package approval. */
	public MigrationCutoverEvidence approveCutoverPackage(final Path packageDirectory,
			final String expectedEvidenceFingerprint, final Duration maximumReportAge,
			final Long maxCutoverEvidenceFileSizeBytes, final Long maxVerificationReportFileSizeBytes,
			final Long maxCutoverReportFileSizeBytes) {
		final var inspection = new MigrationCutoverArtifactService().approvePackage(packageDirectory,
				expectedEvidenceFingerprint, maximumReportAge, maxCutoverEvidenceFileSizeBytes,
				maxVerificationReportFileSizeBytes, maxCutoverReportFileSizeBytes);
		approvedVerificationReportFingerprint = inspection.evidence().verificationReportFingerprint();
		approvedCutoverReportFingerprint = inspection.evidence().cutoverReportFingerprint();
		approvedCutoverEvidenceFingerprint = inspection.evidenceFingerprint();
		return inspection.evidence();
	}

	private static CutoverPackageInspection toInspection(final MigrationCutoverArtifactService.Inspection inspection) {
		return new CutoverPackageInspection(inspection.evidence(), inspection.evidenceFingerprint(),
				inspection.verification(), inspection.cutover());
	}

	/**
	 * Executes only added/modified tables and their transitive FK dependents.
	 * Removed nodes are reported but never translated into destructive work.
	 */
	public StateExecution executeModified(final MigrationNodeManifest previous) throws SQLException {
		Objects.requireNonNull(previous, "previous");
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			if (maintenanceTableName == null) {
				return executeModified(sourceConnection, targetConnection, null, previous);
			}
			try (Connection maintenanceConnection = target.getConnection()) {
				maintenanceConnection.setAutoCommit(true);
				return executeModified(sourceConnection, targetConnection, maintenanceConnection, previous);
			}
		}
	}

	private StateExecution executeModified(final Connection sourceConnection, final Connection targetConnection,
			final Connection maintenanceConnection, final MigrationNodeManifest previous) throws SQLException {
		final BulkMigrationJobPlan fullPlan = plan(sourceConnection, targetConnection, false, false,
				maintenanceConnection);
		final MigrationNodeManifest current = fullPlan.getNodeManifest();
		final MigrationNodeSelection selection = MigrationNodeStateSelector.modifiedAndDownstream(previous, current);
		if (selection.selected().isEmpty()) {
			return new StateExecution(current, selection, null);
		}
		final BulkMigrationJobPlan selectedPlan = fullPlan.selectTasks(selection.selected());
		return new StateExecution(current, selection, executePlan(targetConnection, selectedPlan));
	}

	/** Returns a detached, read-only plan and status snapshot without executing. */
	public BulkMigrationOperationalReport dryRun() throws SQLException {
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			final BulkMigrationJobPlan readOnlyPlan = plan(sourceConnection, targetConnection, true);
			final BulkMigrationJobStatus status = BulkMigrationJobStatusInspector.inspect(readOnlyPlan);
			return new BulkMigrationOperationalReportBuilder().build(readOnlyPlan, status,
					maintenanceState(readOnlyPlan), null);
		}
	}

	/** Writes and returns the same detached dry-run snapshot as JSON. */
	public BulkMigrationOperationalReport dryRun(final Path reportFile) throws SQLException {
		final Path file = Objects.requireNonNull(reportFile, "reportFile");
		final BulkMigrationOperationalReport report = dryRun();
		final var snapshot = new BulkMigrationOperationalReportIO().writeSnapshot(file, report, null);
		operationalReportFingerprint = snapshot.fingerprint();
		return snapshot.report();
	}

	/**
	 * Deletes only this job's checkpoints after exact plan-fingerprint approval.
	 * Migrated target rows are not changed.
	 */
	public BulkMigrationJobCheckpointResetResult resetCheckpointsWithFingerprint(final String approvedPlanFingerprint)
			throws SQLException {
		if (approvedPlanFingerprint == null || approvedPlanFingerprint.isBlank()) {
			throw new IllegalArgumentException("approvedPlanFingerprint must not be empty");
		}
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			final BulkMigrationJobPlan reviewedPlan = plan(sourceConnection, targetConnection, true);
			if (!reviewedPlan.getFingerprint().equals(approvedPlanFingerprint)) {
				throw new IllegalArgumentException("Approved plan fingerprint does not match the migration job plan");
			}
			if (leaseConfiguration == null) {
				return resetCheckpoints(sourceConnection, targetConnection, approvedPlanFingerprint);
			}
			if (leaseConfiguration.mode() == BulkMigrationJobLeaseMode.FILE) {
				final var manager = BulkMigrationJobLeaseManagerFactory.create(null, leaseConfiguration);
				try (var ignored = manager.acquire(reviewedPlan.getJobId(), reviewedPlan.getFingerprint())) {
					return resetCheckpoints(sourceConnection, targetConnection, approvedPlanFingerprint);
				}
			}
			try (Connection leaseConnection = target.getConnection()) {
				leaseConnection.setAutoCommit(true);
				final var manager = BulkMigrationJobLeaseManagerFactory.create(leaseConnection, leaseConfiguration);
				try (var ignored = manager.acquire(reviewedPlan.getJobId(), reviewedPlan.getFingerprint())) {
					return resetCheckpoints(sourceConnection, targetConnection, approvedPlanFingerprint);
				}
			}
		}
	}

	private BulkMigrationJobCheckpointResetResult resetCheckpoints(final Connection sourceConnection,
			final Connection targetConnection, final String approvedPlanFingerprint) throws SQLException {
		final BulkMigrationJobPlan resetPlan = plan(sourceConnection, targetConnection, false);
		return BulkMigrationJobCheckpointManager.reset(resetPlan, approvedPlanFingerprint);
	}

	/**
	 * Reads a reviewed dry-run JSON report and resets the exact current plan's
	 * checkpoints.
	 */
	public BulkMigrationJobCheckpointResetResult resetCheckpoints(final Path approvedDryRunReport) throws SQLException {
		return resetCheckpoints(approvedDryRunReport, null, null);
	}

	/** Adds optional SHA-256 and bounded-read checks to report-based reset. */
	public BulkMigrationJobCheckpointResetResult resetCheckpoints(final Path approvedDryRunReport,
			final String expectedReportFingerprint, final Long maxEvidenceFileSizeBytes) throws SQLException {
		final Path reportFile = Objects.requireNonNull(approvedDryRunReport, "approvedDryRunReport");
		final var snapshot = approvedOperationalReport(reportFile, expectedReportFingerprint, maxEvidenceFileSizeBytes);
		approvedOperationalReportFingerprint = snapshot.fingerprint();
		return resetCheckpointsWithFingerprint(snapshot.report().planFingerprint());
	}

	/** Restores interrupted maintenance after exact plan-fingerprint approval. */
	public BulkMigrationMaintenanceRecoveryResult recoverMaintenanceWithFingerprint(
			final String approvedPlanFingerprint) throws SQLException {
		if (approvedPlanFingerprint == null || approvedPlanFingerprint.isBlank()) {
			throw new IllegalArgumentException("approvedPlanFingerprint must not be empty");
		}
		if (maintenanceDirectory == null && maintenanceTableName == null) {
			throw new IllegalStateException("File or database maintenance must be configured");
		}
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			if (maintenanceTableName == null) {
				return recoverMaintenance(sourceConnection, targetConnection, null, approvedPlanFingerprint);
			}
			try (Connection maintenanceConnection = target.getConnection()) {
				maintenanceConnection.setAutoCommit(true);
				return recoverMaintenance(sourceConnection, targetConnection, maintenanceConnection,
						approvedPlanFingerprint);
			}
		}
	}

	/** Restores maintenance approved by a previously reviewed dry-run report. */
	public BulkMigrationMaintenanceRecoveryResult recoverMaintenance(final Path approvedDryRunReport)
			throws SQLException {
		return recoverMaintenance(approvedDryRunReport, null, null);
	}

	/** Adds optional SHA-256 and bounded-read checks to report-based recovery. */
	public BulkMigrationMaintenanceRecoveryResult recoverMaintenance(final Path approvedDryRunReport,
			final String expectedReportFingerprint, final Long maxEvidenceFileSizeBytes) throws SQLException {
		final Path reportFile = Objects.requireNonNull(approvedDryRunReport, "approvedDryRunReport");
		final var snapshot = approvedOperationalReport(reportFile, expectedReportFingerprint, maxEvidenceFileSizeBytes);
		approvedOperationalReportFingerprint = snapshot.fingerprint();
		return recoverMaintenanceWithFingerprint(snapshot.report().planFingerprint());
	}

	private static BulkMigrationOperationalReportIO.Snapshot approvedOperationalReport(final Path reportFile,
			final String expectedFingerprint, final Long maxFileSizeBytes) {
		if (expectedFingerprint != null && !expectedFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new IllegalArgumentException("expectedReportFingerprint must be a lowercase SHA-256 value");
		}
		final var snapshot = new BulkMigrationOperationalReportIO().readSnapshot(reportFile, maxFileSizeBytes);
		if (expectedFingerprint != null && !expectedFingerprint.equals(snapshot.fingerprint())) {
			throw new IllegalArgumentException("Approved operational report fingerprint does not match");
		}
		return snapshot;
	}

	private BulkMigrationMaintenanceRecoveryResult recoverMaintenance(final Connection sourceConnection,
			final Connection targetConnection, final Connection maintenanceConnection,
			final String approvedPlanFingerprint) throws SQLException {
		final BulkMigrationJobPlan recoveryPlan = plan(sourceConnection, targetConnection, true, false,
				maintenanceConnection);
		if (!(recoveryPlan.getLifecycle() instanceof DurableBulkMigrationJobLifecycle durable)) {
			throw new IllegalStateException("Durable maintenance lifecycle was not configured");
		}
		return durable.recoverInterrupted(targetConnection, recoveryPlan, approvedPlanFingerprint);
	}

	private BulkMigrationJobListener executionListener(final BulkMigrationJobPlan plan) {
		if (operationalReportFile == null) {
			return jobListener;
		}
		final BulkMigrationJobListener report = new BulkMigrationOperationalReportJobListener(plan,
				operationalReportFile, () -> maintenanceStateUnchecked(plan), () -> null);
		return jobListener == BulkMigrationJobListener.NO_OP ? report
				: CompositeBulkMigrationJobListener.of(jobListener, report);
	}

	private static com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState maintenanceStateUnchecked(
			final BulkMigrationJobPlan plan) {
		try {
			return maintenanceState(plan);
		} catch (SQLException e) {
			throw new com.sqlapp.exceptions.CommandException("Failed to inspect migration maintenance state for report",
					e);
		}
	}

	/** Executes the migration and immediately verifies the resulting target. */
	public Execution executeAndVerify() throws SQLException {
		final BulkMigrationJobResult execution = execute();
		return new Execution(execution, verify());
	}

	/**
	 * Runs the usual safe workflow: execute, verify, and fail when verification
	 * does not match.
	 */
	public Execution run() throws SQLException {
		final BulkMigrationJobResult execution = execute();
		try {
			return new Execution(execution, verify()).requireMatch();
		} catch (BulkMigrationVerificationMismatchException failure) {
			writeRepairPlanOnMismatch(failure);
			attachOperationalFailure(failure);
			throw failure;
		} catch (SQLException | RuntimeException failure) {
			final var postExecution = new BulkMigrationPostExecutionException(execution, failure);
			attachOperationalFailure(postExecution);
			throw postExecution;
		} catch (Error failure) {
			attachOperationalFailure(failure);
			throw failure;
		}
	}

	/**
	 * Runs Schema-derived invariants against the current target without mutation.
	 */
	public List<MigrationDataTestResult> testTarget() throws SQLException {
		return testTarget(MigrationDataTestPlanner.infer(tables));
	}

	/**
	 * Runs supplied invariants against the current target. Custom SQL uses the
	 * standard sqlapp comment-template syntax.
	 */
	public List<MigrationDataTestResult> testTarget(final List<MigrationDataTest> tests) throws SQLException {
		try (Connection connection = target.getConnection()) {
			return MigrationDataTestRunner.run(connection, List.copyOf(tests));
		}
	}

	/** Runs SQL transformation fixtures against the source without mutation. */
	public List<MigrationTransformationTestResult> testSourceTransformations(
			final List<MigrationTransformationTest> tests) throws SQLException {
		try (Connection connection = source.getConnection()) {
			return MigrationTransformationTestRunner.run(connection, List.copyOf(tests));
		}
	}

	/**
	 * Runs transformation fixtures and data invariants before migration, then the
	 * normal verified migration and target data invariants. Transformation SQL uses
	 * the standard sqlapp comment-template syntax.
	 */
	public ComprehensiveValidatedExecution runWithValidation(final List<MigrationTransformationTest> transformations,
			final List<MigrationDataTest> dataTests) throws SQLException {
		final List<MigrationTransformationTest> immutableTransformations = List.copyOf(transformations);
		final List<MigrationDataTest> immutableDataTests = List.copyOf(dataTests);
		final List<MigrationTransformationTestResult> transformationResults;
		final List<MigrationDataTestResult> preflight;
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			requireNoBreakingDrift(MigrationSchemaDriftAssessor.assess(targetConnection, tables));
			transformationResults = MigrationTransformationTestRunner.run(sourceConnection, immutableTransformations);
			requireTransformationTests(transformationResults);
			preflight = MigrationDataTestRunner.run(sourceConnection, immutableDataTests);
		}
		requireDataTests("source preflight", preflight);
		final Execution execution = run();
		final List<MigrationDataTestResult> postflight = testTarget(immutableDataTests);
		requireDataTests("target postflight", postflight);
		return new ComprehensiveValidatedExecution(execution, transformationResults, preflight, postflight);
	}

	/**
	 * Runs source preflight tests, the usual safe migration workflow, then target
	 * postflight tests. An ERROR result prevents execution or completion.
	 */
	public ValidatedExecution runWithDataTests(final List<MigrationDataTest> tests) throws SQLException {
		final List<MigrationDataTest> immutable = List.copyOf(tests);
		final List<MigrationDataTestResult> preflight;
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			requireNoBreakingDrift(MigrationSchemaDriftAssessor.assess(targetConnection, tables));
			preflight = MigrationDataTestRunner.run(sourceConnection, immutable);
		}
		requireDataTests("source preflight", preflight);
		final Execution execution = run();
		final List<MigrationDataTestResult> postflight = testTarget(immutable);
		requireDataTests("target postflight", postflight);
		return new ValidatedExecution(execution, preflight, postflight);
	}

	/**
	 * Reads the current target definition and compares it with migration tables.
	 */
	public SchemaCompatibilityReport assessTargetDrift() throws SQLException {
		try (Connection connection = target.getConnection()) {
			return MigrationSchemaDriftAssessor.assess(connection, tables);
		}
	}

	private static void requireNoBreakingDrift(final SchemaCompatibilityReport report) {
		if (!report.isCompatible()) {
			final List<String> objects = report.changes().stream().filter(
					change -> change.compatibility() == com.sqlapp.data.schemas.migration.SchemaCompatibility.BREAKING)
					.map(change -> change.objectId() + "." + change.property()).toList();
			throw new IllegalStateException("Breaking target schema drift detected: " + objects);
		}
	}

	/** Uses invariants inferred from the canonical Schema tables. */
	public ValidatedExecution runWithDataTests() throws SQLException {
		return runWithDataTests(MigrationDataTestPlanner.infer(tables));
	}

	private static void requireDataTests(final String phase, final List<MigrationDataTestResult> results) {
		final List<String> failures = results.stream()
				.filter(result -> result.status() == MigrationDataTestResult.Status.ERROR)
				.map(result -> result.id() + "=" + result.failures()).toList();
		if (!failures.isEmpty()) {
			throw new IllegalStateException("Migration data tests failed during " + phase + ": " + failures);
		}
	}

	private static void requireTransformationTests(final List<MigrationTransformationTestResult> results) {
		final List<String> failures = results.stream().filter(result -> !result.match())
				.map(MigrationTransformationTestResult::id).toList();
		if (!failures.isEmpty()) {
			throw new IllegalStateException("Migration transformation tests failed: " + failures);
		}
	}

	private void writeRepairPlanOnMismatch(final BulkMigrationVerificationMismatchException failure) {
		if (repairPlanOnMismatchFile == null) {
			return;
		}
		try {
			final var repair = planRepair(failure.getVerificationResult());
			repair.writeJson(repairPlanOnMismatchFile);
			repairPlanReportFingerprint = repair.getReportFingerprint();
		} catch (SQLException | RuntimeException repairPlanFailure) {
			failure.addSuppressed(repairPlanFailure);
		}
	}

	private void attachOperationalFailure(final Throwable failure) {
		try {
			publishOperationalFailure(failure);
		} catch (SQLException | RuntimeException reportFailure) {
			failure.addSuppressed(reportFailure);
		}
	}

	private void publishOperationalFailure(final Throwable failure) throws SQLException {
		if (operationalReportFile == null) {
			return;
		}
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			final BulkMigrationJobPlan readOnlyPlan = plan(sourceConnection, targetConnection, true);
			new BulkMigrationOperationalReportJobListener(readOnlyPlan, operationalReportFile,
					() -> maintenanceStateUnchecked(readOnlyPlan), () -> null)
					.onJobFailed(readOnlyPlan.getFingerprint(), failure);
		}
	}

	/** Returns the detailed checkpoint status for advanced integrations. */
	public BulkMigrationJobStatus status() throws SQLException {
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			return BulkMigrationJobStatusInspector.inspect(plan(sourceConnection, targetConnection, true));
		}
	}

	/** Conservatively assesses whether this job can be resumed right now. */
	public BulkMigrationResumeReadiness resumeReadiness() throws SQLException {
		final BulkMigrationOperationalReport report = dryRun();
		if (leaseConfiguration == null) {
			return BulkMigrationOperationalReportResumeAssessor.assess(report);
		}
		if (leaseConfiguration.mode() == BulkMigrationJobLeaseMode.FILE) {
			final var lease = new FileBulkMigrationJobLeaseStore(leaseConfiguration.directory()).load(report.jobId())
					.orElse(null);
			return BulkMigrationOperationalReportResumeAssessor.assess(report, lease, Instant.now());
		}
		try (Connection connection = target.getConnection()) {
			final var lease = JdbcBulkMigrationJobLeaseStore.readOnly(connection, leaseConfiguration.tableName())
					.load(report.jobId()).orElse(null);
			return BulkMigrationOperationalReportResumeAssessor.assess(report, lease, Instant.now());
		}
	}

	public BulkMigrationJobVerificationResult verify() throws SQLException {
		verificationReportFingerprint = null;
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection();
				BulkMigrationVerificationScope ignored = BulkMigrationVerificationScope.open(verificationIsolation,
						sourceConnection, targetConnection)) {
			final BulkMigrationJobPlan verificationPlan = plan(sourceConnection, targetConnection, true);
			final List<BulkMigrationJobTaskVerificationResult> results = new ArrayList<>();
			for (final Table table : orderedTables()) {
				final var expected = keysetSource(sourceConnection, table);
				final Table targetTable = targetTable(table);
				final var actual = keysetSource(targetConnection, targetTable,
						expected.getKeyColumnNames().stream().map(name -> targetColumnName(table, name)).toList());
				final List<String> columns = verificationColumns(table);
				final var verification = BulkMigrationVerifier.verify(expected, actual, columns,
						columns.stream().map(name -> targetColumnName(table, name)).toList(),
						verificationChunkSize(table));
				results.add(new BulkMigrationJobTaskVerificationResult(taskId(table), columns, verification));
			}
			final var verification = new BulkMigrationJobVerificationResult(verificationPlan.getFingerprint(), results);
			if (verificationReportFile != null) {
				final var io = new BulkMigrationVerificationReportIO();
				final var report = io.fromResult(verificationPlan.getFingerprint(), verificationIsolation,
						maxReportedMismatches, verification, null);
				verificationReportFingerprint = io.writeSnapshot(verificationReportFile, report).fingerprint();
			}
			return verification;
		}
	}

	/** Verifies and returns the result, or throws with that result on mismatch. */
	public BulkMigrationJobVerificationResult verifyOrThrow() throws SQLException {
		final var result = verify();
		if (!result.isMatch()) {
			throw new BulkMigrationVerificationMismatchException(result);
		}
		return result;
	}

	public Repair planRepair(final BulkMigrationJobVerificationResult verification) {
		return new Repair(this, Objects.requireNonNull(verification, "verification"));
	}

	/**
	 * Runs verification and prepares the existing review-before-repair workflow.
	 */
	public Repair verifyAndPlanRepair() throws SQLException {
		return planRepair(verify());
	}

	/**
	 * Verifies, writes a reviewable repair plan, and returns its repair handle.
	 * Execution still requires explicit approval of the written plan.
	 */
	public Repair verifyAndWriteRepairPlan(final Path file) throws SQLException {
		final Path reportFile = Objects.requireNonNull(file, "file");
		final Repair repair = verifyAndPlanRepair();
		repair.writeJson(reportFile);
		return repair;
	}

	/** Combined result of the common execute-then-verify workflow. */
	public record Execution(BulkMigrationJobResult migration, BulkMigrationJobVerificationResult verification) {
		public Execution {
			Objects.requireNonNull(migration, "migration");
			Objects.requireNonNull(verification, "verification");
			if (migration.getPlanFingerprint() == null
					|| !migration.getPlanFingerprint().equals(verification.getPlanFingerprint())) {
				throw new IllegalArgumentException("Migration and verification results must identify the same plan");
			}
			if (!migration.getTasks().stream().map(com.sqlapp.jdbc.bulk.BulkMigrationJobTaskResult::getTaskId).toList()
					.equals(verification.getTasks().stream().map(BulkMigrationJobTaskVerificationResult::getTaskId)
							.toList())) {
				throw new IllegalArgumentException(
						"Migration and verification result tasks must match in dependency order");
			}
		}

		public boolean isMatch() {
			return verification.isMatch();
		}

		public Execution requireMatch() {
			if (!isMatch()) {
				throw new BulkMigrationVerificationMismatchException(migration, verification);
			}
			return this;
		}
	}

	/** Result of the preflight, execute/verify, and postflight workflow. */
	public record ValidatedExecution(Execution execution, List<MigrationDataTestResult> preflight,
			List<MigrationDataTestResult> postflight) {
		public ValidatedExecution {
			Objects.requireNonNull(execution, "execution");
			preflight = List.copyOf(preflight);
			postflight = List.copyOf(postflight);
		}
	}

	/**
	 * Result of transformation, data, execution, verification, and postflight
	 * checks.
	 */
	public record ComprehensiveValidatedExecution(Execution execution,
			List<MigrationTransformationTestResult> transformations, List<MigrationDataTestResult> preflight,
			List<MigrationDataTestResult> postflight) {
		public ComprehensiveValidatedExecution {
			Objects.requireNonNull(execution, "execution");
			transformations = List.copyOf(transformations);
			preflight = List.copyOf(preflight);
			postflight = List.copyOf(postflight);
		}
	}

	/** Result of state-aware execution; migration is null when no node changed. */
	public record StateExecution(MigrationNodeManifest current, MigrationNodeSelection selection,
			BulkMigrationJobResult migration) {
		public StateExecution {
			Objects.requireNonNull(current, "current");
			Objects.requireNonNull(selection, "selection");
			if (selection.selected().isEmpty() != (migration == null)) {
				throw new IllegalArgumentException("Migration result must exist exactly when nodes were selected");
			}
		}

		public boolean isNoOp() {
			return migration == null;
		}
	}

	/** Immutable result returned after publishing a cutover package. */
	public record CutoverPackage(MigrationCutoverEvidence evidence, String evidenceFingerprint) {
		public CutoverPackage {
			Objects.requireNonNull(evidence, "evidence");
			Objects.requireNonNull(evidenceFingerprint, "evidenceFingerprint");
		}
	}

	/** Cross-checked reports returned by the read-only package review API. */
	public record CutoverPackageInspection(MigrationCutoverEvidence evidence, String evidenceFingerprint,
			BulkMigrationVerificationReport verification, MigrationCutoverReport cutover) {
		public CutoverPackageInspection {
			Objects.requireNonNull(evidence, "evidence");
			Objects.requireNonNull(evidenceFingerprint, "evidenceFingerprint");
			Objects.requireNonNull(verification, "verification");
			Objects.requireNonNull(cutover, "cutover");
		}
	}

	private BulkMigrationJobPlan plan(final Connection sourceConnection, final Connection targetConnection,
			final boolean readOnly) throws SQLException {
		return plan(sourceConnection, targetConnection, readOnly, true, targetConnection);
	}

	private BulkMigrationJobPlan plan(final Connection sourceConnection, final Connection targetConnection,
			final boolean checkpointReadOnly, final boolean maintenanceReadOnly, final Connection maintenanceConnection)
			throws SQLException {
		final List<BulkMigrationJobTask> tasks = new ArrayList<>();
		for (final Table table : tables) {
			final var options = options(table);
			final BulkMigrationCheckpointStore checkpointStore = checkpointStore(table, targetConnection,
					checkpointReadOnly);
			tasks.add(BulkMigrationJobTask.builder().taskId(taskId(table))
					.keysetSource(keysetSource(sourceConnection, table)).targetTable(configuredTargetTable(table))
					.columnMappings(tableOption(table).getColumnMappings()).options(options)
					.checkpointStore(checkpointStore).build());
		}
		final BulkMigrationJobLifecycle effective = effectiveLifecycle(maintenanceConnection, maintenanceReadOnly);
		return jobId == null ? BulkMigrationJobPlanner.plan(tasks, effective)
				: BulkMigrationJobPlanner.plan(jobId, tasks, effective);
	}

	private BulkMigrationJobLifecycle effectiveLifecycle(final Connection maintenanceConnection, final boolean readOnly)
			throws SQLException {
		if (maintenanceDirectory != null) {
			return new DurableBulkMigrationJobLifecycle(lifecycle,
					new FileBulkMigrationMaintenanceStateStore(maintenanceDirectory));
		}
		if (maintenanceTableName != null) {
			final var store = readOnly ? JdbcBulkMigrationMaintenanceStateStore.readOnly(
					Objects.requireNonNull(maintenanceConnection, "maintenanceConnection"), maintenanceTableName)
					: new JdbcBulkMigrationMaintenanceStateStore(
							Objects.requireNonNull(maintenanceConnection, "maintenanceConnection"),
							maintenanceTableName);
			return new DurableBulkMigrationJobLifecycle(lifecycle, store);
		}
		return lifecycle;
	}

	private static com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState maintenanceState(final BulkMigrationJobPlan plan)
			throws SQLException {
		if (plan.getLifecycle() instanceof DurableBulkMigrationJobLifecycle durable) {
			return durable.inspect(plan).orElse(null);
		}
		return null;
	}

	private BulkMigrationJobRepairPlan repairPlan(final Connection sourceConnection, final Connection targetConnection,
			final BulkMigrationJobVerificationResult verification) throws SQLException {
		final BulkMigrationJobPlan currentPlan = plan(sourceConnection, targetConnection, true);
		verification.validateAgainst(currentPlan);
		final List<BulkMigrationJobRepairTask> tasks = new ArrayList<>();
		for (int i = 0; i < verification.getTasks().size(); i++) {
			final Table table = orderedTables().get(i);
			final var verified = verification.getTasks().get(i);
			tasks.add(BulkMigrationJobRepairTask.builder().taskId(taskId(table))
					.expectedKeysetSource(keysetSource(sourceConnection, table)).target(targetTable(table))
					.verificationResult(verified.getVerificationResult())
					.options(BulkMigrationRepairOption.builder().columnMappings(tableOption(table).getColumnMappings())
							.bulkUpsertOption(targetUpsertOption(table)).build())
					.build());
		}
		return BulkMigrationJobRepairPlanner.plan(targetConnection, tasks);
	}

	private ChunkedBulkMigrationOption options(final Table table) {
		final BulkMigrationTableOption option = tableOption(table);
		return ChunkedBulkMigrationOption.builder()
				.migrationId(option.getMigrationId() == null || option.getMigrationId().isBlank() ? taskId(table)
						: option.getMigrationId())
				.chunkSize(option.getChunkSize() == null ? chunkSize : option.getChunkSize()).mode(mode)
				.incrementalStrategy(incrementalStrategy).resume(resume).checkpointMode(checkpointMode(table))
				.checkpointTableName(checkpointTableName).sourceFingerprint(sourceFingerprint)
				.targetFingerprint(targetFingerprint).bulkOption(bulkOption(table))
				.bulkUpsertOption(targetUpsertOption(table)).retryOption(retryOption(table)).build();
	}

	private JdbcBulkMigrationKeysetSource keysetSource(final Connection connection, final Table table) {
		final List<String> columns = tableOption(table).getKeysetColumns();
		return columns.isEmpty() ? new JdbcBulkMigrationKeysetSource(connection, table)
				: new JdbcBulkMigrationKeysetSource(connection, table, columns);
	}

	private static JdbcBulkMigrationKeysetSource keysetSource(final Connection connection, final Table table,
			final List<String> columns) {
		return new JdbcBulkMigrationKeysetSource(connection, table, columns);
	}

	private List<String> verificationColumns(final Table table) {
		final List<String> columns = tableOption(table).getVerificationColumns();
		return columns.isEmpty()
				? BulkMigrationVerificationColumns
						.resolve(targetTable(table), mode, bulkOption(table), targetUpsertOption(table)).stream()
						.map(name -> sourceColumnName(table, name)).toList()
				: columns;
	}

	private int verificationChunkSize(final Table table) {
		final BulkMigrationTableOption option = tableOption(table);
		if (option.getVerificationChunkSize() != null) {
			return option.getVerificationChunkSize();
		}
		if (verificationChunkSize != null) {
			return verificationChunkSize;
		}
		return option.getChunkSize() == null ? chunkSize : option.getChunkSize();
	}

	private BulkUpsertOption upsertOption(final Table table) {
		final BulkUpsertOption value = tableOption(table).getUpsertOption();
		return value == null ? upsertOption : value;
	}

	private BulkUpsertOption targetUpsertOption(final Table table) {
		final BulkUpsertOption value = upsertOption(table);
		return BulkUpsertOption.builder()
				.keyColumns(value.getKeyColumns().stream().map(name -> targetColumnName(table, name)).toList())
				.updateColumns(value.getUpdateColumns().stream().map(name -> targetColumnName(table, name)).toList())
				.updateWhenMatched(value.isUpdateWhenMatched()).insertWhenNotMatched(value.isInsertWhenNotMatched())
				.useTransaction(value.isUseTransaction()).duplicateKeyStrategy(value.getDuplicateKeyStrategy())
				.duplicateRowSelector(value.getDuplicateRowSelector())
				.duplicateRowSelectorFingerprint(value.getDuplicateRowSelectorFingerprint())
				.stagingTableName(value.getStagingTableName()).bulkOption(value.getBulkOption()).build();
	}

	private String targetColumnName(final Table table, final String sourceName) {
		return tableOption(table).getColumnMappings().entrySet().stream()
				.filter(entry -> entry.getKey().equalsIgnoreCase(sourceName)).map(Map.Entry::getValue).findFirst()
				.orElse(sourceName);
	}

	private String sourceColumnName(final Table table, final String targetName) {
		return table.getColumns().stream().map(column -> column.getName())
				.filter(name -> targetColumnName(table, name).equalsIgnoreCase(targetName)).findFirst()
				.orElse(targetName);
	}

	private Table targetTable(final Table source) {
		final BulkMigrationTableOption option = tableOption(source);
		if (option.getTargetTable() == null && option.getColumnMappings().isEmpty()) {
			return source;
		}
		final Table target = new Table(source.getName()).setCatalogName(source.getCatalogName())
				.setSchemaName(source.getSchemaName());
		source.getColumns().forEach(column -> target.getColumns().add(column.clone()));
		if (source.getPrimaryKeyConstraint() != null) {
			final var primaryKeyColumns = source.getPrimaryKeyConstraint().getColumns().stream()
					.map(reference -> target.getColumns().get(reference.getName()))
					.toArray(com.sqlapp.data.schemas.Column[]::new);
			target.setPrimaryKey(source.getPrimaryKeyConstraint().getName(), primaryKeyColumns);
		}
		if (option.getTargetTable() != null) {
			final String[] names = option.getTargetTable().split("\\.", -1);
			if (names.length < 1 || names.length > 3 || java.util.Arrays.stream(names).anyMatch(String::isBlank)) {
				throw new IllegalArgumentException("targetTable must be table, schema.table, or catalog.schema.table");
			}
			target.setName(names[names.length - 1]);
			if (names.length >= 2)
				target.setSchemaName(names[names.length - 2]);
			if (names.length == 3)
				target.setCatalogName(names[0]);
		}
		for (final var mapping : option.getColumnMappings().entrySet()) {
			final var column = target.getColumns().get(mapping.getKey());
			if (column == null || mapping.getValue() == null || mapping.getValue().isBlank()) {
				throw new IllegalArgumentException("Invalid columnMappings entry: " + mapping);
			}
			column.setName(mapping.getValue());
		}
		return target;
	}

	private Table configuredTargetTable(final Table source) {
		final BulkMigrationTableOption option = tableOption(source);
		return option.getTargetTable() == null && option.getColumnMappings().isEmpty() ? null : targetTable(source);
	}

	private BulkOption bulkOption(final Table table) {
		final BulkOption value = tableOption(table).getBulkOption();
		return value == null ? bulkOption : value;
	}

	private BulkMigrationRetryOption retryOption(final Table table) {
		final BulkMigrationRetryOption value = tableOption(table).getRetryOption();
		return value == null ? retryOption : value;
	}

	private BulkMigrationCheckpointMode checkpointMode(final Table table) {
		return tableOption(table).getCheckpointStore() == null ? checkpointMode : BulkMigrationCheckpointMode.CUSTOM;
	}

	private BulkMigrationCheckpointStore checkpointStore(final Table table, final Connection targetConnection,
			final boolean readOnly) throws SQLException {
		final BulkMigrationCheckpointStore perTable = tableOption(table).getCheckpointStore();
		if (perTable != null) {
			return perTable;
		}
		return switch (checkpointMode) {
		case DATABASE -> readOnly ? JdbcBulkMigrationCheckpointStore.readOnly(targetConnection, checkpointTableName)
				: new JdbcBulkMigrationCheckpointStore(targetConnection, checkpointTableName);
		case FILE -> new FileBulkMigrationCheckpointStore(checkpointDirectory);
		case CUSTOM -> checkpointStore;
		};
	}

	private BulkMigrationTableOption tableOption(final Table table) {
		return tableOptions.getOrDefault(table.getName(), BulkMigrationTableOption.defaults());
	}

	private List<Table> orderedTables() {
		return Table.TableOrder.CREATE.sort(tables, table -> table);
	}

	private void validate() {
		if (tables.isEmpty()) {
			throw new IllegalArgumentException("At least one migration table is required");
		}
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be greater than zero");
		}
		if (maxReportedMismatches <= 0) {
			throw new IllegalArgumentException("maxReportedMismatches must be greater than zero");
		}
		if (verificationChunkSize != null && verificationChunkSize <= 0) {
			throw new IllegalArgumentException("verificationChunkSize must be greater than zero");
		}
		if (resume && (blank(sourceFingerprint) || blank(targetFingerprint))) {
			throw new IllegalArgumentException(
					"sourceFingerprint and targetFingerprint are required when resume is enabled");
		}
		if (checkpointMode == BulkMigrationCheckpointMode.FILE && checkpointDirectory == null) {
			throw new IllegalArgumentException("checkpointDirectory is required for FILE checkpoints");
		}
		if (checkpointMode == BulkMigrationCheckpointMode.CUSTOM && checkpointStore == null) {
			throw new IllegalArgumentException("checkpointStore is required for CUSTOM checkpoints");
		}
		if (checkpointMode == BulkMigrationCheckpointMode.DATABASE
				&& (checkpointDirectory != null || checkpointStore != null)) {
			throw new IllegalArgumentException("DATABASE checkpoints cannot use a directory or custom store");
		}
		if (maintenanceDirectory != null && maintenanceTableName != null) {
			throw new IllegalArgumentException("File and database maintenance cannot both be configured");
		}
		if (maintenanceTableName != null && !maintenanceTableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid maintenance table name: " + maintenanceTableName);
		}
	}

	private static List<Table> resolveTables(final Schema schema, final List<String> names) {
		if (names == null || names.isEmpty()) {
			return List.copyOf(schema.getTables());
		}
		final var unique = new HashSet<String>();
		final List<Table> result = new ArrayList<>();
		for (final String name : names) {
			if (name == null || name.isBlank() || !unique.add(name)) {
				throw new IllegalArgumentException("Table names must be non-empty and unique");
			}
			final Table table = schema.getTables().get(name);
			if (table == null) {
				throw new IllegalArgumentException("Unknown migration table: " + name);
			}
			result.add(table);
		}
		return List.copyOf(result);
	}

	private static Map<String, BulkMigrationTableOption> resolveTableOptions(final List<Table> tables,
			final Map<String, BulkMigrationTableOption> values) {
		if (values == null || values.isEmpty()) {
			return Map.of();
		}
		final Map<String, BulkMigrationTableOption> result = new LinkedHashMap<>();
		for (final var entry : values.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				throw new IllegalArgumentException("Table options require a table name and value");
			}
			final List<Table> matches = tables.stream().filter(table -> table.getName().equalsIgnoreCase(entry.getKey())
					|| taskId(table).equalsIgnoreCase(entry.getKey())).toList();
			if (matches.size() != 1) {
				throw new IllegalArgumentException("Unknown or ambiguous table option: " + entry.getKey());
			}
			final Table table = matches.get(0);
			if (result.put(table.getName(), validateTableOption(table, entry.getValue())) != null) {
				throw new IllegalArgumentException("Duplicate table option: " + entry.getKey());
			}
		}
		return Map.copyOf(result);
	}

	private static BulkMigrationTableOption validateTableOption(final Table table,
			final BulkMigrationTableOption option) {
		if (option.getChunkSize() != null && option.getChunkSize() <= 0) {
			throw new IllegalArgumentException("chunkSize must be greater than zero: " + table.getName());
		}
		if (option.getVerificationChunkSize() != null && option.getVerificationChunkSize() <= 0) {
			throw new IllegalArgumentException("verificationChunkSize must be greater than zero: " + table.getName());
		}
		validateColumns(table, option.getKeysetColumns(), "keysetColumns");
		validateColumns(table, option.getVerificationColumns(), "verificationColumns");
		final var targetNames = new HashSet<String>();
		for (final var column : table.getColumns()) {
			final String targetName = option.getColumnMappings().entrySet().stream()
					.filter(entry -> entry.getKey().equalsIgnoreCase(column.getName())).map(Map.Entry::getValue)
					.findFirst().orElse(column.getName());
			if (targetName == null || targetName.isBlank()
					|| !targetNames.add(targetName.toLowerCase(java.util.Locale.ROOT))) {
				throw new IllegalArgumentException("Invalid columnMappings target: " + targetName);
			}
		}
		if (option.getColumnMappings().keySet().stream().anyMatch(name -> table.getColumns().get(name) == null)) {
			throw new IllegalArgumentException("columnMappings contains an unknown source column: " + table.getName());
		}
		return option;
	}

	private static void validateColumns(final Table table, final List<String> values, final String role) {
		if (values == null) {
			throw new IllegalArgumentException(role + " must not be null: " + table.getName());
		}
		final var names = new HashSet<String>();
		for (final String value : values) {
			final var column = value == null ? null : table.getColumns().get(value);
			if (column == null || !names.add(column.getName())) {
				throw new IllegalArgumentException("Invalid " + role + " column '" + value + "': " + table.getName());
			}
		}
	}

	private static String taskId(final Table table) {
		return table.getSchemaName() == null ? table.getName() : table.getSchemaName() + "." + table.getName();
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}

	/** Reviewed repair flow bound to this migration configuration. */
	public static final class Repair {
		private final BulkMigration migration;
		private final BulkMigrationJobVerificationResult verification;
		private String reportFingerprint;

		private Repair(final BulkMigration migration, final BulkMigrationJobVerificationResult verification) {
			this.migration = migration;
			this.verification = verification;
		}

		public boolean isRequired() {
			return !verification.isMatch();
		}

		public BulkMigrationJobVerificationResult getVerificationResult() {
			return verification;
		}

		public String getReportFingerprint() {
			return reportFingerprint;
		}

		public BulkMigrationJobRepairPlanReport writeJson(final Path file) throws SQLException {
			try (Connection sourceConnection = migration.source.getConnection();
					Connection targetConnection = migration.target.getConnection()) {
				final var plan = migration.repairPlan(sourceConnection, targetConnection, verification);
				final var io = new BulkMigrationJobRepairPlanReportIO();
				final var report = io.fromPlan(plan);
				final var snapshot = io.writeSnapshot(file, report, null);
				reportFingerprint = snapshot.fingerprint();
				migration.repairPlanReportFingerprint = reportFingerprint;
				return snapshot.report();
			}
		}

		public BulkMigrationJobRepairResult executeApproved(final String approvedFingerprint) throws SQLException {
			try (Connection sourceConnection = migration.source.getConnection();
					Connection targetConnection = migration.target.getConnection()) {
				final var plan = migration.repairPlan(sourceConnection, targetConnection, verification);
				return BulkMigrationJobRepairExecutor.execute(targetConnection, plan, approvedFingerprint);
			}
		}

		/** Reads a previously reviewed JSON report and executes that exact plan. */
		public BulkMigrationJobRepairResult executeApproved(final Path reportFile) throws SQLException {
			Objects.requireNonNull(reportFile, "reportFile");
			final var snapshot = new BulkMigrationJobRepairPlanReportIO().readSnapshot(reportFile, null);
			return executeApproved(snapshot.report(), snapshot.fingerprint());
		}

		/** Adds optional SHA-256, age, and bounded-read checks to file approval. */
		public BulkMigrationJobRepairResult executeApproved(final Path reportFile,
				final String expectedReportFingerprint, final Long maxReportAgeSeconds,
				final Long maxReportFileSizeBytes) throws SQLException {
			Objects.requireNonNull(reportFile, "reportFile");
			final var approved = BulkMigrationJobRepairApprovalValidator.validate(reportFile.toFile(),
					expectedReportFingerprint, maxReportAgeSeconds, maxReportFileSizeBytes);
			return executeApproved(approved.report(), approved.fingerprint());
		}

		private BulkMigrationJobRepairResult executeApproved(final BulkMigrationJobRepairPlanReport approved,
				final String approvedFingerprint) throws SQLException {
			reportFingerprint = approvedFingerprint;
			migration.approvedRepairPlanReportFingerprint = approvedFingerprint;
			try (Connection sourceConnection = migration.source.getConnection();
					Connection targetConnection = migration.target.getConnection()) {
				final var plan = migration.repairPlan(sourceConnection, targetConnection, verification);
				if (!plan.getFingerprint().equals(approved.planFingerprint())) {
					throw new IllegalArgumentException("Approved repair plan fingerprint does not match");
				}
				return BulkMigrationJobRepairExecutor.execute(targetConnection, plan, approved.planFingerprint());
			}
		}
	}
}
