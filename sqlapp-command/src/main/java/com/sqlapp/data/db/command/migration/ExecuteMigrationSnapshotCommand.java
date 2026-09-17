/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Objects;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.JdbcBatchMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.JdbcStreamingMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;
import com.sqlapp.jdbc.bulk.MigrationSnapshotExecutionResult;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

import lombok.Getter;
import lombok.Setter;

/** Executes one YAML-defined atomic SCD2 snapshot. */
@Getter
@Setter
public class ExecuteMigrationSnapshotCommand extends AbstractDataSourceCommand {
	private final Clock clock;
	private File configurationFile;
	private DataSource sourceDataSource;
	private MigrationSnapshotExecutionResult result;
	private MigrationSnapshotExecutionReport report;

	public ExecuteMigrationSnapshotCommand() {
		this(Clock.systemUTC());
	}

	public ExecuteMigrationSnapshotCommand(final Clock clock) {
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	protected void doRun() {
		result = null;
		report = null;
		if (getDataSource() == null) throw new CommandException("Migration snapshot target data source is required.");
		if (sourceDataSource == null) throw new CommandException("Migration snapshot source data source is required.");
		final java.time.Instant startedAt = clock.instant();
		final var resolved = new MigrationSnapshotConfigurationResolver(
				Clock.fixed(startedAt, ZoneOffset.UTC)).resolve(configurationFile);
		try {
			execute(sourceDataSource, source -> executeNoTranAndClose(getDataSource(), target -> {
				final var dialect = DialectResolver.getInstance().getDialect(target);
				final var setBased = SetBasedMigrationSnapshotResolver.find(dialect);
				result = executeSnapshot(source, target, resolved);
				final var metadata = target.getMetaData();
				report = new MigrationSnapshotExecutionReport(MigrationSnapshotExecutionReport.CURRENT_FORMAT_VERSION,
						clock.instant(), startedAt, resolved.definition().id(), resolved.configurationFingerprint(),
						resolved.approvalGeneratedAt(), resolved.approvalArtifactFingerprint(),
						name(resolved.sourceTable()),
						name(resolved.targetTable()), resolved.definition().keyColumns(),
						resolved.definition().trackedColumns(), resolved.definition().expireMissingRows(),
						resolved.effectiveAt(), resolved.fetchSize(), resolved.batchSize(), resolved.approvalValidFor(),
						metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion(),
						setBased.<String>map(x -> x.getClass().getName())
								.orElse(JdbcBatchMigrationSnapshotExecutor.class.getName()),
						setBased.map(x -> x.supportsCallerTransactionAtomicity()).orElse(true), result.expiredRows(),
						result.insertedRows(), result.unchangedRows());
			}));
		} catch (RuntimeException failure) {
			final MigrationSnapshotFailurePhase phase = result == null
					? MigrationSnapshotFailurePhase.DATABASE_EXECUTION
					: MigrationSnapshotFailurePhase.POST_COMMIT_FINALIZATION;
			writeFailure(resolved, startedAt, phase, failure);
			throw failure;
		}
		if (resolved.reportFile() != null) {
			try {
				new MigrationSnapshotExecutionReportIO().write(resolved.reportFile(), report);
				info("Migration snapshot report: ", resolved.reportFile());
			} catch (RuntimeException failure) {
				writeFailure(resolved, startedAt, MigrationSnapshotFailurePhase.SUCCESS_REPORT_WRITE, failure);
				throw failure;
			}
		}
		info("Migration snapshot completed: ", result);
	}

	private MigrationSnapshotExecutionResult executeSnapshot(final java.sql.Connection source,
			final java.sql.Connection target, final MigrationSnapshotConfigurationResolver.Resolution resolved)
			throws java.sql.SQLException {
		final var lease = resolved.leaseConfiguration();
		if (lease == null) {
			return executeSnapshot(source, target, resolved, com.sqlapp.jdbc.bulk.MigrationSnapshotExecutionGuard.NO_OP);
		}
		if (lease.mode() == BulkMigrationJobLeaseMode.FILE) {
			return executeSnapshotWithLease(source, target, resolved,
					BulkMigrationJobLeaseManagerFactory.create(null, lease));
		}
		try (var leaseConnection = getDataSource().getConnection()) {
			leaseConnection.setAutoCommit(true);
			return executeSnapshotWithLease(source, target, resolved,
					BulkMigrationJobLeaseManagerFactory.create(leaseConnection, lease));
		}
	}

	private MigrationSnapshotExecutionResult executeSnapshotWithLease(final java.sql.Connection source,
			final java.sql.Connection target, final MigrationSnapshotConfigurationResolver.Resolution resolved,
			final com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseManager manager) throws java.sql.SQLException {
		try (var handle = manager.acquire(resolved.definition().id(), resolved.configurationFingerprint());
				var heartbeat = handle.startHeartbeat()) {
			// Record the committed result before heartbeat shutdown or lease release.
			// A later cleanup failure is post-commit finalization, never a rollback.
			result = executeSnapshot(source, target, resolved, heartbeat::check);
			return result;
		}
	}

	private static MigrationSnapshotExecutionResult executeSnapshot(final java.sql.Connection source,
			final java.sql.Connection target, final MigrationSnapshotConfigurationResolver.Resolution resolved,
			final com.sqlapp.jdbc.bulk.MigrationSnapshotExecutionGuard guard) throws java.sql.SQLException {
		return JdbcStreamingMigrationSnapshotExecutor.execute(source, target, resolved.sourceTable(),
				resolved.targetTable(), resolved.definition(), resolved.effectiveAt(), resolved.fetchSize(),
				resolved.batchSize(), guard);
	}

	private void writeFailure(final MigrationSnapshotConfigurationResolver.Resolution resolved,
			final java.time.Instant startedAt, final MigrationSnapshotFailurePhase phase,
			final RuntimeException failure) {
		if (resolved.failureReportFile() == null) return;
		final String message = failure.getMessage() == null || failure.getMessage().isBlank()
				? failure.getClass().getName() : failure.getMessage();
		final String bounded = message.length() <= MigrationSnapshotFailureReportIO.FAILURE_MESSAGE_MAX_LENGTH ? message
				: message.substring(0, MigrationSnapshotFailureReportIO.FAILURE_MESSAGE_MAX_LENGTH);
		final var failureReport = new MigrationSnapshotFailureReport(MigrationSnapshotFailureReport.CURRENT_FORMAT_VERSION,
				clock.instant(), startedAt, phase, resolved.definition().id(), resolved.configurationFingerprint(),
				resolved.approvalGeneratedAt(), resolved.approvalArtifactFingerprint(), name(resolved.sourceTable()),
				name(resolved.targetTable()), resolved.effectiveAt(), failure.getClass().getName(), bounded);
		try {
			new MigrationSnapshotFailureReportIO().write(resolved.failureReportFile(), failureReport);
			info("Migration snapshot failure report: ", resolved.failureReportFile());
		} catch (RuntimeException reportFailure) {
			failure.addSuppressed(reportFailure);
		}
	}

	private static String name(final Table table) {
		final var parts = new java.util.ArrayList<String>();
		if (table.getCatalogName() != null) parts.add(table.getCatalogName());
		if (table.getSchemaName() != null) parts.add(table.getSchemaName());
		parts.add(table.getName());
		return String.join(".", parts);
	}
}
