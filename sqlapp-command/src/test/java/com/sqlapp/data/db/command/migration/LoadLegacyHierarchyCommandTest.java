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

	@Test
	void testRejectLoadPlanThatDisagreesWithItsContract() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"PUBLIC\"/>");
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile,
				new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		plan.getDataSets().getFirst().setFileName("different.csv");
		File planFile = new File(temporaryDirectory, "mismatched-load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);
		assertTrue(exception.getMessage().contains("disagrees with its contract"));
	}

	@Test
	void testRejectLoadPlanThatOmitsContractDataSet() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"PUBLIC\"/>");
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile,
				new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		File contractFile = new File(temporaryDirectory, "contract.yaml");
		var contract = new LegacyMigrationContractIO().read(contractFile);
		var omitted = new com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet();
		omitted.setId("omitted");
		omitted.setSourcePath("OMITTED");
		omitted.setFileName("omitted.csv");
		omitted.setTargetTable("OMITTED");
		var field = new com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field();
		field.setPosition(1);
		field.setSourcePath("OMITTED.ID");
		field.setStagingColumn("ID");
		field.setTargetColumn("ID");
		field.setExtracted(true);
		omitted.getFields().add(field);
		contract.getDataSets().add(omitted);
		new LegacyMigrationContractIO().write(contractFile, contract);
		plan.setContractFingerprint(
				new LegacyMigrationMappingValidator().fingerprint(contractFile));
		File planFile = new File(temporaryDirectory, "omitted-load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);

		assertTrue(exception.getMessage().contains("missing=[omitted]"));
		assertTrue(exception.getMessage().contains("unexpected=[]"));
	}

	@Test
	void testRejectMissingTargetTableBeforeOpeningAConnection() throws Exception {
		File schemaFile = new File(temporaryDirectory, "empty-schema.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"PUBLIC\"/>");
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile,
				new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		File planFile = new File(temporaryDirectory, "missing-target-load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);
		assertTrue(exception.getMessage().contains("Target table was not found in schema"));
	}

	@Test
	void testResolvePlanReferencesRelativeToLoadPlan() throws Exception {
		File schemaFile = new File(temporaryDirectory, "relative-schema.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"PUBLIC\"/>");
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile,
				new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		File contractFile = new File(temporaryDirectory, "contract.yaml");
		File viewpointsFile = new File(temporaryDirectory, "viewpoints.yaml");
		Files.writeString(viewpointsFile.toPath(), "viewpoints: []");
		plan.setSchemaFile(schemaFile.getName());
		plan.setContractFile(contractFile.getName());
		plan.setViewpointsFile(viewpointsFile.getName());
		plan.setViewpointsFingerprint(
				new LegacyMigrationMappingValidator().fingerprint(viewpointsFile));
		File planFile = new File(temporaryDirectory, "relative-load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);

		assertTrue(exception.getMessage().contains("Target table was not found in schema"));
	}

	private void initialize(LegacyMigrationLoadPlan plan, File schemaFile,
			String schemaFingerprint) throws Exception {
		plan.setMigrationId("test-migration");
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
		field.setAction("COPY");
		field.setExtracted(true);
		dataSet.getFields().add(field);
		plan.getDataSets().add(dataSet);

		var contract = new com.sqlapp.data.schemas.migration.LegacyMigrationContract();
		contract.setMigrationId(plan.getMigrationId());
		var contractDataSet = new com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet();
		contractDataSet.setId(dataSet.getId());
		contractDataSet.setSourcePath("ROOT");
		contractDataSet.setFileName(dataSet.getFileName());
		contractDataSet.setTargetTable(dataSet.getTargetTable());
		contractDataSet.getSourceBusinessKey().add("ID");
		var contractField = new com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field();
		contractField.setPosition(1);
		contractField.setSourcePath("ROOT.ID");
		contractField.setStagingColumn("ID");
		contractField.setTargetColumn("ID");
		contractField.setTargetDataType("INT");
		contractField.setAction("COPY");
		contractField.setExtracted(true);
		contractDataSet.getFields().add(contractField);
		contract.getDataSets().add(contractDataSet);
		File contractFile = new File(temporaryDirectory, "contract.yaml");
		new LegacyMigrationContractIO().write(contractFile, contract);
		plan.setContractFile(contractFile.getPath());
		plan.setContractFingerprint(
				new LegacyMigrationMappingValidator().fingerprint(contractFile));
	}
}
