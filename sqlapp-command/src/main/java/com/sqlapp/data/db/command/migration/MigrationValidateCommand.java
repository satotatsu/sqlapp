/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import com.sqlapp.data.schemas.Table;

/** Explicit, read-only checksum validation, independent of the migration opt-in. */
public class MigrationValidateCommand extends MigrationCommand {
	@Override
	protected void doRun() {
		resetValidationResult();
		requireValidationDirectory();
		final DbVersionHandler handler = createDbVersionHandler();
		executeNoTranAndClose(getDataSource(), connection -> {
			final var dialect = getDialect(connection);
			final var files = new DbVersionFileHandler();
			files.setUpSqlDirectory(getSqlDirectory());
			files.setRecursive(isRecursive());
			files.setEncoding(getEncoding());
			files.setSqlSplitter(dialect.createSqlSplitter());
			final Table definition = handler.createVersionTableDefinition(getSchemaChangeLogTableName());
			final Table history = handler.getTable(connection, dialect, definition);
			if (history == null) {
				info("No migration history table; no applied migrations to validate.");
				validateChecksums(definition, handler, files.read());
			} else {
				handler.load(connection, dialect, history);
				validateChecksums(history, handler, files.read());
			}
		});
	}
}
