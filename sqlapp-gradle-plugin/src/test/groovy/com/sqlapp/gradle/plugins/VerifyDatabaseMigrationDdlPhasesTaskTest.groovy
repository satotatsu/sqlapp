/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

class VerifyDatabaseMigrationDdlPhasesTaskTest extends AbstractTaskTest {
	@Test
	void mapsDirectoryOnlyWithoutEnablingOptionalPolicies() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('verifyDatabaseMigrationDdlPhases',
				VerifyDatabaseMigrationDdlPhasesTask).get()
		def directory = new File(testProjectDir, 'ddl-phases')
		directory.mkdirs()
		task.directory.set(directory)

		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(directory, command.directory)
		assertNull(command.assessmentReportFile)
		assertNull(command.verificationReportFile)
		assertNull(command.expectedManifestFingerprint)
		assertNull(command.expectedAssessmentReportFingerprint)
		assertNull(command.expectedSourceFingerprint)
		assertNull(command.expectedMappingFingerprint)
		assertNull(command.expectedTargetDatabase)
		assertNull(command.expectedTargetVersion)
		assertFalse(command.failOnUnresolvedAutoNumberStrategies)
		assertFalse(command.failOnIncompleteMapping)
		assertFalse(command.failOnMappingSemanticDifferences)
		assertFalse(command.failOnAssessmentBlockers)
		assertFalse(command.requireDeploymentReady)
		assertFalse(command.requireDataScan)
		assertFalse(command.requireRelationshipsCollected)
		assertFalse(command.requireApprovedFingerprints)
	}

	@Test
	void registersAndMapsDirectory() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('verifyDatabaseMigrationDdlPhases',
				VerifyDatabaseMigrationDdlPhasesTask).get()
		assertFalse(task.directory.isPresent())
		assertFalse(task.failOnUnresolvedAutoNumberStrategies.get())
		assertFalse(task.failOnIncompleteMapping.get())
		assertFalse(task.failOnMappingSemanticDifferences.get())
		assertFalse(task.failOnAssessmentBlockers.get())
		assertFalse(task.requireDeploymentReady.get())
		assertFalse(task.requireDataScan.get())
		assertFalse(task.requireRelationshipsCollected.get())
		assertFalse(task.requireApprovedFingerprints.get())
		def directory = new File(testProjectDir, 'ddl-phases')
		directory.mkdirs()
		def report = new File(testProjectDir, 'assessment.json')
		report.text = '{}'
		task.directory.set(directory)
		task.assessmentReportFile.set(report)
		def verificationReport = new File(testProjectDir, 'verification.json')
		task.verificationReportFile.set(verificationReport)
		task.expectedManifestFingerprint.set('sha256:' + '4' * 64)
		task.expectedAssessmentReportFingerprint.set('sha256:' + '3' * 64)
		task.expectedSourceFingerprint.set('sha256:' + '1' * 64)
		task.expectedMappingFingerprint.set('sha256:' + '2' * 64)
		task.expectedTargetDatabase.set('oracle')
		task.expectedTargetVersion.set('19c')
		task.failOnUnresolvedAutoNumberStrategies.set(true)
		task.failOnIncompleteMapping.set(true)
		task.failOnMappingSemanticDifferences.set(true)
		task.failOnAssessmentBlockers.set(true)
		task.requireDeploymentReady.set(true)
		task.requireDataScan.set(true)
		task.requireRelationshipsCollected.set(true)
		task.requireApprovedFingerprints.set(true)
		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(directory, command.directory)
		assertEquals(report, command.assessmentReportFile)
		assertEquals(verificationReport, command.verificationReportFile)
		assertEquals(task.expectedManifestFingerprint.get(), command.expectedManifestFingerprint)
		assertEquals(task.expectedAssessmentReportFingerprint.get(), command.expectedAssessmentReportFingerprint)
		assertEquals(task.expectedSourceFingerprint.get(), command.expectedSourceFingerprint)
		assertEquals(task.expectedMappingFingerprint.get(), command.expectedMappingFingerprint)
		assertEquals('oracle', command.expectedTargetDatabase)
		assertEquals('19c', command.expectedTargetVersion)
		assertTrue(command.failOnUnresolvedAutoNumberStrategies)
		assertTrue(command.failOnIncompleteMapping)
		assertTrue(command.failOnMappingSemanticDifferences)
		assertTrue(command.failOnAssessmentBlockers)
		assertTrue(command.requireDeploymentReady)
		assertTrue(command.requireDataScan)
		assertTrue(command.requireRelationshipsCollected)
		assertTrue(command.requireApprovedFingerprints)
	}
}
