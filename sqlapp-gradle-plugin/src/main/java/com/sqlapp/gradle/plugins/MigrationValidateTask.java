/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.schema.MigrationCommand;
import com.sqlapp.data.db.command.migration.schema.MigrationValidateCommand;

@DisableCachingByDefault
public abstract class MigrationValidateTask extends MigrationTask {
	@Override
	protected MigrationCommand createCommand() {
		return new MigrationValidateCommand();
	}
}
