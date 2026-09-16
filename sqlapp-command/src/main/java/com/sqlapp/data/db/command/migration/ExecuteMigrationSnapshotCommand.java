/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.JdbcStreamingMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.MigrationSnapshotExecutionResult;

import lombok.Getter;
import lombok.Setter;

/** Executes one YAML-defined atomic SCD2 snapshot. */
@Getter
@Setter
public class ExecuteMigrationSnapshotCommand extends AbstractDataSourceCommand {
	private File configurationFile;
	private DataSource sourceDataSource;
	private MigrationSnapshotExecutionResult result;

	@Override
	protected void doRun() {
		result = null;
		if (getDataSource() == null) throw new CommandException("Migration snapshot target data source is required.");
		if (sourceDataSource == null) throw new CommandException("Migration snapshot source data source is required.");
		final var resolved = new MigrationSnapshotConfigurationResolver().resolve(configurationFile);
		execute(sourceDataSource, source -> execute(getDataSource(), target -> result =
				JdbcStreamingMigrationSnapshotExecutor.execute(source, target, resolved.sourceTable(),
						resolved.targetTable(), resolved.definition(), resolved.effectiveAt(), resolved.fetchSize(),
						resolved.batchSize())));
		info("Migration snapshot completed: ", result);
	}
}
