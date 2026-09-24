/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.File;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.migration.MigrationPlanCommand;
import com.sqlapp.gradle.plugins.extension.MigrationExtension;
import com.zaxxer.hikari.HikariDataSource;

class MigrationPlanTaskTest {
	@Test
	void registersReadOnlyMigrationPlan() {
		final var project = ProjectBuilder.builder().build();
		project.getPluginManager().apply(DbPlugin.class);
		final var task = assertInstanceOf(MigrationPlanTask.class,
				project.getTasks().getByName("migrationPlan"));
		final var command = assertInstanceOf(MigrationPlanCommand.class, task.createCommand());
		final File output = new File(project.getProjectDir(), "build/migration-plan.json");
		task.getOutputFile().set(output);
		final var extension = project.getExtensions().getByType(MigrationExtension.class);
		extension.getDataSource().getJdbcUrl().set("jdbc:hsqldb:mem:plan_task");
		try {
			task.initializeCommand(command);
			assertEquals(output, command.getOutputFile());
		} finally {
			((HikariDataSource) command.getDataSource()).close();
		}
	}
}
