/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.MigrationCommand;
import com.sqlapp.data.db.command.migration.MigrationPlanCommand;

@DisableCachingByDefault
public abstract class MigrationPlanTask extends MigrationTask {
	@Override
	protected MigrationCommand createCommand() {
		return new MigrationPlanCommand();
	}
}
