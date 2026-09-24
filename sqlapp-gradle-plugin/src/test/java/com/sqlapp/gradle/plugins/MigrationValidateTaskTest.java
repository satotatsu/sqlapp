/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.migration.MigrationCommand;
import com.sqlapp.data.db.command.migration.MigrationValidateCommand;
import com.sqlapp.gradle.plugins.extension.MigrationExtension;

class MigrationValidateTaskTest {
	@Test
	void registersReadOnlyValidationAndDefaultsToNoAutomaticChecksums() {
		final var project = ProjectBuilder.builder().build();
		project.getPluginManager().apply(DbPlugin.class);
		final var task = assertInstanceOf(MigrationValidateTask.class,
				project.getTasks().getByName("migrationValidate"));
		assertInstanceOf(MigrationValidateCommand.class, task.createCommand());
		final var extension = project.getExtensions().getByType(MigrationExtension.class);
		assertFalse(extension.getChecksumValidation().get());
		assertFalse(extension.getRejectOutOfOrder().get());
		assertFalse(extension.getRejectNonTransactional().get());
		assertFalse(extension.getRequireDownMigration().get());
		final var command = new MigrationCommand();
		extension.initializeCommand(command);
		assertFalse(command.isChecksumValidation());
		extension.getChecksumValidation().set(true);
		extension.initializeCommand(command);
		assertTrue(command.isChecksumValidation());
		extension.getRejectOutOfOrder().set(true);
		extension.initializeCommand(command);
		assertTrue(command.isRejectOutOfOrder());
		extension.getRejectNonTransactional().set(true);
		extension.initializeCommand(command);
		assertTrue(command.isRejectNonTransactional());
		extension.getRequireDownMigration().set(true);
		extension.initializeCommand(command);
		assertTrue(command.isRequireDownMigration());
		final File expected = new File(project.getProjectDir(), "expected.xml");
		extension.getPreMigrationSchemaFile().set(expected);
		extension.initializeCommand(command);
		assertEquals(expected, command.getPreMigrationSchemaFile());
		final File plan = new File(project.getProjectDir(), "expected-plan.json");
		extension.getExpectedPlanFile().set(plan);
		extension.getExpectedPlanMaxAgeSeconds().set(3600L);
		extension.initializeCommand(command);
		assertEquals(plan, command.getExpectedPlanFile());
		assertEquals(java.time.Duration.ofHours(1), command.getExpectedPlanMaxAge());
	}
}
