/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;


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
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairTask;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationTargetValidator;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskVerificationResult;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.CompositeBulkMigrationJobListener;

import lombok.Getter;
import lombok.Setter;

/**
 * Executes one validated bulk migration plan against the configured data
 * source.
 */
@Getter
@Setter
public class ExecuteBulkMigrationJobCommand extends AbstractDataSourceCommand {
	private BulkMigrationJobPlan plan;
	private File configurationFile;
	private String expectedConfigurationFingerprint;
	private Long maxConfigurationFileSizeBytes;
	private File assessmentReportFile;
	private File ddlVerificationReportFile;
	private File targetValidationReportFile;
	private String expectedTargetValidationReportFingerprint;
	private Long maxTargetValidationAgeSeconds;
	private Long maxTargetValidationReportFileSizeBytes;
	private String targetEnvironmentId;
	private BulkMigrationTargetValidationReport approvedTargetValidationReport;
	private String approvedTargetValidationReportFingerprint;
	private DataSource sourceDataSource;
	private BulkMigrationJobListener listener = BulkMigrationJobListener.NO_OP;
	private ChunkedBulkMigrationListener chunkListener = ChunkedBulkMigrationListener.NO_OP;
	private BulkMigrationJobLeaseConfiguration leaseConfiguration;
	private BulkMigrationJobResult result;
	private BulkMigrationJobVerificationResult verificationResult;

