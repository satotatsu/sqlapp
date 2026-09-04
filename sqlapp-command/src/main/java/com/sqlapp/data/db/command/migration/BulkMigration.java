/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationJobExecutor;
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
import com.sqlapp.jdbc.bulk.BulkMigrationRetryOption;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.CompositeBulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;
import com.sqlapp.jdbc.bulk.ReadOnlyJdbcBulkMigrationCheckpointStore;

import lombok.Builder;

/**
 * Simple, safe facade for the usual execute, inspect, verify, and repair flow.
 * Advanced callers can continue to use the underlying bulk APIs directly.
 */
public final class BulkMigration {
	private final DataSource source;
	private final DataSource target;
	private final List<Table> tables;
	private final BulkMigrationMode mode;
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
	private final Path operationalReportFile;
	private final Path verificationReportFile;
	private final int maxReportedMismatches;
	private final BulkMigrationVerificationIsolation verificationIsolation;
	private final Integer verificationChunkSize;

	@Builder
	private BulkMigration(final DataSource source, final DataSource target,
			final Schema schema, final List<String> tableNames,
			final BulkMigrationMode mode, final Integer chunkSize,
			final Boolean resume, final String sourceFingerprint,
			final String targetFingerprint, final String checkpointTableName,
			final BulkUpsertOption upsertOption,
			final Map<String, BulkMigrationTableOption> tableOptions,
			final BulkOption bulkOption, final BulkMigrationRetryOption retryOption,
			final BulkMigrationJobListener jobListener,
			final ChunkedBulkMigrationListener chunkListener,
			final BulkMigrationCheckpointMode checkpointMode,
			final Path checkpointDirectory,
			final BulkMigrationCheckpointStore checkpointStore,
			final BulkMigrationJobLeaseConfiguration leaseConfiguration,
			final BulkMigrationJobLifecycle lifecycle,
			final Path operationalReportFile, final Path verificationReportFile,
			final Integer maxReportedMismatches,
			final BulkMigrationVerificationIsolation verificationIsolation,
			final Integer verificationChunkSize) {
		this.source = Objects.requireNonNull(source, "source");
		this.target = Objects.requireNonNull(target, "target");
		this.tables = resolveTables(Objects.requireNonNull(schema, "schema"), tableNames);
		this.mode = mode == null ? BulkMigrationMode.UPSERT : mode;
		this.chunkSize = chunkSize == null ? 10_000 : chunkSize;
		this.resume = resume != null && resume;
		this.sourceFingerprint = sourceFingerprint;
		this.targetFingerprint = targetFingerprint;
		this.checkpointTableName = checkpointTableName == null
				|| checkpointTableName.isBlank() ? "SQLAPP_BULK_MIGRATION_CHECKPOINT"
						: checkpointTableName;
		this.upsertOption = upsertOption == null ? BulkUpsertOption.defaults() : upsertOption;
		this.tableOptions = resolveTableOptions(this.tables, tableOptions);
		this.bulkOption = bulkOption == null ? BulkOption.defaults() : bulkOption;
		this.retryOption = retryOption == null ? BulkMigrationRetryOption.none() : retryOption;
		this.jobListener = jobListener == null ? BulkMigrationJobListener.NO_OP : jobListener;
		this.chunkListener = chunkListener == null
				? ChunkedBulkMigrationListener.NO_OP : chunkListener;
		this.checkpointMode = checkpointMode == null
				? BulkMigrationCheckpointMode.DATABASE : checkpointMode;
		this.checkpointDirectory = checkpointDirectory == null ? null
				: checkpointDirectory.toAbsolutePath().normalize();
		this.checkpointStore = checkpointStore;
		this.leaseConfiguration = leaseConfiguration;
		this.lifecycle = lifecycle == null ? BulkMigrationJobLifecycle.NO_OP : lifecycle;
		this.operationalReportFile = operationalReportFile == null ? null
				: operationalReportFile.toAbsolutePath().normalize();
		this.verificationReportFile = verificationReportFile == null ? null
				: verificationReportFile.toAbsolutePath().normalize();
		this.maxReportedMismatches = maxReportedMismatches == null
				? BulkMigrationVerificationReportIO.DEFAULT_MAX_REPORTED_MISMATCHES
				: maxReportedMismatches;
		this.verificationIsolation = verificationIsolation == null
				? BulkMigrationVerificationIsolation.DEFAULT : verificationIsolation;
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

		public BulkMigrationBuilder customCheckpointStore(
				final BulkMigrationCheckpointStore store) {
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

		/** Prevents concurrent execution using a lease file outside the target database. */
		public BulkMigrationBuilder fileLease(final String ownerId, final Path directory) {
			this.leaseConfiguration = BulkMigrationJobLeaseConfiguration.file(ownerId,
					directory);
			return this;
		}

		/** Writes an atomic JSON operational report at job and task boundaries. */
		public BulkMigrationBuilder operationalReport(final Path file) {
			this.operationalReportFile = Objects.requireNonNull(file, "file");
			return this;
		}

		/** Writes the result of each explicit verification as bounded JSON. */
		public BulkMigrationBuilder verificationReport(final Path file) {
			this.verificationReportFile = Objects.requireNonNull(file, "file");
			return this;
		}

		/** Writes verification JSON while limiting retained mismatch details. */
		public BulkMigrationBuilder verificationReport(final Path file,
				final int maxMismatches) {
			this.verificationReportFile = Objects.requireNonNull(file, "file");
			this.maxReportedMismatches = maxMismatches;
			return this;
		}

		public BulkMigrationBuilder tableOption(final String tableName,
				final BulkMigrationTableOption option) {
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

		public BulkMigrationBuilder fingerprints(final String source,
				final String target) {
			this.sourceFingerprint = source;
			this.targetFingerprint = target;
			return this;
		}
	}

	public BulkMigrationJobResult execute() throws SQLException {
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			final BulkMigrationJobPlan plan = plan(sourceConnection, targetConnection, false);
			final BulkMigrationJobListener executionListener = executionListener(plan);
			if (leaseConfiguration == null) {
				return BulkMigrationJobExecutor.executePlan(targetConnection, plan, executionListener,
						chunkListener);
			}
			if (leaseConfiguration.mode() == BulkMigrationJobLeaseMode.FILE) {
				final BulkMigrationJobLeaseManager manager =
						BulkMigrationJobLeaseManagerFactory.create(null, leaseConfiguration);
				return BulkMigrationJobExecutor.executePlan(targetConnection, plan, executionListener,
						chunkListener, manager);
			}
			try (Connection leaseConnection = target.getConnection()) {
				leaseConnection.setAutoCommit(true);
				final BulkMigrationJobLeaseManager manager =
						BulkMigrationJobLeaseManagerFactory.create(leaseConnection,
								leaseConfiguration);
				return BulkMigrationJobExecutor.executePlan(targetConnection, plan, executionListener,
						chunkListener, manager);
			}
		}
	}

	private BulkMigrationJobListener executionListener(final BulkMigrationJobPlan plan) {
		if (operationalReportFile == null) {
			return jobListener;
		}
		final BulkMigrationJobListener report =
				new BulkMigrationOperationalReportJobListener(plan, operationalReportFile);
		return jobListener == BulkMigrationJobListener.NO_OP ? report
				: CompositeBulkMigrationJobListener.of(jobListener, report);
	}

	/** Executes the migration and immediately verifies the resulting target. */
	public Execution executeAndVerify() throws SQLException {
		final BulkMigrationJobResult execution = execute();
		return new Execution(execution, verify());
	}

	/** Executes and verifies, throwing only when the completed verification mismatches. */
	public Execution executeAndVerifyOrThrow() throws SQLException {
		final BulkMigrationJobResult execution = execute();
		try {
			return new Execution(execution, verify()).requireMatch();
		} catch (SQLException | RuntimeException | Error failure) {
			try {
				publishOperationalFailure(failure);
			} catch (SQLException | RuntimeException reportFailure) {
				failure.addSuppressed(reportFailure);
			}
			throw failure;
		}
	}

	private void publishOperationalFailure(final Throwable failure) throws SQLException {
		if (operationalReportFile == null) {
			return;
		}
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			final BulkMigrationJobPlan readOnlyPlan = plan(sourceConnection,
					targetConnection, true);
			new BulkMigrationOperationalReportJobListener(readOnlyPlan,
					operationalReportFile).onJobFailed(readOnlyPlan.getFingerprint(), failure);
		}
	}

