/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		final var command = new MigrationCommand();
		extension.initializeCommand(command);
		assertFalse(command.isChecksumValidation());
		extension.getChecksumValidation().set(true);
		extension.initializeCommand(command);
		assertTrue(command.isChecksumValidation());
	}
}
