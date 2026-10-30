/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class TransferDatabaseTaskTest extends AbstractTaskTest {
	@ParameterizedTest @ValueSource(booleans=[false,true])
	void insertsKeylessDuplicatesWithOptionalTargetOnlyIdentity(boolean generatedKey) {
		def sourceUrl='jdbc:hsqldb:mem:keyless_task_source_'+UUID.randomUUID()
		def targetUrl='jdbc:hsqldb:mem:keyless_task_target_'+UUID.randomUUID()
		def source=new org.hsqldb.jdbc.JDBCDataSource(); source.setUrl(sourceUrl); source.setUser('SA'); source.setPassword('')
		source.connection.withCloseable {c -> c.createStatement().withCloseable {s -> s.execute('CREATE TABLE ITEMS(VALUE INTEGER)'); s.execute('INSERT INTO ITEMS VALUES(7),(7),(NULL)') }}
		def project=createProject(testProjectDir); project.plugins.apply(DbPlugin)
		def task=project.tasks.named('transferDatabase',TransferDatabaseTask).get()
		def file=new File(testProjectDir,'keyless.yaml')
		file.text='formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: '+(generatedKey?'CREATE_MISSING_WITH_IDENTITY':'CREATE_MISSING')+'\n'
		task.configurationFile.set(file)
		task.sourceDataSource.jdbcUrl.set(sourceUrl); task.sourceDataSource.username.set('SA'); task.sourceDataSource.password.set('')
		task.dataSource.jdbcUrl.set(targetUrl); task.dataSource.username.set('SA'); task.dataSource.password.set('')
		task.exec()
		task.allowExecution.set(true); task.expectedConfigurationFingerprint.set(task.internalCommand().review.prepared().job().fingerprint())
		task.exec()
		assertTrue(task.internalCommand().result.verificationResult().isMatch())
		assertEquals(3,task.internalCommand().result.copyResult().processedRows)
		def target=new org.hsqldb.jdbc.JDBCDataSource(); target.setUrl(targetUrl); target.setUser('SA'); target.setPassword('')
		target.connection.withCloseable { c -> c.metaData.getPrimaryKeys(null,'PUBLIC','ITEMS').withCloseable { keys ->
			assertEquals(generatedKey,keys.next()); if(generatedKey) assertEquals('ID',keys.getString('COLUMN_NAME'))
		} }
	}
	@ParameterizedTest @ValueSource(booleans=[false,true])
	void executesNameMappingFromTheSameSavedJob(boolean schemaOnly) {
		def sourceUrl = 'jdbc:hsqldb:mem:mapped_task_source_' + UUID.randomUUID()
		def targetUrl = 'jdbc:hsqldb:mem:mapped_task_target_' + UUID.randomUUID()
		def source = new org.hsqldb.jdbc.JDBCDataSource(); source.setUrl(sourceUrl); source.setUser('SA'); source.setPassword('')
		source.connection.withCloseable { c -> c.createStatement().withCloseable { s ->
			s.execute('CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)'); s.execute('INSERT INTO ITEMS VALUES(42)')
		} }
		def project = createProject(testProjectDir); project.plugins.apply(DbPlugin)
		def task = project.tasks.named('transferDatabase', TransferDatabaseTask).get()
		def file = new File(testProjectDir, 'mapped.yaml')
		file.text = 'formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\ndataMode: ' + (schemaOnly ? 'SCHEMA_ONLY' : 'JDBC_DIRECT') + '\nschemaMode: CREATE_MISSING\nmappingFile: names.yaml\n'
		new File(testProjectDir, 'names.yaml').text = 'formatVersion: 1\ntables: {ITEMS: {targetTable: COPY, columns: {ID: ITEM_ID}}}\n'
		task.configurationFile.set(file)
		task.sourceDataSource.jdbcUrl.set(sourceUrl); task.sourceDataSource.username.set('SA'); task.sourceDataSource.password.set('')
		task.dataSource.jdbcUrl.set(targetUrl); task.dataSource.username.set('SA'); task.dataSource.password.set('')
		task.exec()
		assertNull(task.internalCommand().result)
		task.allowExecution.set(true)
		task.expectedConfigurationFingerprint.set(task.internalCommand().review.prepared().job().fingerprint())
		task.exec()
		assertEquals(com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService.Phase.COMPLETE, task.internalCommand().result.phase())
		if (!schemaOnly) assertTrue(task.internalCommand().result.verificationResult().isMatch())
		def target = new org.hsqldb.jdbc.JDBCDataSource(); target.setUrl(targetUrl); target.setUser('SA'); target.setPassword('')
		target.connection.withCloseable { c -> c.createStatement().withCloseable { s ->
			s.executeQuery('SELECT COUNT(ITEM_ID) FROM COPY').withCloseable { r -> r.next(); assertEquals(schemaOnly ? 0 : 1,r.getInt(1)) }
		} }
	}

	@ParameterizedTest @ValueSource(booleans=[false,true])
	void executesSchemaOnlySharedJobWithoutCopyingKeylessTableRows(boolean applyDifferences) {
		def sourceUrl = 'jdbc:hsqldb:mem:schema_task_source_' + UUID.randomUUID()
		def targetUrl = 'jdbc:hsqldb:mem:schema_task_target_' + UUID.randomUUID()
		def source = new org.hsqldb.jdbc.JDBCDataSource(); source.setUrl(sourceUrl); source.setUser('SA'); source.setPassword('')
		source.connection.withCloseable { c -> c.createStatement().withCloseable { s ->
			s.execute(applyDifferences ? 'CREATE TABLE ITEMS(ID INTEGER, NAME VARCHAR(40))' : 'CREATE TABLE ITEMS(ID INTEGER)'); s.execute('INSERT INTO ITEMS(ID) VALUES(NULL),(42)')
		} }
		def project = createProject(testProjectDir); project.plugins.apply(DbPlugin)
		def task = project.tasks.named('transferDatabase', TransferDatabaseTask).get()
		def file = new File(testProjectDir, 'schema-only.yaml')
		file.text = 'formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: ' + (applyDifferences ? 'APPLY_DIFFERENCES' : 'CREATE_MISSING') + '\ndataMode: SCHEMA_ONLY\n'
		if (applyDifferences) {
			def existing = new org.hsqldb.jdbc.JDBCDataSource(); existing.setUrl(targetUrl); existing.setUser('SA'); existing.setPassword('')
			existing.connection.withCloseable { c -> c.createStatement().withCloseable { s -> s.execute('CREATE TABLE ITEMS(ID INTEGER)'); s.execute('INSERT INTO ITEMS VALUES(5)') } }
		}
		task.configurationFile.set(file)
		task.sourceDataSource.jdbcUrl.set(sourceUrl); task.sourceDataSource.username.set('SA'); task.sourceDataSource.password.set('')
		task.dataSource.jdbcUrl.set(targetUrl); task.dataSource.username.set('SA'); task.dataSource.password.set('')
		task.exec()
		assertNull(task.internalCommand().result)
		def pin = task.internalCommand().review.prepared().job().fingerprint()
		task.allowExecution.set(true); task.expectedConfigurationFingerprint.set(pin)
		task.exec()
		assertEquals(com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService.Phase.COMPLETE, task.internalCommand().result.phase())
		assertNull(task.internalCommand().result.copyResult())
		def target = new org.hsqldb.jdbc.JDBCDataSource(); target.setUrl(targetUrl); target.setUser('SA'); target.setPassword('')
		target.connection.withCloseable { c ->
			c.createStatement().withCloseable { s -> s.executeQuery('SELECT COUNT(*) FROM ITEMS').withCloseable { r -> r.next(); assertEquals(applyDifferences ? 1 : 0, r.getInt(1)) } }
			if(applyDifferences) c.metaData.getColumns(null,'PUBLIC','ITEMS','NAME').withCloseable { r -> assertTrue(r.next()) }
		}
	}
	@Test void registersReadOnlyDefaultsAndValidatesBeforeCreatingPools() {
		def project = createProject(testProjectDir); project.plugins.apply(DbPlugin)
		def task = project.tasks.named('transferDatabase', TransferDatabaseTask).get()
		assertFalse(task.allowExecution.get()); assertNotNull(task.sourceDataSource)
		assertTrue(task.workspaceDirectory.get().asFile.path.contains('sqlapp-transfer'))
		task.configurationFile.set(new File(testProjectDir, 'missing.yaml')); task.allowExecution.set(true)
		assertTrue(assertThrows(IllegalArgumentException, { task.exec() }).message.contains('expectedConfigurationFingerprint'))
		assertNull(task.internalCommand().dataSource); assertNull(task.internalCommand().sourceDataSource)
	}
	@ParameterizedTest @ValueSource(booleans=[false,true])
	void testKitReviewsThenExecutesSharedDefinitionAndRevalidatesReplay(boolean createMissing) {
		def sourceUrl = 'jdbc:hsqldb:file:' + new File(testProjectDir, 'source/db').path.replace('\\','/') + ';shutdown=true'
		def targetUrl = 'jdbc:hsqldb:file:' + new File(testProjectDir, 'target/db').path.replace('\\','/') + ';shutdown=true'
		for (def url : [sourceUrl,targetUrl]) {
			def ds = new org.hsqldb.jdbc.JDBCDataSource(); ds.setUrl(url); ds.setUser('SA'); ds.setPassword('')
			ds.connection.withCloseable { c -> c.createStatement().withCloseable { s ->
				if(url==sourceUrl||!createMissing)s.execute('CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)')
				if(url == sourceUrl) s.execute('INSERT INTO ITEMS VALUES(1),(2)')
			}}
		}
		new File(testProjectDir,'settings.gradle').text = "rootProject.name='direct-transfer-test'\n"
		new File(testProjectDir,'transfer.yaml').text = 'formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\nchunkSize: 1\n'.replace('REQUIRE_EXISTING',createMissing?'CREATE_MISSING':'REQUIRE_EXISTING')
		new File(testProjectDir,'build.gradle').text = """plugins { id 'com.sqlapp.db' }
tasks.named('transferDatabase') {
 configurationFile.set(layout.projectDirectory.file('transfer.yaml'))
 allowExecution.set(providers.gradleProperty('execute').map { it.toBoolean() }.orElse(false))
 expectedConfigurationFingerprint.set(providers.gradleProperty('pin'))
 sourceDataSource { jdbcUrl.set('${sourceUrl}'); username.set('SA'); password.set('') }
 dataSource { jdbcUrl.set('${targetUrl}'); username.set('SA'); password.set('') }
 doLast { file('review-pin.txt').text = internalCommand().review.prepared().job().fingerprint() }
}
"""
		def metadata = new Properties(); getClass().classLoader.getResourceAsStream('plugin-under-test-metadata.properties').withCloseable { metadata.load(it) }
		def classpath = metadata.getProperty('implementation-classpath').split(File.pathSeparator).collect {new File(it)}
		for(def type : [org.hsqldb.jdbc.JDBCDataSource,com.sqlapp.data.db.dialect.hsql.Hsql]) {
			def location = new File(type.protectionDomain.codeSource.location.toURI()); classpath.add(location)
			if(location.isDirectory()) { def resources = new File(location,'../../../resources/main').canonicalFile; if(resources.isDirectory()) classpath.add(resources) }
		}
		def runner = { List<String> args -> GradleRunner.create().withProjectDir(testProjectDir).withPluginClasspath(classpath).withArguments(['transferDatabase','--offline','--stacktrace'] + args) }
		assertEquals(TaskOutcome.SUCCESS,runner([]).build().task(':transferDatabase').outcome)
		def target = new org.hsqldb.jdbc.JDBCDataSource(); target.setUrl(targetUrl); target.setUser('SA'); target.setPassword('')
		if(!createMissing)target.connection.withCloseable { c -> c.createStatement().withCloseable { s -> s.executeQuery('SELECT COUNT(*) FROM ITEMS').withCloseable { r -> r.next(); assertEquals(0,r.getInt(1)) } } }
		else target.connection.withCloseable { c -> c.metaData.getTables(null,'PUBLIC','ITEMS',null).withCloseable {r->assertFalse(r.next())} }
		def pin = new File(testProjectDir,'review-pin.txt').text
		assertEquals(TaskOutcome.SUCCESS,runner(['-Pexecute=true','-Ppin='+pin]).build().task(':transferDatabase').outcome)
		assertEquals(TaskOutcome.FAILED,runner(['-Pexecute=true','-Ppin='+pin]).buildAndFail().task(':transferDatabase').outcome)
		target.connection.withCloseable { c -> c.createStatement().withCloseable { s -> s.executeQuery('SELECT COUNT(*) FROM ITEMS').withCloseable { r -> r.next(); assertEquals(2,r.getInt(1)) } } }
		assertTrue(new File(testProjectDir,'build/sqlapp-transfer/direct').listFiles().any {new File(it,'transfer-result.json').isFile()})
	}
	@Test void sourceSetupFailureReleasesTheTaskOwnedTargetPool() {
		def project = createProject(testProjectDir); project.plugins.apply(DbPlugin)
		def task = project.tasks.named('transferDatabase', TransferDatabaseTask).get()
		def definition = new File(testProjectDir,'transfer.yaml')
		definition.text = 'formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\n'
		task.configurationFile.set(definition)
		task.dataSource.jdbcUrl.set('jdbc:hsqldb:mem:source_setup_cleanup_' + UUID.randomUUID())
		task.dataSource.username.set('SA'); task.dataSource.password.set('')
		assertThrows(IllegalArgumentException, {task.exec()})
		assertTrue(((com.zaxxer.hikari.HikariDataSource)task.internalCommand().dataSource).isClosed())
		assertNull(task.internalCommand().sourceDataSource)
	}
}
