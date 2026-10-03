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
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskVerificationResult;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.CompositeBulkMigrationJobListener;
import com.sqlapp.util.MessageDigests;

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
	private File assessmentReportFile;
	private File ddlVerificationReportFile;
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
		if (getDataSource() == null) {
			throw new CommandException("Bulk migration target data source is required.");
		}
		if (plan != null && configurationFile != null) {
			throw new CommandException("Specify either a bulk migration plan or configurationFile, not both.");
		}
		if (plan == null && configurationFile == null) {
			throw new CommandException("Bulk migration plan or configurationFile is required.");
		}
		validateExpectedConfigurationFingerprint();
		validateApprovalArtifactInputs();
		if (configurationFile != null && sourceDataSource == null) {
			throw new CommandException("Bulk migration source data source is required for configurationFile.");
		}
		if (plan != null) {
			executePlan(plan);
			return;
		}
		execute(sourceDataSource, sourceConnection -> {
			final var resolved = new BulkMigrationJobConfigurationResolver().resolveJob(configurationFile,
					sourceConnection);
			validateApprovalArtifacts(resolved.provenance());
			if (leaseConfiguration != null && resolved.leaseConfiguration() != null) {
				throw new CommandException(
						"Specify lease configuration either in the job file " + "or as a command property, not both.");
			}
			executePlan(resolved.plan(), resolved.leaseConfiguration(), listener, resolved.reportConfiguration(),
					resolved.verificationConfiguration(), sourceConnection, resolved.provenance());
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

	private void validateExpectedConfigurationFingerprint() {
		if (expectedConfigurationFingerprint == null || expectedConfigurationFingerprint.isBlank()) {
			return;
		}
		if (configurationFile == null) {
			throw new CommandException("expectedConfigurationFingerprint requires configurationFile.");
		}
		if (!expectedConfigurationFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("expectedConfigurationFingerprint must be a lowercase SHA-256 value.");
		}
		try {
			final String actual = "sha256:" + MessageDigests.SHA256.checksumAsString(configurationFile);
			if (!expectedConfigurationFingerprint.equals(actual)) {
				throw new CommandException(
						"configurationFile fingerprint does not match expectedConfigurationFingerprint.");
			}
		} catch (final CommandException e) {
			throw e;
		} catch (final Exception e) {
			throw new CommandException("Could not fingerprint configurationFile: " + e.getMessage(), e);
		}
	}

	private void validateApprovalArtifactInputs() {
		if ((assessmentReportFile != null || ddlVerificationReportFile != null) && configurationFile == null) {
			throw new CommandException("Approval artifact files require configurationFile.");
		}
		validateArtifactFile(assessmentReportFile, "assessmentReportFile");
		validateArtifactFile(ddlVerificationReportFile, "ddlVerificationReportFile");
	}

	private static void validateArtifactFile(final File file, final String property) {
		if (file != null && !file.isFile()) {
			throw new CommandException(property + " must be an existing file.");
		}
	}

	private void validateApprovalArtifacts(final BulkMigrationArtifactProvenance provenance) {
		validateApprovalArtifact(assessmentReportFile,
				provenance == null ? null : provenance.assessmentReportFingerprint(), "assessmentReportFile",
				"assessmentReportFingerprint");
		validateApprovalArtifact(ddlVerificationReportFile,
				provenance == null ? null : provenance.ddlVerificationReportFingerprint(), "ddlVerificationReportFile",
				"ddlVerificationReportFingerprint");
	}

	private static void validateApprovalArtifact(final File file, final String expectedFingerprint,
			final String fileProperty, final String provenanceProperty) {
		BulkMigrationArtifactProvenanceVerifier.verify(file, expectedFingerprint, fileProperty, provenanceProperty);
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
		return BulkMigrationVerificationColumns.resolve(task.getEffectiveSourceTable(), task.getOptions().getMode(),
				task.getOptions().getBulkOption(), task.getOptions().getBulkUpsertOption());
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
					.columnMappings(task.getColumnMappings()).options(task.getOptions())
					.chunkListener(task.getChunkListener())
					.checkpointStore(new JdbcBulkMigrationCheckpointStore(targetConnection,
							task.getOptions().getCheckpointTableName()))
					.build());
		}
		return BulkMigrationJobPlanner.plan(plan.getJobId(), tasks, plan.getLifecycle());
	}
}
