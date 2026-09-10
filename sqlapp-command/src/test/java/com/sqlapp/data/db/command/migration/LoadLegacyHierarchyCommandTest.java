/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.exceptions.CommandException;

class LoadLegacyHierarchyCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testRejectSchemaThatDoesNotMatchLoadPlan() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"PUBLIC\"/>");
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, "sha256:different");
		File planFile = new File(temporaryDirectory, "load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		assertThrows(CommandException.class, command::run);
	}

	@Test
	void testReportInvalidSchemaXmlAsCommandException() throws Exception {
		File schemaFile = new File(temporaryDirectory, "invalid-schema.xml");
		Files.writeString(schemaFile.toPath(), "<schema>");
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile,
				new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		File planFile = new File(temporaryDirectory, "invalid-load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);
		assertTrue(exception.getMessage().contains("Failed to read target schema XML"));
		assertTrue(exception.getMessage().contains("invalid-schema.xml"));
	}

	private void initialize(LegacyMigrationLoadPlan plan, File schemaFile,
			String schemaFingerprint) {
		plan.setMigrationId("test-migration");
		plan.setContractFile(new File(temporaryDirectory, "contract.yaml").getPath());
		plan.setContractFingerprint("sha256:contract");
		plan.setSchemaFile(schemaFile.getPath());
		plan.setSchemaFingerprint(schemaFingerprint);
		var dataSet = new LegacyMigrationLoadPlan.LoadDataSet();
		dataSet.setId("root");
		dataSet.setFileName("root.csv");
		dataSet.setStagingTable("STG_ROOT");
		dataSet.setTargetTable("ROOT");
		dataSet.getSourceBusinessKey().add("ID");
		var field = new LegacyMigrationLoadPlan.LoadField();
		field.setCsvPosition(1);
		field.setStagingColumn("ID");
		field.setTargetColumn("ID");
		field.setDataType("INT");
		field.setExtracted(true);
		dataSet.getFields().add(field);
		plan.getDataSets().add(dataSet);
	}
}
