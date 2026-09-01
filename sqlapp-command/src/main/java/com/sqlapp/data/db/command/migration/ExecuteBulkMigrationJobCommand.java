/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.sql.Connection;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseManager;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;

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

	@Override
	protected void doRun() {
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
			final BulkMigrationJobPlan resolved = new BulkMigrationJobConfigurationResolver()
					.resolve(configurationFile, sourceConnection);
			executePlan(resolved);
		});
	}

	private void executePlan(final BulkMigrationJobPlan executionPlan) {
		executionPlan.validateUnchanged();
		execute(getDataSource(), targetConnection -> {
			// The chunk executor owns commit/rollback boundaries, including durable
			// checkpoint writes. AbstractDataSourceCommand otherwise starts a transaction.
			targetConnection.setAutoCommit(true);
			if (leaseConfiguration == null) {
				result = BulkMigrationJobExecutor.executePlan(targetConnection, executionPlan,
						listener, chunkListener);
				return;
			}
			if (leaseConfiguration.mode() == BulkMigrationJobLeaseMode.FILE) {
				final BulkMigrationJobLeaseManager manager =
						BulkMigrationJobLeaseManagerFactory.create(null,
								leaseConfiguration);
				result = BulkMigrationJobExecutor.executePlan(targetConnection, executionPlan,
						listener, chunkListener, manager);
				return;
			}
			try (Connection leaseConnection = getDataSource().getConnection()) {
				leaseConnection.setAutoCommit(true);
				final BulkMigrationJobLeaseManager manager =
						BulkMigrationJobLeaseManagerFactory.create(leaseConnection,
								leaseConfiguration);
				result = BulkMigrationJobExecutor.executePlan(targetConnection, executionPlan,
						listener, chunkListener, manager);
			}
		});
		info("Bulk migration job completed: ", executionPlan.getFingerprint());
	}
}
