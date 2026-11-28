/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

import com.sqlapp.data.schemas.Schema
import com.sqlapp.data.schemas.Table
import com.sqlapp.data.schemas.Trigger

class ImportLogicMigrationResponseTaskTest extends AbstractTaskTest {
	@Test
	void registersTaskAndMapsWorkDirectory() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('importLogicMigrationResponse', ImportLogicMigrationResponseTask).get()
		assertEquals(DbPlugin.GROUP_TRANSFER, task.group)
		def work = new File(testProjectDir, 'logic/run-001')
		task.workDir.set(work)
		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(work, command.workDir)
	}

	@Test
	void importsResponseWrittenForGeneratedRequest() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def source = new File(testProjectDir, 'oracle.xml')
		def oracle = new Schema('APP').setProductName('Oracle').setProductMajorVersion(19)
		oracle.tables.add(new Table('ORDERS'))
		def trigger = new Trigger('ORDERS_TRG')
		trigger.tableSchemaName = 'APP'
		trigger.tableName = 'ORDERS'
		trigger.actionTiming = 'BEFORE'
		trigger.actionOrientation = 'ROW'
		trigger.addEventManipulation('INSERT')
		trigger.setDefinition('CREATE OR REPLACE TRIGGER APP.ORDERS_TRG BEFORE INSERT ON APP.ORDERS FOR EACH ROW BEGIN NULL; END;')
		oracle.triggers.add(trigger)
		oracle.writeXml(source)
		def target = new File(testProjectDir, 'postgres.xml')
		def postgres = new Schema('public').setProductName('PostgreSQL').setProductMajorVersion(16)
		postgres.tables.add(new Table('orders'))
		postgres.writeXml(target)
		def work = new File(testProjectDir, 'logic/run-001')
		def generate = project.tasks.named('generateLogicMigrationRequest', GenerateLogicMigrationRequestTask).get()
		generate.sourceSchemaFile.set(source)
		generate.targetSchemaFile.set(target)
		generate.workDir.set(work)
		generate.exec()
		def requestId = generate.internalCommand().result.requestId()
		new File(work, 'response/response.json').setText("""{
  "format": "sqlapp-logic-response", "formatVersion": 1, "requestId": "${requestId}",
  "results": [{"sourceId": "s1", "status": "UNRESOLVED", "artifactIds": [], "notes": ["Not needed in PostgreSQL"],
    "suggestion": {"type": "NOT_NEEDED", "reason": "Empty body", "checks": []}}],
  "artifacts": []
}""", 'UTF-8')
		def task = project.tasks.named('importLogicMigrationResponse', ImportLogicMigrationResponseTask).get()
		task.workDir.set(work)
		task.exec()
		assertTrue(new File(work, 'import/import.json').isFile())
		assertEquals('UNRESOLVED', task.internalCommand().report.objects()[0].status().name())
	}
}
