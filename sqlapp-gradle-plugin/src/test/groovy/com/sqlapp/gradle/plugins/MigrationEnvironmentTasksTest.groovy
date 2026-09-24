/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull

import org.gradle.api.Project
import org.junit.jupiter.api.Test

import com.sqlapp.data.db.command.migration.MigrationEnvironmentSnapshot
import com.sqlapp.data.db.command.migration.MigrationEnvironmentSnapshotIO
import com.sqlapp.data.db.command.migration.MigrationPlan

class MigrationEnvironmentTasksTest extends AbstractTaskTest {
	private File snapshot(String environment) {
		def file = new File(testProjectDir, environment + '.json')
		new MigrationEnvironmentSnapshotIO().write(file.toPath(),
				new MigrationEnvironmentSnapshot(environment, 1_800_000_000_000L,
						new MigrationPlan.DatabaseIdentity('HSQL', '2.7.4', 'sha256:' + '1'.repeat(64)),
						[], []))
		return file
	}

	@Test
	void tasksAreRegisteredAndComparisonPropertiesAreMapped() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def capture = project.tasks.named('migrationEnvironmentSnapshot',
				MigrationEnvironmentSnapshotTask).get()
		assertNotNull(capture)
		assertFalse(capture.environmentId.isPresent())
		assertFalse(capture.outputFile.isPresent())
		def compare = project.tasks.named('compareMigrationEnvironments',
				CompareMigrationEnvironmentsTask).get()
		def production = snapshot('production')
		def staging = snapshot('staging')
		compare.snapshotFiles.from(production, staging)
		compare.baselineEnvironmentId.set('production')
		compare.outputFile.set(new File(testProjectDir, 'comparison.json'))
		compare.failOnDifferences.set(true)
		compare.exec()
		assertEquals('production', compare.internalCommand().baselineEnvironmentId)
		assertEquals(2, compare.internalCommand().snapshotFiles.size())
		assertEquals(new File(testProjectDir, 'comparison.json'), compare.internalCommand().outputFile)
		assertEquals(true, compare.internalCommand().failOnDifferences)
	}

	@Test
	void capturePropertiesAreMappedAndSnapshotIsWritten() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def extension = project.extensions.getByName('migration')
		extension.sqlDirectory.set(new File(testProjectDir, 'sql'))
		new File(testProjectDir, 'sql').mkdirs()
		extension.dataSource {
			driverClassName = 'org.hsqldb.jdbc.JDBCDriver'
			jdbcUrl = 'jdbc:hsqldb:mem:environment_snapshot_task'
			username = 'SA'
			password = ''
		}
		def task = project.tasks.named('migrationEnvironmentSnapshot',
				MigrationEnvironmentSnapshotTask).get()
		def output = new File(testProjectDir, 'captured.json')
		task.environmentId.set('test')
		task.outputFile.set(output)
		task.exec()
		assertEquals('test', task.internalCommand().environmentId)
		assertEquals(output, task.internalCommand().outputFile)
		assertEquals('test', new MigrationEnvironmentSnapshotIO().read(output.toPath()).environmentId())
	}
}
