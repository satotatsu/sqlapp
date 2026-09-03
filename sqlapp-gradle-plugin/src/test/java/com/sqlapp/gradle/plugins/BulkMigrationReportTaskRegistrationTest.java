/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class BulkMigrationReportTaskRegistrationTest {
	@Test
	void registersTheReviewOnlyJobRepairPlanReportTask() {
		final var project = ProjectBuilder.builder().build();

		project.getPluginManager().apply(DbPlugin.class);

		final var task = assertInstanceOf(GenerateBulkMigrationJobRepairPlanReportTask.class,
				project.getTasks().getByName("generateBulkMigrationJobRepairPlanReport"));
		assertFalse(task.getPlan().isPresent());
		assertFalse(task.getTargetFile().isPresent());
		assertEquals("generateBulkMigrationJobRepairPlanReport", task.getName());
	}
}
