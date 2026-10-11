/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

import com.sqlapp.data.db.command.migration.schema.MigrationExecutionReport
import com.sqlapp.data.db.command.migration.schema.MigrationExecutionReportIO
import com.sqlapp.data.db.command.migration.schema.MigrationPlan
import com.sqlapp.exceptions.CommandException

import org.gradle.api.Project
import org.junit.jupiter.api.Test

class VerifyMigrationExecutionReportTaskTest extends AbstractTaskTest {
	@Test
	void testConfiguredPoliciesAreAppliedDuringExecution() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named("verifyMigrationExecutionReport",
				VerifyMigrationExecutionReportTask).get()
		long now = System.currentTimeMillis()
		String plan = 'sha256:' + '1'.repeat(64)
		String connection = 'sha256:' + '2'.repeat(64)
		def report = new MigrationExecutionReport(MigrationExecutionReport.CURRENT_FORMAT_VERSION,
				now, now, true, true, new MigrationPlan.DatabaseIdentity('HSQL', '2.7.4', connection),
				plan, [1L], [1L], null)
		File file = new File(testProjectDir, 'execution.json')
		new MigrationExecutionReportIO().write(file.toPath(), report)
		task.reportFile.set(file)
		task.expectedReportFingerprint.set(project.providers.provider { report.reportFingerprint() })
		task.expectedPlanFingerprint.set(plan)
		task.expectedDatabaseConnectionFingerprint.set(connection)
		task.maxReportAgeSeconds.set(3600L)
		task.requireSuccessful.set(true)
		task.requireAllSelectedCommitted.set(true)
		task.exec()
		assertEquals(report, task.internalCommand().report)
		assertEquals(Long.valueOf(3600), task.internalCommand().maxReportAgeSeconds)
		assertTrue(task.internalCommand().requireSuccessful)
		assertTrue(task.internalCommand().requireAllSelectedCommitted)
		task.expectedPlanFingerprint.set('sha256:' + '3'.repeat(64))
		def failure = assertThrows(CommandException) { task.exec() }
		assertTrue(failure.message.contains('expectedPlanFingerprint'))
	}

	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named("verifyMigrationExecutionReport",
				VerifyMigrationExecutionReportTask).get()
		assertNotNull(task)
		assertFalse(task.reportFile.isPresent())
		assertFalse(task.expectedReportFingerprint.isPresent())
		assertFalse(task.expectedPlanFingerprint.isPresent())
		assertFalse(task.expectedDatabaseConnectionFingerprint.isPresent())
		assertFalse(task.maxReportAgeSeconds.isPresent())
		assertFalse(task.requireSuccessful.get())
		assertFalse(task.requireAllSelectedCommitted.get())
	}
}