	public BulkMigrationJobStatus inspect() throws SQLException {
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			return BulkMigrationJobStatusInspector.inspect(
					plan(sourceConnection, targetConnection, true));
		}
	}

	/** Writes and returns a read-only operational snapshot without executing the job. */
	public BulkMigrationOperationalReport inspect(final Path reportFile)
			throws SQLException {
		Objects.requireNonNull(reportFile, "reportFile");
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			final BulkMigrationJobPlan readOnlyPlan = plan(sourceConnection,
					targetConnection, true);
			return new BulkMigrationOperationalReportJobListener(readOnlyPlan, reportFile)
					.publish();
		}
	}

	public BulkMigrationJobVerificationResult verify() throws SQLException {
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection();
				BulkMigrationVerificationScope ignored = BulkMigrationVerificationScope.open(
						verificationIsolation, sourceConnection, targetConnection)) {
			final List<BulkMigrationJobTaskVerificationResult> results = new ArrayList<>();
			for (final Table table : orderedTables()) {
				final var expected = keysetSource(sourceConnection, table);
				final var actual = keysetSource(targetConnection, table);
				final List<String> columns = verificationColumns(table);
				final var verification = BulkMigrationVerifier.verify(expected, actual,
						columns, verificationChunkSize(table));
				results.add(new BulkMigrationJobTaskVerificationResult(taskId(table),
						columns, verification));
			}
			final var verification = new BulkMigrationJobVerificationResult(results);
			if (verificationReportFile != null) {
				final String planFingerprint = plan(sourceConnection, targetConnection, true)
						.getFingerprint();
				new BulkMigrationVerificationReportIO().write(verificationReportFile,
						planFingerprint, verificationIsolation,
						maxReportedMismatches, verification);
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

	/** Runs verification and prepares the existing review-before-repair workflow. */
	public Repair verifyAndPlanRepair() throws SQLException {
		return planRepair(verify());
	}

	/** Combined result of the common execute-then-verify workflow. */
	public record Execution(BulkMigrationJobResult migration,
			BulkMigrationJobVerificationResult verification) {
		public Execution {
			Objects.requireNonNull(migration, "migration");
			Objects.requireNonNull(verification, "verification");
		}

		public boolean isMatch() {
			return verification.isMatch();
		}

		public Execution requireMatch() {
			if (!isMatch()) {
				throw new BulkMigrationVerificationMismatchException(verification);
			}
			return this;
		}
	}

	private BulkMigrationJobPlan plan(final Connection sourceConnection,
			final Connection targetConnection, final boolean readOnly) throws SQLException {
		final List<BulkMigrationJobTask> tasks = new ArrayList<>();
		for (final Table table : tables) {
			final var options = options(table);
			final BulkMigrationCheckpointStore checkpointStore = checkpointStore(
					table, targetConnection, readOnly);
			tasks.add(BulkMigrationJobTask.builder().taskId(taskId(table))
					.keysetSource(keysetSource(sourceConnection, table))
					.options(options).checkpointStore(checkpointStore).build());
		}
		return BulkMigrationJobPlanner.plan(tasks, lifecycle);
	}

	private BulkMigrationJobRepairPlan repairPlan(final Connection sourceConnection,
			final Connection targetConnection,
			final BulkMigrationJobVerificationResult verification) throws SQLException {
		if (!verification.getTasks().stream().map(
				BulkMigrationJobTaskVerificationResult::getTaskId).toList()
				.equals(orderedTables().stream().map(BulkMigration::taskId).toList())) {
			throw new IllegalArgumentException(
					"Verification tasks do not match the migration tables and dependency order");
		}
		final List<BulkMigrationJobRepairTask> tasks = new ArrayList<>();
		for (int i = 0; i < verification.getTasks().size(); i++) {
			final Table table = orderedTables().get(i);
			final var verified = verification.getTasks().get(i);
			tasks.add(BulkMigrationJobRepairTask.builder().taskId(taskId(table))
					.expectedKeysetSource(keysetSource(sourceConnection, table)).target(table)
					.verificationResult(verified.getVerificationResult())
					.options(BulkMigrationRepairOption.builder()
							.bulkUpsertOption(upsertOption(table)).build()).build());
		}
		return BulkMigrationJobRepairPlanner.plan(targetConnection, tasks);
	}

	private ChunkedBulkMigrationOption options(final Table table) {
		final BulkMigrationTableOption option = tableOption(table);
		return ChunkedBulkMigrationOption.builder().migrationId(option.getMigrationId() == null
				|| option.getMigrationId().isBlank() ? taskId(table) : option.getMigrationId())
				.chunkSize(option.getChunkSize() == null ? chunkSize : option.getChunkSize())
				.mode(mode).resume(resume).checkpointMode(checkpointMode(table))
				.checkpointTableName(checkpointTableName)
				.sourceFingerprint(sourceFingerprint).targetFingerprint(targetFingerprint)
				.bulkOption(bulkOption(table)).bulkUpsertOption(upsertOption(table))
				.retryOption(retryOption(table)).build();
	}

	private JdbcBulkMigrationKeysetSource keysetSource(final Connection connection,
			final Table table) {
		final List<String> columns = tableOption(table).getKeysetColumns();
		return columns.isEmpty() ? new JdbcBulkMigrationKeysetSource(connection, table)
				: new JdbcBulkMigrationKeysetSource(connection, table, columns);
	}

	private List<String> verificationColumns(final Table table) {
		final List<String> columns = tableOption(table).getVerificationColumns();
		return columns.isEmpty() ? BulkMigrationVerificationColumns.resolve(table, mode,
				bulkOption(table), upsertOption(table)) : columns;
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

	private BulkOption bulkOption(final Table table) {
		final BulkOption value = tableOption(table).getBulkOption();
		return value == null ? bulkOption : value;
	}

	private BulkMigrationRetryOption retryOption(final Table table) {
		final BulkMigrationRetryOption value = tableOption(table).getRetryOption();
		return value == null ? retryOption : value;
	}

	private BulkMigrationCheckpointMode checkpointMode(final Table table) {
		return tableOption(table).getCheckpointStore() == null
				? checkpointMode : BulkMigrationCheckpointMode.CUSTOM;
	}

	private BulkMigrationCheckpointStore checkpointStore(final Table table,
			final Connection targetConnection, final boolean readOnly) throws SQLException {
		final BulkMigrationCheckpointStore perTable = tableOption(table).getCheckpointStore();
		if (perTable != null) {
			return perTable;
		}
		return switch (checkpointMode) {
		case DATABASE -> readOnly
				? new ReadOnlyJdbcBulkMigrationCheckpointStore(targetConnection,
						checkpointTableName)
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
			throw new IllegalArgumentException(
					"maxReportedMismatches must be greater than zero");
		}
		if (verificationChunkSize != null && verificationChunkSize <= 0) {
			throw new IllegalArgumentException(
					"verificationChunkSize must be greater than zero");
		}
		if (resume && (blank(sourceFingerprint) || blank(targetFingerprint))) {
			throw new IllegalArgumentException(
					"sourceFingerprint and targetFingerprint are required when resume is enabled");
		}
		if (checkpointMode == BulkMigrationCheckpointMode.FILE
				&& checkpointDirectory == null) {
			throw new IllegalArgumentException(
					"checkpointDirectory is required for FILE checkpoints");
		}
		if (checkpointMode == BulkMigrationCheckpointMode.CUSTOM
				&& checkpointStore == null) {
			throw new IllegalArgumentException(
					"checkpointStore is required for CUSTOM checkpoints");
		}
		if (checkpointMode == BulkMigrationCheckpointMode.DATABASE
				&& (checkpointDirectory != null || checkpointStore != null)) {
			throw new IllegalArgumentException(
					"DATABASE checkpoints cannot use a directory or custom store");
		}
	}

	private static List<Table> resolveTables(final Schema schema,
			final List<String> names) {
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

	private static Map<String, BulkMigrationTableOption> resolveTableOptions(
			final List<Table> tables, final Map<String, BulkMigrationTableOption> values) {
		if (values == null || values.isEmpty()) {
			return Map.of();
		}
		final Map<String, BulkMigrationTableOption> result = new LinkedHashMap<>();
		for (final var entry : values.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				throw new IllegalArgumentException("Table options require a table name and value");
			}
			final List<Table> matches = tables.stream().filter(table ->
					table.getName().equalsIgnoreCase(entry.getKey())
					|| taskId(table).equalsIgnoreCase(entry.getKey())).toList();
			if (matches.size() != 1) {
				throw new IllegalArgumentException("Unknown or ambiguous table option: "
						+ entry.getKey());
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
			throw new IllegalArgumentException("chunkSize must be greater than zero: "
					+ table.getName());
		}
		if (option.getVerificationChunkSize() != null
				&& option.getVerificationChunkSize() <= 0) {
			throw new IllegalArgumentException(
					"verificationChunkSize must be greater than zero: "
							+ table.getName());
		}
		validateColumns(table, option.getKeysetColumns(), "keysetColumns");
		validateColumns(table, option.getVerificationColumns(), "verificationColumns");
		return option;
	}

	private static void validateColumns(final Table table, final List<String> values,
			final String role) {
		if (values == null) {
			throw new IllegalArgumentException(role + " must not be null: " + table.getName());
		}
		final var names = new HashSet<String>();
		for (final String value : values) {
			final var column = value == null ? null : table.getColumns().get(value);
			if (column == null || !names.add(column.getName())) {
				throw new IllegalArgumentException("Invalid " + role + " column '" + value
						+ "': " + table.getName());
			}
		}
	}

	private static String taskId(final Table table) {
		return table.getSchemaName() == null ? table.getName()
				: table.getSchemaName() + "." + table.getName();
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}

	/** Reviewed repair flow bound to this migration configuration. */
	public static final class Repair {
		private final BulkMigration migration;
		private final BulkMigrationJobVerificationResult verification;

		private Repair(final BulkMigration migration,
				final BulkMigrationJobVerificationResult verification) {
			this.migration = migration;
			this.verification = verification;
		}

		public boolean isRequired() {
			return !verification.isMatch();
		}

		public BulkMigrationJobVerificationResult getVerificationResult() {
			return verification;
		}

		public BulkMigrationJobRepairPlanReport writeJson(final Path file)
				throws SQLException {
			try (Connection sourceConnection = migration.source.getConnection();
					Connection targetConnection = migration.target.getConnection()) {
				final var plan = migration.repairPlan(sourceConnection, targetConnection,
						verification);
				final var io = new BulkMigrationJobRepairPlanReportIO();
				final var report = io.fromPlan(plan);
				io.write(file, report);
				return report;
			}
		}

		public BulkMigrationJobRepairResult executeApproved(
				final String approvedFingerprint) throws SQLException {
			try (Connection sourceConnection = migration.source.getConnection();
					Connection targetConnection = migration.target.getConnection()) {
				final var plan = migration.repairPlan(sourceConnection, targetConnection,
						verification);
				return BulkMigrationJobRepairExecutor.execute(targetConnection, plan,
						approvedFingerprint);
			}
		}

		/** Reads a previously reviewed JSON report and executes that exact plan. */
		public BulkMigrationJobRepairResult executeApproved(final Path reportFile)
				throws SQLException {
			Objects.requireNonNull(reportFile, "reportFile");
			try (Connection sourceConnection = migration.source.getConnection();
					Connection targetConnection = migration.target.getConnection()) {
				final var plan = migration.repairPlan(sourceConnection, targetConnection,
						verification);
				final var approved = new BulkMigrationJobRepairPlanReportIO().read(
						reportFile, plan.getFingerprint());
				return BulkMigrationJobRepairExecutor.execute(targetConnection, plan,
						approved.planFingerprint());
			}
		}
	}
}
