/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class TransferFileTaskTest extends AbstractTaskTest {
	@Test
	void replacesOccupiedTargetFromPinnedFilePackage() {
		def source = new org.hsqldb.jdbc.JDBCDataSource(url: 'jdbc:hsqldb:mem:file_staged_source_' + UUID.randomUUID(), user: 'SA')
		def target = new org.hsqldb.jdbc.JDBCDataSource(url: 'jdbc:hsqldb:mem:file_staged_target_' + UUID.randomUUID(), user: 'SA')
		try {
			[source, target].eachWithIndex { db, i ->
				db.connection.withCloseable { c -> c.createStatement().withCloseable { s ->
					s.execute('CREATE TABLE ITEMS(ID INTEGER PRIMARY KEY)')
					s.execute('INSERT INTO ITEMS VALUES(' + (i + 1) + ')')
				} }
			}
			def job = new File(testProjectDir, 'transfer.yaml')
			job.setText('formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\ndataMode: FILE\ntargetPreparation: STAGED_REPLACE\nfiles: {directory: packages}\n', 'UTF-8')
			def exported = new com.sqlapp.data.db.command.migration.transfer.TransferFileExportService().export(job.toPath(), [from: source])
			def project = createProject(testProjectDir)
			project.plugins.apply(DbPlugin)
			def task = project.tasks.named('importTransferFile', ImportTransferFileTask).get()
			task.manifestFile.set(exported.file().toFile())
			task.expectedManifestFingerprint.set(exported.fingerprint())
			task.allowExecution.set(true)
			task.dataSource.jdbcUrl.set(target.url)
			task.dataSource.username.set('SA')
			task.dataSource.password.set('')
			task.exec()
			assertEquals('COMMITTED', task.internalCommand().result.staging().state().name())
			assertEquals([1L], task.internalCommand().result.staging().committedRows().values().toList())
		} finally {
			[source, target].each { db -> db.connection.withCloseable { c ->
				c.createStatement().withCloseable { s -> s.execute('SHUTDOWN') }
			} }
		}
	}

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
	@CsvSource(['false, CSV, UTF-8, VALIDATE, false, false, false', 'true, CSV, UTF-8, VALIDATE, false, false, false',
		'false, JSONL, UTF-8, NONE, false, false, false', 'true, JSONL, UTF-8, NONE, false, false, false',
		'false, TSV, UTF-8, VALIDATE, false, false, false', 'true, TSV, UTF-8, VALIDATE, false, false, false',
		'true, CSV, windows-31j, NONE, false, false, false', 'true, TSV, UTF-16LE, SKIP, false, false, false',
		'true, CSV, UTF-8, VALIDATE, true, false, false', 'true, TSV, UTF-8, VALIDATE, true, false, false', 'true, JSONL, UTF-8, NONE, true, false, false',
		'true, CSV, UTF-8, VALIDATE, true, true, false', 'true, TSV, UTF-8, VALIDATE, true, true, false', 'true, JSONL, UTF-8, NONE, true, true, false',
		'true, CSV, UTF-8, VALIDATE, false, false, true', 'true, TSV, UTF-8, VALIDATE, true, true, true', 'false, JSONL, UTF-8, NONE, true, false, true'])
	void testKitExportsAndImportsInSeparateRunsWithSharedJobAndNeverSkipsReplayValidation(boolean createMissing, String format, String encoding, String headerMode, boolean keyless, boolean generatedKey, boolean mapped) {
		def targetTable = mapped ? 'COPY_ITEMS' : 'ITEMS'
		def targetId = mapped ? 'ITEM_KEY' : 'ID'
		def targetName = mapped ? 'LABEL' : 'NAME'
		def targetTimestamp = mapped ? 'MODIFIED_AT' : 'TS'
		def sourceUrl = 'jdbc:hsqldb:file:' + new File(testProjectDir, 'source/db').path.replace('\\', '/') + ';shutdown=true'
		def targetUrl = 'jdbc:hsqldb:file:' + new File(testProjectDir, 'target/db').path.replace('\\', '/') + ';shutdown=true'
		for (def url : [sourceUrl, targetUrl]) {
			def ds = new org.hsqldb.jdbc.JDBCDataSource(); ds.setUrl(url); ds.setUser('SA'); ds.setPassword('')
			ds.connection.withCloseable { connection -> connection.createStatement().withCloseable { statement ->
				if (url == sourceUrl || !createMissing) statement.execute('CREATE TABLE ' + (url == sourceUrl ? 'ITEMS' : targetTable) + '(' + (url == sourceUrl ? 'ID' : targetId) + ' INTEGER NOT NULL' + (keyless ? '' : ' PRIMARY KEY') + ', ' + (url == sourceUrl ? 'NAME' : targetName) + ' VARCHAR(40), ' + (url == sourceUrl ? 'TS' : targetTimestamp) + ' TIMESTAMP(9))')
				if (url == sourceUrl && keyless) statement.execute("INSERT INTO ITEMS VALUES(1,'日本語',TIMESTAMP '2024-02-29 23:59:59.123456789')")
				if (url == sourceUrl) statement.execute("INSERT INTO ITEMS VALUES(1,'日本語',TIMESTAMP '2024-02-29 23:59:59.123456789'),(2,'two',NULL)")
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
'''.replace('REQUIRE_EXISTING', generatedKey ? 'CREATE_MISSING_WITH_IDENTITY' : createMissing ? 'CREATE_MISSING' : 'REQUIRE_EXISTING')
				.replace('nullValue: __NULL__', format == 'JSONL' ? 'format: JSONL' : "format: ${format}, nullValue: __NULL__")
				.replace('directory: packages', "directory: packages, encoding: ${encoding}, headerMode: ${headerMode}")
		if (generatedKey) {
			def job = new File(testProjectDir, 'transfer.yaml')
			job.text = job.text.replace('dataMode: FILE', 'dataMode: FILE\ngeneratedKeyColumn: ROW_ID')
		}
		if (mapped) {
			new File(testProjectDir, 'names.yaml').text = 'formatVersion: 1\ntables: {ITEMS: {targetTable: COPY_ITEMS, columns: {ID: ITEM_KEY, NAME: LABEL, TS: MODIFIED_AT}}}\n'
			def job = new File(testProjectDir, 'transfer.yaml'); job.text += '\nmappingFile: names.yaml\n'
		}
		if (!createMissing && format != 'JSONL') {
			def job = new File(testProjectDir, 'transfer.yaml')
			job.text = job.text.replace(', nullValue: __NULL__', '')
		}
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
		if (mapped) java.nio.file.Files.move(new File(testProjectDir, 'names.yaml').toPath(), new File(testProjectDir, 'names-offline.yaml').toPath())
		assertEquals(TaskOutcome.SUCCESS, runner('importTransferFile').build().task(':importTransferFile').outcome)
		assertEquals(TaskOutcome.FAILED, runner('importTransferFile').buildAndFail().task(':importTransferFile').outcome)
		def target = new org.hsqldb.jdbc.JDBCDataSource(); target.setUrl(targetUrl); target.setUser('SA'); target.setPassword('')
		target.connection.withCloseable { connection -> connection.createStatement().withCloseable { statement ->
			statement.executeQuery('SELECT COUNT(*) FROM ' + targetTable).withCloseable { rows -> rows.next(); assertEquals(keyless ? 3 : 2, rows.getInt(1)) }
			statement.executeQuery('SELECT ' + targetTimestamp + ' FROM ' + targetTable + ' WHERE ' + targetId + '=1').withCloseable { rows -> rows.next(); assertEquals(123456789, rows.getTimestamp(1).nanos) }
			statement.executeQuery('SELECT ' + targetName + ' FROM ' + targetTable + ' WHERE ' + targetId + '=1').withCloseable { rows -> rows.next(); assertEquals('日本語', rows.getString(1)) }
			statement.executeQuery('SELECT ' + targetTimestamp + ' FROM ' + targetTable + ' WHERE ' + targetId + '=2').withCloseable { rows -> rows.next(); assertNull(rows.getTimestamp(1)) }
		}}
		if (generatedKey) target.connection.withCloseable { c -> c.metaData.getPrimaryKeys(null, 'PUBLIC', targetTable).withCloseable { keys ->
			assertTrue(keys.next()); assertEquals('ROW_ID', keys.getString('COLUMN_NAME')); assertFalse(keys.next())
		} }
		assertTrue(new File(testProjectDir, 'build/sqlapp-transfer/import').listFiles().any { new File(it, 'import-result.json').isFile() })
	}
}
