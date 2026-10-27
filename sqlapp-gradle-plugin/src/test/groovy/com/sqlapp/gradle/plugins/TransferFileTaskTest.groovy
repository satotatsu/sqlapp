/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class TransferFileTaskTest extends AbstractTaskTest {
	@Test
	void registersMapsPropertiesAndRejectsUnauthorizedImportBeforePoolConstruction() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def exportTask = project.tasks.named('exportTransferFile', ExportTransferFileTask).get()
		def importTask = project.tasks.named('importTransferFile', ImportTransferFileTask).get()
		assertFalse(importTask.allowExecution.get())
		assertTrue(importTask.workspaceDirectory.get().asFile.path.contains('sqlapp-transfer'))
		assertFalse(exportTask.configurationFile.isPresent())
		def denied = assertThrows(IllegalArgumentException, { importTask.exec() })
		assertTrue(denied.message.contains('allowExecution'))
		assertNull(importTask.internalCommand().dataSource)
		importTask.allowExecution.set(true)
		assertTrue(assertThrows(IllegalArgumentException, { importTask.exec() }).message.contains('expectedManifestFingerprint'))
		assertNull(importTask.internalCommand().dataSource)
		exportTask.configurationFile.set(new File(testProjectDir, 'transfer.yaml'))
		def exportCommand = exportTask.createCommand()
		exportTask.beforeRun(exportCommand)
		assertEquals(exportTask.configurationFile.get().asFile, exportCommand.configurationFile)
		importTask.manifestFile.set(new File(testProjectDir, 'manifest.json'))
		importTask.expectedManifestFingerprint.set('sha256:' + 'a'.repeat(64))
		def importCommand = importTask.createCommand()
		importTask.beforeRun(importCommand)
		assertTrue(importCommand.allowExecution)
		assertEquals(importTask.manifestFile.get().asFile, importCommand.manifestFile)
		assertEquals(importTask.workspaceDirectory.get().asFile, importCommand.workspaceDirectory)
		assertEquals(importTask.expectedManifestFingerprint.get(), importCommand.expectedManifestFingerprint)
	}

	@ParameterizedTest
	@ValueSource(booleans = [false, true])
	void testKitExportsAndImportsInSeparateRunsWithSharedJobAndNeverSkipsReplayValidation(boolean createMissing) {
		def sourceUrl = 'jdbc:hsqldb:file:' + new File(testProjectDir, 'source/db').path.replace('\\', '/') + ';shutdown=true'
		def targetUrl = 'jdbc:hsqldb:file:' + new File(testProjectDir, 'target/db').path.replace('\\', '/') + ';shutdown=true'
		for (def url : [sourceUrl, targetUrl]) {
			def ds = new org.hsqldb.jdbc.JDBCDataSource(); ds.setUrl(url); ds.setUser('SA'); ds.setPassword('')
			ds.connection.withCloseable { connection -> connection.createStatement().withCloseable { statement ->
				if (url == sourceUrl || !createMissing) statement.execute('CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40), TS TIMESTAMP(9))')
				if (url == sourceUrl) statement.execute("INSERT INTO ITEMS VALUES(1,'one',TIMESTAMP '2024-02-29 23:59:59.123456789'),(2,'two',NULL)")
			}}
		}
		new File(testProjectDir, 'settings.gradle').text = "rootProject.name = 'csv-transfer-test'\n"
		new File(testProjectDir, 'transfer.yaml').text = '''formatVersion: 1
source: from
target: to
tables: [ITEMS]
schemaMode: REQUIRE_EXISTING
dataMode: FILE
chunkSize: 1
files: {directory: packages, nullValue: __NULL__}
'''.replace('REQUIRE_EXISTING', createMissing ? 'CREATE_MISSING' : 'REQUIRE_EXISTING')
		new File(testProjectDir, 'build.gradle').text = """plugins { id 'com.sqlapp.db' }
tasks.named('exportTransferFile') {
    configurationFile.set(layout.projectDirectory.file('transfer.yaml'))
    dataSource.jdbcUrl.set('${sourceUrl}')
    dataSource.username.set('SA')
    dataSource.password.set('')
    doLast {
        def snapshot = internalCommand().result
        def props = new Properties()
        props.setProperty('manifest', snapshot.file().toString())
        props.setProperty('fingerprint', snapshot.fingerprint())
        file('export.properties').withOutputStream { props.store(it, null) }
    }
}
tasks.named('importTransferFile') {
    def props = new Properties()
    def saved = file('export.properties')
    if (saved.exists()) saved.withInputStream { props.load(it) }
    manifestFile.set(file(props.getProperty('manifest', 'missing.json')))
    expectedManifestFingerprint.set(props.getProperty('fingerprint', ''))
    allowExecution.set(true)
    dataSource.jdbcUrl.set('${targetUrl}')
    dataSource.username.set('SA')
    dataSource.password.set('')
}
"""
		def metadata = new Properties()
		getClass().classLoader.getResourceAsStream('plugin-under-test-metadata.properties').withCloseable { metadata.load(it) }
		def classpath = metadata.getProperty('implementation-classpath').split(File.pathSeparator).collect { new File(it) }
		for (def type : [org.hsqldb.jdbc.JDBCDataSource, com.sqlapp.data.db.dialect.hsql.Hsql]) {
			def location = new File(type.protectionDomain.codeSource.location.toURI())
			classpath.add(location)
			if (location.isDirectory()) {
				def resources = new File(location, '../../../resources/main').canonicalFile
				if (resources.isDirectory()) classpath.add(resources)
			}
		}
		def runner = { String task -> GradleRunner.create().withProjectDir(testProjectDir).withPluginClasspath(classpath)
				.withArguments(task, '--offline', '--stacktrace') }
		assertEquals(TaskOutcome.SUCCESS, runner('exportTransferFile').build().task(':exportTransferFile').outcome)
		java.nio.file.Files.move(new File(testProjectDir, 'source').toPath(), new File(testProjectDir, 'source-offline').toPath())
		assertEquals(TaskOutcome.SUCCESS, runner('importTransferFile').build().task(':importTransferFile').outcome)
		assertEquals(TaskOutcome.FAILED, runner('importTransferFile').buildAndFail().task(':importTransferFile').outcome)
		def target = new org.hsqldb.jdbc.JDBCDataSource(); target.setUrl(targetUrl); target.setUser('SA'); target.setPassword('')
		target.connection.withCloseable { connection -> connection.createStatement().withCloseable { statement ->
			statement.executeQuery('SELECT COUNT(*) FROM ITEMS').withCloseable { rows -> rows.next(); assertEquals(2, rows.getInt(1)) }
			statement.executeQuery('SELECT TS FROM ITEMS WHERE ID=1').withCloseable { rows -> rows.next(); assertEquals(123456789, rows.getTimestamp(1).nanos) }
		}}
		assertTrue(new File(testProjectDir, 'build/sqlapp-transfer/import').listFiles().any { new File(it, 'import-result.json').isFile() })
	}
}
