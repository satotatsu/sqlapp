/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.JdbcBatchMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.JdbcStreamingMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.MigrationSnapshotExecutionResult;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

import lombok.Getter;
import lombok.Setter;

/** Executes one YAML-defined atomic SCD2 snapshot. */
@Getter
@Setter
public class ExecuteMigrationSnapshotCommand extends AbstractDataSourceCommand {
	private File configurationFile;
	private DataSource sourceDataSource;
	private MigrationSnapshotExecutionResult result;
	private MigrationSnapshotExecutionReport report;

	@Override
	protected void doRun() {
		result = null;
		report = null;
		if (getDataSource() == null) throw new CommandException("Migration snapshot target data source is required.");
		if (sourceDataSource == null) throw new CommandException("Migration snapshot source data source is required.");
		final var resolved = new MigrationSnapshotConfigurationResolver().resolve(configurationFile);
		execute(sourceDataSource, source -> executeNoTranAndClose(getDataSource(), target -> {
			final var dialect = DialectResolver.getInstance().getDialect(target);
			final var setBased = SetBasedMigrationSnapshotResolver.find(dialect);
			result = JdbcStreamingMigrationSnapshotExecutor.execute(source, target, resolved.sourceTable(),
					resolved.targetTable(), resolved.definition(), resolved.effectiveAt(), resolved.fetchSize(),
					resolved.batchSize());
			final var metadata = target.getMetaData();
			report = new MigrationSnapshotExecutionReport(MigrationSnapshotExecutionReport.CURRENT_FORMAT_VERSION,
					java.time.Instant.now(), resolved.definition().id(), resolved.configurationFingerprint(),
					name(resolved.sourceTable()),
					name(resolved.targetTable()), resolved.definition().keyColumns(),
					resolved.definition().trackedColumns(), resolved.definition().expireMissingRows(),
					resolved.effectiveAt(), resolved.fetchSize(), resolved.batchSize(),
					metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion(),
					setBased.<String>map(x -> x.getClass().getName())
							.orElse(JdbcBatchMigrationSnapshotExecutor.class.getName()),
					setBased.map(x -> x.supportsCallerTransactionAtomicity()).orElse(true), result.expiredRows(),
					result.insertedRows(), result.unchangedRows());
		}));
		if (resolved.reportFile() != null) {
			new MigrationSnapshotExecutionReportIO().write(resolved.reportFile(), report);
			info("Migration snapshot report: ", resolved.reportFile());
		}
		info("Migration snapshot completed: ", result);
	}

	private static String name(final Table table) {
		final var parts = new java.util.ArrayList<String>();
		if (table.getCatalogName() != null) parts.add(table.getCatalogName());
		if (table.getSchemaName() != null) parts.add(table.getSchemaName());
		parts.add(table.getName());
		return String.join(".", parts);
	}
}
