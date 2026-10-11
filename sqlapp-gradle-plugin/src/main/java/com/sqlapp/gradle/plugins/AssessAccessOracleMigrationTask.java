/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.work.DisableCachingByDefault;
import com.sqlapp.data.db.command.migration.assessment.AssessAccessOracleMigrationCommand;

/** Compatibility task with the Oracle target preset. */
@DisableCachingByDefault(because = "Assessment must rerun its failure policy")
public abstract class AssessAccessOracleMigrationTask extends AssessDatabaseMigrationTask {
	public AssessAccessOracleMigrationTask() {
		getTargetDatabase().set("oracle");
		getTargetDatabase().disallowChanges();
	}

	@Override
	protected AssessAccessOracleMigrationCommand createCommand() {
		return new AssessAccessOracleMigrationCommand();
	}

	@Override
	public AssessAccessOracleMigrationCommand internalCommand() {
		return (AssessAccessOracleMigrationCommand) super.internalCommand();
	}
}