	@Override
	protected void doRun() {
		result = null;
		verificationResult = null;
		approvedTargetValidationReport = null;
		approvedTargetValidationReportFingerprint = null;
		if (getDataSource() == null) {
			throw new CommandException("Bulk migration target data source is required.");
		}
		if (plan != null && configurationFile != null) {
			throw new CommandException("Specify either a bulk migration plan or configurationFile, not both.");
		}
		if (plan == null && configurationFile == null) {
			throw new CommandException("Bulk migration plan or configurationFile is required.");
		}
		BulkMigrationExecutionApprovalValidator.validateConfigurationFingerprint(configurationFile,
				expectedConfigurationFingerprint);
		BulkMigrationExecutionApprovalValidator.validateConfigurationFileSize(configurationFile,
				maxConfigurationFileSizeBytes);
		BulkMigrationExecutionApprovalValidator.validateArtifactInputs(configurationFile, assessmentReportFile,
				ddlVerificationReportFile);
		BulkMigrationExecutionApprovalValidator.validateTargetInputs(configurationFile, targetValidationReportFile,
				expectedTargetValidationReportFingerprint, maxTargetValidationAgeSeconds,
				maxTargetValidationReportFileSizeBytes, targetEnvironmentId);
		if (configurationFile != null && sourceDataSource == null) {
			throw new CommandException("Bulk migration source data source is required for configurationFile.");
		}
		if (plan != null) {
			executePlan(plan);
			return;
		}
		execute(sourceDataSource, sourceConnection -> {
			final var resolved = new BulkMigrationJobConfigurationResolver().resolveJob(configurationFile,
					sourceConnection, expectedConfigurationFingerprint, maxConfigurationFileSizeBytes);
			BulkMigrationExecutionApprovalValidator.validateArtifacts(resolved.provenance(), assessmentReportFile,
					ddlVerificationReportFile);
			final var approvedTarget = BulkMigrationExecutionApprovalValidator.validateTargetReport(
					targetValidationReportFile, expectedTargetValidationReportFingerprint, targetEnvironmentId,
					maxTargetValidationAgeSeconds == null ? 0 : maxTargetValidationAgeSeconds,
					maxTargetValidationReportFileSizeBytes, resolved);
			approvedTargetValidationReport = approvedTarget == null ? null : approvedTarget.report();
			approvedTargetValidationReportFingerprint = approvedTarget == null ? null : approvedTarget.fingerprint();
			final BulkMigrationArtifactProvenance executionProvenance = BulkMigrationExecutionApprovalValidator
					.executionProvenance(resolved.provenance(), approvedTargetValidationReportFingerprint);
			if (leaseConfiguration != null && resolved.leaseConfiguration() != null) {
				throw new CommandException(
						"Specify lease configuration either in the job file " + "or as a command property, not both.");
			}
			executePlan(resolved.plan(), resolved.leaseConfiguration(), listener, resolved.reportConfiguration(),
					resolved.verificationConfiguration(), sourceConnection, executionProvenance);
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
			final BulkMigrationJobConfigurationResolver.OperationalReportConfiguration reportConfiguration) {
		executePlan(executionPlan, executionLeaseConfiguration, configuredListener, reportConfiguration, null);
	}

	private void executePlan(final BulkMigrationJobPlan executionPlan,
			final BulkMigrationJobLeaseConfiguration executionLeaseConfiguration,
			final BulkMigrationJobListener configuredListener,
			final BulkMigrationJobConfigurationResolver.OperationalReportConfiguration reportConfiguration,
			final BulkMigrationJobConfigurationResolver.VerificationConfiguration verificationConfiguration) {
		executePlan(executionPlan, executionLeaseConfiguration, configuredListener, reportConfiguration,
				verificationConfiguration, null);
	}

	private void executePlan(final BulkMigrationJobPlan executionPlan,
			final BulkMigrationJobLeaseConfiguration executionLeaseConfiguration,
			final BulkMigrationJobListener configuredListener,
			final BulkMigrationJobConfigurationResolver.OperationalReportConfiguration reportConfiguration,
			final BulkMigrationJobConfigurationResolver.VerificationConfiguration verificationConfiguration,
			final Connection sourceConnection) {
		executePlan(executionPlan, executionLeaseConfiguration, configuredListener, reportConfiguration,
				verificationConfiguration, sourceConnection, null);
	}

	private void executePlan(final BulkMigrationJobPlan executionPlan,
			final BulkMigrationJobLeaseConfiguration executionLeaseConfiguration,
			final BulkMigrationJobListener configuredListener,
			final BulkMigrationJobConfigurationResolver.OperationalReportConfiguration reportConfiguration,
			final BulkMigrationJobConfigurationResolver.VerificationConfiguration verificationConfiguration,
			final Connection sourceConnection, final BulkMigrationArtifactProvenance provenance) {
		executionPlan.validateUnchanged();
		execute(getDataSource(), targetConnection -> {
			// The chunk executor owns commit/rollback boundaries, including durable
			// checkpoint writes. AbstractDataSourceCommand otherwise starts a transaction.
			targetConnection.setAutoCommit(true);
			final BulkMigrationJobPlan effectivePlan = reportConfiguration == null ? executionPlan
					: withExplicitDatabaseCheckpointStores(executionPlan, targetConnection);
			final BulkMigrationJobListener executionListener;
			final BulkMigrationOperationalReportJobListener reportListener;
			if (reportConfiguration == null) {
				reportListener = null;
				executionListener = configuredListener;
			} else {
				reportListener = new BulkMigrationOperationalReportJobListener(effectivePlan,
						reportConfiguration.targetFile(), () -> null, () -> null, reportConfiguration.failurePolicy(),
						failure -> {
						}, provenance);
				executionListener = configuredListener == BulkMigrationJobListener.NO_OP ? reportListener
						: CompositeBulkMigrationJobListener.of(configuredListener, reportListener);
			}
			try {
				BulkMigrationExecutionApprovalValidator.validateTargetDatabaseIdentity(approvedTargetValidationReport,
						targetConnection);
				BulkMigrationTargetValidator.validate(targetConnection, effectivePlan);
			} catch (SQLException | RuntimeException | Error rejection) {
				try {
					executionListener.onJobRejected(effectivePlan.getFingerprint(), rejection);
				} catch (RuntimeException listenerFailure) {
					rejection.addSuppressed(listenerFailure);
				}
				throw rejection;
			}
			if (executionLeaseConfiguration == null) {
				result = BulkMigrationJobExecutor.executePlan(targetConnection, effectivePlan, executionListener,
						chunkListener);
			} else if (executionLeaseConfiguration.mode() == BulkMigrationJobLeaseMode.FILE) {
				final BulkMigrationJobLeaseManager manager = BulkMigrationJobLeaseManagerFactory.create(null,
						executionLeaseConfiguration);
				result = BulkMigrationJobExecutor.executePlan(targetConnection, effectivePlan, executionListener,
						chunkListener, manager);
			} else {
				try (Connection leaseConnection = getDataSource().getConnection()) {
					leaseConnection.setAutoCommit(true);
					final BulkMigrationJobLeaseManager manager = BulkMigrationJobLeaseManagerFactory
							.create(leaseConnection, executionLeaseConfiguration);
					result = BulkMigrationJobExecutor.executePlan(targetConnection, effectivePlan, executionListener,
							chunkListener, manager);
				}
			}
			if (verificationConfiguration != null) {
				try (var ignored = BulkMigrationVerificationScope.open(verificationConfiguration.isolation(),
						java.util.Objects.requireNonNull(sourceConnection,
								"sourceConnection is required for verification"),
						targetConnection)) {
					verificationResult = verify(effectivePlan, targetConnection, verificationConfiguration.chunkSize(),
							verificationConfiguration.columnsByTask());
					if (verificationConfiguration.targetFile() != null) {
						new BulkMigrationVerificationReportIO().write(verificationConfiguration.targetFile(),
								effectivePlan.getFingerprint(), verificationConfiguration.isolation(),
								verificationConfiguration.maxReportedMismatches(), verificationResult, provenance);
					}
					if (!verificationResult.isMatch()
							&& verificationConfiguration.repairPlanOnMismatchFile() != null) {
						writeRepairPlan(effectivePlan, targetConnection, verificationResult,
								verificationConfiguration.repairPlanOnMismatchFile());
					}
					if (verificationConfiguration.failOnMismatch() && !verificationResult.isMatch()) {
						throw new CommandException("Bulk migration verification failed: "
								+ verificationResult.getMismatchedTasks() + " task(s) mismatched.");
					}
				} catch (SQLException | RuntimeException | Error failure) {
					if (reportListener != null) {
						try {
							reportListener.onJobFailed(effectivePlan.getFingerprint(), failure);
						} catch (RuntimeException reportFailure) {
							failure.addSuppressed(reportFailure);
						}
					}
					throw failure;
				}
			}
		});
		info("Bulk migration job completed: ", executionPlan.getFingerprint());
	}

	private static void writeRepairPlan(final BulkMigrationJobPlan plan, final Connection targetConnection,
			final BulkMigrationJobVerificationResult verification, final java.nio.file.Path targetFile)
			throws SQLException {
		final var repairPlan = repairPlan(plan, targetConnection, verification);
		new BulkMigrationJobRepairPlanReportIO().write(targetFile, repairPlan);
	}

	static com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlan repairPlan(final BulkMigrationJobPlan plan,
			final Connection targetConnection, final BulkMigrationJobVerificationResult verification)
			throws SQLException {
		verification.validateAgainst(plan);
		final List<BulkMigrationJobRepairTask> tasks = new ArrayList<>(plan.getTasks().size());
		for (int i = 0; i < plan.getTasks().size(); i++) {
			final BulkMigrationJobTask task = plan.getTasks().get(i);
			if (task.getKeysetSource() == null) {
				throw new CommandException("Declarative repair planning requires a JDBC keyset source: "
						+ task.getTaskId());
			}
			tasks.add(BulkMigrationJobRepairTask.builder().taskId(task.getTaskId())
					.expectedKeysetSource(task.getKeysetSource()).target(task.getEffectiveTargetTable())
					.verificationResult(verification.getTasks().get(i).getVerificationResult())
					.options(BulkMigrationRepairOption.builder().columnMappings(task.getColumnMappings())
							.bulkUpsertOption(task.getOptions().getBulkUpsertOption()).build())
					.build());
		}
		return BulkMigrationJobRepairPlanner.plan(targetConnection, tasks);
	}

	static BulkMigrationJobVerificationResult verifyWithIsolation(final BulkMigrationJobPlan plan,
			final Connection targetConnection, final int chunkSize, final Map<String, List<String>> columnsByTask,
			final BulkMigrationVerificationIsolation isolation) throws SQLException {
		if (isolation == null) {
			throw new IllegalArgumentException("isolation must not be null");
		}
		if (isolation == BulkMigrationVerificationIsolation.DEFAULT) {
			return verify(plan, targetConnection, chunkSize, columnsByTask);
		}
		try (var ignored = BulkMigrationVerificationScope.open(isolation, targetConnection)) {
			return verify(plan, targetConnection, chunkSize, columnsByTask);
		}
	}

	static BulkMigrationJobVerificationResult verify(final BulkMigrationJobPlan plan, final Connection targetConnection,
			final int chunkSize) throws SQLException {
		return verify(plan, targetConnection, chunkSize, Map.of());
	}

	static BulkMigrationJobVerificationResult verify(final BulkMigrationJobPlan plan, final Connection targetConnection,
			final int chunkSize, final Map<String, List<String>> columnsByTask) throws SQLException {
		java.util.Objects.requireNonNull(plan, "plan").validateUnchanged();
		final List<BulkMigrationJobTaskVerificationResult> results = new ArrayList<>();
		for (final BulkMigrationJobTask task : plan.getTasks()) {
			if (!(task.getKeysetSource() instanceof JdbcBulkMigrationKeysetSource source)) {
				throw new CommandException(
						"Declarative verification requires a JDBC keyset source: " + task.getTaskId());
			}
			final List<String> targetKeys = source.getKeyColumnNames().stream()
					.map(task::getTargetColumnName).toList();
			final var target = new JdbcBulkMigrationKeysetSource(targetConnection, task.getEffectiveTargetTable(),
					targetKeys);
			final List<String> columns = columnsByTask.getOrDefault(task.getTaskId(), defaultVerificationColumns(task));
			final List<String> targetColumns = columns.stream().map(task::getTargetColumnName).toList();
			final var verification = BulkMigrationVerifier.verify(source, target, columns, targetColumns, chunkSize);
			results.add(new BulkMigrationJobTaskVerificationResult(task.getTaskId(), verification.getColumns(),
					verification));
		}
		return new BulkMigrationJobVerificationResult(plan.getFingerprint(), List.copyOf(results))
				.validateAgainst(plan);
	}

	private static List<String> defaultVerificationColumns(final BulkMigrationJobTask task) {
		return BulkMigrationVerificationColumns.resolve(task.getEffectiveTargetTable(), task.getOptions().getMode(),
				task.getOptions().getBulkOption(), task.getOptions().getBulkUpsertOption()).stream()
				.map(task::getSourceColumnName).toList();
	}

	static BulkMigrationJobPlan withExplicitDatabaseCheckpointStores(final BulkMigrationJobPlan plan,
			final Connection targetConnection) throws SQLException {
		final List<BulkMigrationJobTask> tasks = new ArrayList<>(plan.getTasks().size());
		for (final BulkMigrationJobTask task : plan.getTasks()) {
			if (task.getCheckpointStore() != null
					|| task.getOptions().getCheckpointMode() != BulkMigrationCheckpointMode.DATABASE) {
				tasks.add(task);
				continue;
			}
			tasks.add(BulkMigrationJobTask.builder().taskId(task.getTaskId()).sourceTable(task.getSourceTable())
					.keysetSource(task.getKeysetSource()).targetTable(task.getTargetTable())
					.columnMappings(task.getColumnMappings()).requireEmptyTarget(task.isRequireEmptyTarget())
					.options(task.getOptions())
					.chunkListener(task.getChunkListener())
					.checkpointStore(new JdbcBulkMigrationCheckpointStore(targetConnection,
							task.getOptions().getCheckpointTableName()))
					.build());
		}
		return BulkMigrationJobPlanner.plan(plan.getJobId(), tasks, plan.getLifecycle());
	}
}
