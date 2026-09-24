/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.migration.MigrationPlanCommand;

class MigrationPlanTaskTest {
	@Test
	void registersReadOnlyMigrationPlan() {
		final var project = ProjectBuilder.builder().build();
		project.getPluginManager().apply(DbPlugin.class);
		final var task = assertInstanceOf(MigrationPlanTask.class,
				project.getTasks().getByName("migrationPlan"));
		assertInstanceOf(MigrationPlanCommand.class, task.createCommand());
	}
}
