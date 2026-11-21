/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

import com.sqlapp.data.schemas.Schema
import com.sqlapp.data.schemas.Table
import com.sqlapp.data.schemas.Trigger

class GenerateLogicMigrationRequestTaskTest extends AbstractTaskTest {
	@Test
	void registersTaskAndMapsAllProperties() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('generateLogicMigrationRequest', GenerateLogicMigrationRequestTask).get()
		assertEquals(DbPlugin.GROUP_TRANSFER, task.group)
		def source = new File(testProjectDir, 'source.xml')
		def target = new File(testProjectDir, 'target.xml')
		def work = new File(testProjectDir, 'work/run-001')
		task.sourceSchemaFile.set(source)
		task.targetSchemaFile.set(target)
		task.workDir.set(work)
		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(source, command.sourceSchemaFile)
		assertEquals(target, command.targetSchemaFile)
		assertEquals(work, command.workDir)
		assertTrue(command.exclusions.isEmpty())
	}

	@Test
	void writesRequestIntoNewWorkDirectory() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('generateLogicMigrationRequest', GenerateLogicMigrationRequestTask).get()
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
		task.sourceSchemaFile.set(source)
		task.targetSchemaFile.set(target)
		task.workDir.set(work)
		task.exec()
		assertTrue(new File(work, 'request/manifest.json').isFile())
		assertTrue(new File(work, 'request-state.json').isFile())
		def result = task.internalCommand().result
		assertEquals('REQUESTED', result.manifest().objects()[0].eligibility().name())
		assertEquals('TABLE_NAME_ONLY', result.manifest().objects()[0].targetTableResolution().name())
	}
}
