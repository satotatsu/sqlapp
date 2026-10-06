/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

class ValidateBulkMigrationTargetTaskTest {
	@Test
	void registersAndMapsTheReadOnlyTargetValidationTask() throws Exception {
		final var project = ProjectBuilder.builder().build();
		project.getPluginManager().apply(DbPlugin.class);

		final var task = assertInstanceOf(ValidateBulkMigrationTargetTask.class,
				project.getTasks().getByName("validateBulkMigrationTarget"));
		assertFalse(task.getConfigurationFile().isPresent());
		assertFalse(task.getExpectedConfigurationFingerprint().isPresent());
		assertFalse(task.getMaxConfigurationFileSizeBytes().isPresent());
		assertFalse(task.getMaxSchemaFileSizeBytes().isPresent());
		assertFalse(task.getAssessmentReportFile().isPresent());
		assertFalse(task.getDdlVerificationReportFile().isPresent());
		assertFalse(task.getMaxApprovalArtifactFileSizeBytes().isPresent());
		assertFalse(task.getReportFile().isPresent());
		assertFalse(task.getTargetEnvironmentId().isPresent());

		final var configuration = project.getLayout().getBuildDirectory().file("migration/job.yaml").get();
		final var assessment = project.getLayout().getBuildDirectory().file("migration/assessment.json").get();
		final var ddlVerification = project.getLayout().getBuildDirectory().file("migration/ddl.json").get();
		final var report = project.getLayout().getBuildDirectory().file("migration/target-validation.json").get();
		task.getConfigurationFile().set(configuration);
		task.getExpectedConfigurationFingerprint().set("sha256:" + "0".repeat(64));
		task.getMaxConfigurationFileSizeBytes().set(1_048_576L);
		task.getMaxSchemaFileSizeBytes().set(4_194_304L);
		task.getAssessmentReportFile().set(assessment);
		task.getDdlVerificationReportFile().set(ddlVerification);
		task.getMaxApprovalArtifactFileSizeBytes().set(8_388_608L);
		task.getReportFile().set(report);
		task.getTargetEnvironmentId().set("production-oracle");
		task.getSourceDataSource().getJdbcUrl().set("jdbc:hsqldb:mem:validate_task_source");
		task.getSourceDataSource().getUsername().set("SA");
		task.beforeRun(task.internalCommand());

		assertEquals(configuration.getAsFile(), task.internalCommand().getConfigurationFile());
		assertEquals("sha256:" + "0".repeat(64),
				task.internalCommand().getExpectedConfigurationFingerprint());
		assertEquals(1_048_576L, task.internalCommand().getMaxConfigurationFileSizeBytes());
		assertEquals(4_194_304L, task.internalCommand().getMaxSchemaFileSizeBytes());
		assertEquals(assessment.getAsFile(), task.internalCommand().getAssessmentReportFile());
		assertEquals(ddlVerification.getAsFile(), task.internalCommand().getDdlVerificationReportFile());
		assertEquals(8_388_608L, task.internalCommand().getMaxApprovalArtifactFileSizeBytes());
		assertEquals(report.getAsFile(), task.internalCommand().getReportFile());
		assertEquals("production-oracle", task.internalCommand().getTargetEnvironmentId());
		assertTrue(task.internalCommand().getSourceDataSource() instanceof HikariDataSource);
		((HikariDataSource) task.internalCommand().getSourceDataSource()).close();
	}
}
