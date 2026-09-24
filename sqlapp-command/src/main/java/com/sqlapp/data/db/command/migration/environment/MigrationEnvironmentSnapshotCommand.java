/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.environment;

import com.sqlapp.data.db.command.migration.schema.DbVersionHandler;
import com.sqlapp.data.db.command.migration.schema.MigrationCommand;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanCommand;
import com.sqlapp.data.db.command.migration.schema.RepeatableMigrationHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/** Captures one database's migration state without changing it. */
@Getter
@Setter
public class MigrationEnvironmentSnapshotCommand extends MigrationCommand {
	private String environmentId;
	private File outputFile;
	private MigrationEnvironmentSnapshot snapshot;

	@Override
	protected void doRun() {
		if (environmentId == null || environmentId.isBlank()) {
			throw new CommandException("environmentId is required");
		}
		if (outputFile == null) {
			throw new CommandException("outputFile is required");
		}
		executeNoTranAndClose(getDataSource(), connection -> {
			final var dialect = getDialect(connection);
			final DbVersionHandler handler = createDbVersionHandler();
			final Table definition = handler.createVersionTableDefinition(getSchemaChangeLogTableName());
			final Table history = handler.getTable(connection, dialect, definition);
			final var versioned = new ArrayList<MigrationEnvironmentSnapshot.VersionedEntry>();
			if (history != null) {
				handler.load(connection, dialect, history);
				final boolean hasChecksum = history.getColumns().contains(DbVersionHandler.CHECKSUM_COLUMN);
				for (final var row : history.getRows()) {
					final Long version = handler.getId(row);
					if (version != null) {
						versioned.add(new MigrationEnvironmentSnapshot.VersionedEntry(version, handler.getStatus(row),
								hasChecksum ? (String) row.get(DbVersionHandler.CHECKSUM_COLUMN) : null));
					}
				}
			}
			versioned.sort(Comparator.comparingLong(MigrationEnvironmentSnapshot.VersionedEntry::version));
			final var repeatables = new ArrayList<MigrationEnvironmentSnapshot.RepeatableEntry>();
			final RepeatableMigrationHandler repeatableHandler = new RepeatableMigrationHandler();
			final Table repeatableDefinition = repeatableHandler.definition(getSchemaChangeLogTableName());
			final Table repeatableHistory = handler.getTable(connection, dialect, repeatableDefinition);
			if (repeatableHistory != null) {
				for (final Map.Entry<String, String> entry : repeatableHandler.load(connection, dialect,
						repeatableHistory).entrySet()) {
					repeatables.add(new MigrationEnvironmentSnapshot.RepeatableEntry(entry.getKey(), entry.getValue()));
				}
			}
			repeatables.sort(Comparator.comparing(MigrationEnvironmentSnapshot.RepeatableEntry::name));
			snapshot = new MigrationEnvironmentSnapshot(environmentId, System.currentTimeMillis(),
					MigrationPlanCommand.databaseIdentity(connection), versioned, repeatables);
			new MigrationEnvironmentSnapshotIO().write(outputFile.toPath(), snapshot);
			info("Migration environment snapshot: ", outputFile.getAbsolutePath());
		});
	}
}
