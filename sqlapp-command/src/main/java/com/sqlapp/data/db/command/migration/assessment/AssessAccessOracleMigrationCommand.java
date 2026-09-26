/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

/** Compatibility entry point. Prefer AssessDatabaseMigrationCommand with targetDatabase=oracle. */
public class AssessAccessOracleMigrationCommand extends AssessDatabaseMigrationCommand {
	public AssessAccessOracleMigrationCommand() { super.setTargetDatabase("oracle"); }

	@Override
	public void setTargetDatabase(final String database) {
		if (!"oracle".equalsIgnoreCase(database)) {
			throw new IllegalArgumentException("Use AssessDatabaseMigrationCommand for a target other than Oracle");
		}
		super.setTargetDatabase("oracle");
	}
}
