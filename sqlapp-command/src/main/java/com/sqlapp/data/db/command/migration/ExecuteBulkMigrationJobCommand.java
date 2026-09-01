/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseManager;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkUpsertPlan;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.CompositeBulkMigrationJobListener;

import lombok.Getter;
import lombok.Setter;

/** Executes one validated bulk migration plan against the configured data source. */
@Getter
@Setter
public class ExecuteBulkMigrationJobCommand extends AbstractDataSourceCommand {
	private BulkMigrationJobPlan plan;
	private File configurationFile;
	private DataSource sourceDataSource;
	private BulkMigrationJobListener listener = BulkMigrationJobListener.NO_OP;
	private ChunkedBulkMigrationListener chunkListener =
			ChunkedBulkMigrationListener.NO_OP;
	private BulkMigrationJobLeaseConfiguration leaseConfiguration;
	private BulkMigrationJobResult result;
	private BulkMigrationJobVerificationResult verificationResult;

	@Override
	protected void doRun() {
		result = null;
		verificationResult = null;
		if (getDataSource() == null) {
			throw new CommandException("Bulk migration target data source is required.");
		}
		if (plan != null && configurationFile != null) {
			throw new CommandException(
					"Specify either a bulk migration plan or configurationFile, not both.");
		}
		if (plan == null && configurationFile == null) {
			throw new CommandException(
					"Bulk migration plan or configurationFile is required.");
		}
		if (configurationFile != null && sourceDataSource == null) {
			throw new CommandException(
					"Bulk migration source data source is required for configurationFile.");
		}
		if (plan != null) {
			executePlan(plan);
			return;
		}
		execute(sourceDataSource, sourceConnection -> {
			final var resolved = new BulkMigrationJobConfigurationResolver()
					.resolveJob(configurationFile, sourceConnection);
			if (leaseConfiguration != null && resolved.leaseConfiguration() != null) {
				throw new CommandException("Specify lease configuration either in the job file "
						+ "or as a command property, not both.");
			}
			executePlan(resolved.plan(), resolved.leaseConfiguration(), listener,
					resolved.reportConfiguration(), resolved.verificationConfiguration());
		});
	}

	private void executePlan(final BulkMigrationJobPlan executionPlan) {
		executePlan(executionPlan, leaseConfiguration);
	}

	private void executePlan(final BulkMigrationJobPlan executionPlan,
			final BulkMigrationJobLeaseConfiguration executionLeaseConfiguration) {
		executePlan(executionPlan, executionLeaseConfiguration, listener);
	}

	private void executePlan(final BulkMigrationJobPlan executionPlan,
			final BulkMigrationJobLeaseConfiguration executionLeaseConfiguration,
			final BulkMigrationJobListener executionListener) {
		executePlan(executionPlan, executionLeaseConfiguration, executionListener, null);
	}

	private void executePlan(final BulkMigrationJobPlan executionPlan,
			final BulkMigrationJobLeaseConfiguration executionLeaseConfiguration,
			final BulkMigrationJobListener configuredListener,
			final BulkMigrationJobConfigurationResolver.OperationalReportConfiguration
					reportConfiguration) {
		executePlan(executionPlan, executionLeaseConfiguration, configuredListener,
				reportConfiguration, null);
	}

