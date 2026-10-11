/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode

class GenerateAccessBulkMigrationJobConfigurationTaskTest extends AbstractTaskTest {
	@Test
	void registersDefaultsAndMapsProperties() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('generateAccessBulkMigrationJobConfiguration',
				GenerateAccessBulkMigrationJobConfigurationTask).get()
		assertEquals(10_000, task.chunkSize.get())
		assertTrue(task.resume.get())
		assertTrue(task.verification.get())
		assertTrue(task.operationalReport.get())
		assertEquals(BulkMigrationCheckpointMode.DATABASE, task.checkpointMode.get())
		assertEquals(300L, task.leaseDurationSeconds.get())
		def assessment = new File(testProjectDir, 'assessment.json'); assessment.text = '{}'
		def schema = new File(testProjectDir, 'schema.xml'); schema.text = '<schema name="access"/>'
		def output = new File(testProjectDir, 'job.yaml')
		def ddlVerification = new File(testProjectDir, 'ddl-verification.json'); ddlVerification.text = '{}'
		task.assessmentReportFile.set(assessment)
		task.schemaFile.set(schema)
		task.outputFile.set(output)
		task.jobId.set('access-load')
		task.verificationReportFile.set('reports/access-verification.json')
		task.repairPlanOnMismatchFile.set('reports/access-repair.json')
		task.operationalReportFile.set('reports/access-operations.json')
		task.checkpointMode.set(BulkMigrationCheckpointMode.FILE)
		task.checkpointDirectory.set('state/access')
		task.leaseOwnerId.set('ci-worker-1')
		task.leaseDurationSeconds.set(600L)
		task.expectedAssessmentReportFingerprint.set('sha256:' + 'a' * 64)
		task.ddlVerificationReportFile.set(ddlVerification)
		task.expectedDdlVerificationReportFingerprint.set('sha256:' + 'b' * 64)
		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(assessment, command.assessmentReportFile)
		assertEquals(schema, command.schemaFile)
		assertEquals(output, command.outputFile)
		assertEquals('access-load', command.jobId)
		assertEquals(10_000, command.chunkSize)
		assertTrue(command.resume)
		assertTrue(command.verification)
		assertEquals('reports/access-verification.json', command.verificationReportFile)
		assertEquals('reports/access-repair.json', command.repairPlanOnMismatchFile)
		assertTrue(command.operationalReport)
		assertEquals('reports/access-operations.json', command.operationalReportFile)
		assertEquals(BulkMigrationCheckpointMode.FILE, command.checkpointMode)
		assertEquals('state/access', command.checkpointDirectory)
		assertEquals('ci-worker-1', command.leaseOwnerId)
		assertEquals(600L, command.leaseDurationSeconds)
		assertEquals('sha256:' + 'a' * 64, command.expectedAssessmentReportFingerprint)
		assertEquals(ddlVerification, command.ddlVerificationReportFile)
		assertEquals('sha256:' + 'b' * 64, command.expectedDdlVerificationReportFingerprint)
	}
}
