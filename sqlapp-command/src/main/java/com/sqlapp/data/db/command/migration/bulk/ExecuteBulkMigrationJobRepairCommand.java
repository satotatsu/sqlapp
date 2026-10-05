/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.sql.Connection;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairResult;
import com.sqlapp.util.MessageDigests;

import lombok.Getter;
import lombok.Setter;

/** Re-verifies and executes an explicitly approved declarative job repair plan. */
@Getter
@Setter
public class ExecuteBulkMigrationJobRepairCommand extends AbstractDataSourceCommand {
	private File configurationFile;
	private String expectedConfigurationFingerprint;
	private File approvedRepairPlanFile;
	private DataSource sourceDataSource;
	private BulkMigrationJobRepairResult result;

	@Override
	protected void doRun() {
		result = null;
		if (getDataSource() == null) {
			throw new CommandException("Bulk migration target data source is required.");
		}
		if (sourceDataSource == null) {
			throw new CommandException("Bulk migration source data source is required.");
		}
		if (configurationFile == null || !configurationFile.isFile()) {
			throw new CommandException("Bulk migration configuration file is required.");
		}
		if (approvedRepairPlanFile == null || !approvedRepairPlanFile.isFile()) {
			throw new CommandException("Approved bulk migration repair plan file is required.");
		}
		validateConfigurationFingerprint();
		executeNoTranAndClose(sourceDataSource, sourceConnection -> {
			final var resolved = new BulkMigrationJobConfigurationResolver().resolveJob(configurationFile,
					sourceConnection);
			if (resolved.verificationConfiguration() == null) {
				throw new CommandException("Bulk migration verification must be enabled to execute repair.");
			}
			executeNoTranAndClose(getDataSource(), targetConnection -> executeRepair(resolved, targetConnection));
		});
		info("Bulk migration job repair completed: ", result.getPlanFingerprint());
	}

	private void executeRepair(final BulkMigrationJobConfigurationResolver.Resolution resolved,
			final Connection targetConnection) throws Exception {
		final var verification = resolved.verificationConfiguration();
		final var verified = ExecuteBulkMigrationJobCommand.verifyWithIsolation(resolved.plan(), targetConnection,
				verification.chunkSize(), verification.columnsByTask(), verification.isolation());
		final var plan = ExecuteBulkMigrationJobCommand.repairPlan(resolved.plan(), targetConnection, verified);
		final var approved = new BulkMigrationJobRepairPlanReportIO().read(approvedRepairPlanFile.toPath(),
				plan.getFingerprint());
		result = BulkMigrationJobRepairExecutor.execute(targetConnection, plan, approved.planFingerprint());
	}

	private void validateConfigurationFingerprint() {
		if (expectedConfigurationFingerprint == null || expectedConfigurationFingerprint.isBlank()) {
			return;
		}
		if (!expectedConfigurationFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("expectedConfigurationFingerprint must be a lowercase SHA-256 value.");
		}
		final String actual = "sha256:" + MessageDigests.SHA256.checksumAsString(configurationFile);
		if (!expectedConfigurationFingerprint.equals(actual)) {
			throw new CommandException(
					"configurationFile fingerprint does not match expectedConfigurationFingerprint.");
		}
	}
}