	private void executePlan(final BulkMigrationJobPlan executionPlan,
			final BulkMigrationJobLeaseConfiguration executionLeaseConfiguration,
			final BulkMigrationJobListener configuredListener,
			final BulkMigrationJobConfigurationResolver.OperationalReportConfiguration
					reportConfiguration,
			final BulkMigrationJobConfigurationResolver.VerificationConfiguration
					verificationConfiguration) {
		executionPlan.validateUnchanged();
		execute(getDataSource(), targetConnection -> {
			// The chunk executor owns commit/rollback boundaries, including durable
			// checkpoint writes. AbstractDataSourceCommand otherwise starts a transaction.
			targetConnection.setAutoCommit(true);
			final BulkMigrationJobPlan effectivePlan = reportConfiguration == null
					? executionPlan : withExplicitDatabaseCheckpointStores(executionPlan,
							targetConnection);
			final BulkMigrationJobListener executionListener;
			if (reportConfiguration == null) {
				executionListener = configuredListener;
			} else {
				final var reportListener = new BulkMigrationOperationalReportJobListener(
						effectivePlan, reportConfiguration.targetFile(), () -> null, () -> null,
						reportConfiguration.failurePolicy(), failure -> { });
				executionListener = configuredListener == BulkMigrationJobListener.NO_OP
						? reportListener : CompositeBulkMigrationJobListener.of(
								configuredListener, reportListener);
			}
			if (executionLeaseConfiguration == null) {
				result = BulkMigrationJobExecutor.executePlan(targetConnection, effectivePlan,
						executionListener, chunkListener);
			} else if (executionLeaseConfiguration.mode() == BulkMigrationJobLeaseMode.FILE) {
				final BulkMigrationJobLeaseManager manager =
						BulkMigrationJobLeaseManagerFactory.create(null,
								executionLeaseConfiguration);
				result = BulkMigrationJobExecutor.executePlan(targetConnection, effectivePlan,
						executionListener, chunkListener, manager);
			} else {
				try (Connection leaseConnection = getDataSource().getConnection()) {
					leaseConnection.setAutoCommit(true);
					final BulkMigrationJobLeaseManager manager =
							BulkMigrationJobLeaseManagerFactory.create(leaseConnection,
									executionLeaseConfiguration);
					result = BulkMigrationJobExecutor.executePlan(targetConnection, effectivePlan,
							executionListener, chunkListener, manager);
				}
			}
			if (verificationConfiguration != null) {
				verificationResult = verify(effectivePlan, targetConnection,
						verificationConfiguration.chunkSize(),
						verificationConfiguration.columnsByTask());
				if (verificationConfiguration.targetFile() != null) {
					new BulkMigrationVerificationReportIO().write(
							verificationConfiguration.targetFile(), effectivePlan.getFingerprint(),
							verificationResult);
				}
				if (verificationConfiguration.failOnMismatch()
						&& !verificationResult.isMatch()) {
					throw new CommandException("Bulk migration verification failed: "
							+ verificationResult.getMismatchedTasks() + " task(s) mismatched.");
				}
			}
		});
		info("Bulk migration job completed: ", executionPlan.getFingerprint());
	}

	static BulkMigrationJobVerificationResult verify(final BulkMigrationJobPlan plan,
			final Connection targetConnection, final int chunkSize) throws SQLException {
		return verify(plan, targetConnection, chunkSize, Map.of());
	}

	static BulkMigrationJobVerificationResult verify(final BulkMigrationJobPlan plan,
			final Connection targetConnection, final int chunkSize,
			final Map<String, List<String>> columnsByTask) throws SQLException {
		final List<BulkMigrationJobTaskVerificationResult> results = new ArrayList<>();
		for (final BulkMigrationJobTask task : plan.getTasks()) {
			if (!(task.getKeysetSource() instanceof JdbcBulkMigrationKeysetSource source)) {
				throw new CommandException("Declarative verification requires a JDBC keyset source: "
						+ task.getTaskId());
			}
			final var target = new JdbcBulkMigrationKeysetSource(targetConnection,
					source.getTable(), source.getKeyColumnNames());
			final List<String> columns = columnsByTask.getOrDefault(task.getTaskId(),
					defaultVerificationColumns(task));
			final var verification = BulkMigrationVerifier.verify(source.getTable(),
					source.iterator(null), target.getTable(), target.iterator(null), columns,
					chunkSize);
			results.add(new BulkMigrationJobTaskVerificationResult(task.getTaskId(), columns,
					verification));
		}
		return new BulkMigrationJobVerificationResult(List.copyOf(results));
	}

	private static List<String> defaultVerificationColumns(final BulkMigrationJobTask task) {
		if (task.getOptions().getMode() == BulkMigrationMode.UPSERT) {
			return BulkUpsertPlan.resolve(task.getKeysetSource().getTable(),
					task.getOptions().getBulkUpsertOption()).getStagingColumns().stream()
						.map(column -> column.getName()).toList();
		}
		final var option = task.getOptions().getBulkOption();
		return task.getKeysetSource().getTable().getColumns().stream()
				.filter(column -> !column.isHidden()
						&& (column.getFormula() == null || column.getFormula().isEmpty())
						&& (!column.isIdentity() || option.isKeepIdentity()))
				.map(column -> column.getName()).toList();
	}

	static BulkMigrationJobPlan withExplicitDatabaseCheckpointStores(
			final BulkMigrationJobPlan plan, final Connection targetConnection)
			throws SQLException {
		final List<BulkMigrationJobTask> tasks = new ArrayList<>(plan.getTasks().size());
		for (final BulkMigrationJobTask task : plan.getTasks()) {
			if (task.getCheckpointStore() != null || task.getOptions().getCheckpointMode()
					!= BulkMigrationCheckpointMode.DATABASE) {
				tasks.add(task);
				continue;
			}
			tasks.add(BulkMigrationJobTask.builder().taskId(task.getTaskId())
					.sourceTable(task.getSourceTable()).keysetSource(task.getKeysetSource())
					.options(task.getOptions()).chunkListener(task.getChunkListener())
					.checkpointStore(new JdbcBulkMigrationCheckpointStore(targetConnection,
							task.getOptions().getCheckpointTableName())).build());
		}
		return BulkMigrationJobPlanner.plan(tasks, plan.getLifecycle());
	}
}
