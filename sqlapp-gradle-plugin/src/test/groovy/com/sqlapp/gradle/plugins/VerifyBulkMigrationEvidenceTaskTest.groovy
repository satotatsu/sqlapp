/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

import org.gradle.api.Project
import org.junit.jupiter.api.Test

class VerifyBulkMigrationEvidenceTaskTest extends AbstractTaskTest {
	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('verifyBulkMigrationEvidence', VerifyBulkMigrationEvidenceTask).get()
		assertNotNull(task)
		assertFalse(task.operationalReportFile.isPresent())
		assertFalse(task.verificationReportFile.isPresent())
		assertFalse(task.configurationFile.isPresent())
		assertFalse(task.assessmentReportFile.isPresent())
		assertFalse(task.ddlVerificationReportFile.isPresent())
		assertFalse(task.outputFile.isPresent())
		assertTrue(task.requireSuccessfulExecution.get())
		assertTrue(task.requireMatchingData.get())
		assertTrue(task.requireProvenance.get())
	}
}
