/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpoint;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpoints;
import com.sqlapp.data.db.command.viewpoint.SchemaViewpointsIO;
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
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
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
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		plan.getDataSets().getFirst().setFileName("different.csv");
		File planFile = new File(temporaryDirectory, "mismatched-load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);
		assertTrue(exception.getMessage().contains("disagrees with its contract"));
	}

	@Test
	void testRejectTamperedStagingTable() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"PUBLIC\"/>");
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		plan.getDataSets().getFirst().setStagingTable("UNRELATED_STAGE");
		File planFile = new File(temporaryDirectory, "tampered-staging-load-plan.yaml");
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
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		File contractFile = new File(temporaryDirectory, "contract.yaml");
		var contract = new LegacyMigrationContractIO().read(contractFile);
		var omitted = new com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet();
		omitted.setId("omitted");
		omitted.setSourcePath("OMITTED");
		omitted.setFileName("omitted.csv");
		omitted.setTargetTable("OMITTED");
		omitted.setLoadOrder(1);
		omitted.getSourceBusinessKey().add("ID");
		var field = new com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field();
		field.setPosition(1);
		field.setSourcePath("OMITTED.ID");
		field.setStagingColumn("ID");
		field.setTargetColumn("ID");
		field.setAction(LegacyMigrationMapping.ColumnAction.COPY);
		field.setExtracted(true);
		omitted.getFields().add(field);
		contract.getDataSets().add(omitted);
		new LegacyMigrationContractIO().write(contractFile, contract);
		plan.setContractFingerprint(new LegacyMigrationMappingValidator().fingerprint(contractFile));
		File planFile = new File(temporaryDirectory, "omitted-load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);

		assertTrue(exception.getMessage().contains("missing=[omitted]"));
		assertTrue(exception.getMessage().contains("unexpected=[]"));
	}

	@Test
	void testAllowViewpointToSelectContractSubset() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		addOmittedContractDataSet(plan);
		configureViewpoint(plan);
		plan.getResolvedDataSetIds().add("root");
		File planFile = new File(temporaryDirectory, "viewpoint-load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);

		assertTrue(exception.getMessage().contains("Target column was not found"), exception::getMessage);
	}

	@Test
	void testRejectViewpointResolvedDataSetsThatDisagreeWithPlan() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		addOmittedContractDataSet(plan);
		configureViewpoint(plan);
		plan.getResolvedDataSetIds().add("different");
		File planFile = new File(temporaryDirectory, "tampered-viewpoint-load-plan.yaml");

		CommandException exception = assertThrows(CommandException.class,
				() -> new LegacyMigrationLoadPlanIO().write(planFile, plan));

		assertTrue(exception.getMessage().contains("resolvedDataSetIds must match dataSets in order"));
	}

	@Test
	void testRejectViewpointResolvedTablesThatDisagreeWithSchemaSelection() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		configureViewpoint(plan);
		plan.getResolvedDataSetIds().add("root");
		plan.getResolvedTableIds().set(0, "PUBLIC.DIFFERENT");
		File planFile = new File(temporaryDirectory, "tampered-viewpoint-tables.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);

		assertTrue(
				exception.getMessage().contains("Viewpoint resolvedTableIds disagree with current schema selection"));
	}

	@Test
	void testRejectContractDataSetOutsideViewpointSelection() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		addOmittedContractDataSet(plan);
		addOmittedPlanDataSet(plan);
		configureViewpoint(plan);
		plan.getResolvedDataSetIds().add("root");
		plan.getResolvedDataSetIds().add("omitted");
		File planFile = new File(temporaryDirectory, "expanded-viewpoint-load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);

		assertTrue(exception.getMessage()
				.contains("Viewpoint data set selection disagrees with current schema selection"));
	}

	@Test
	void testRejectDuplicateStagingTables() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		addOmittedPlanDataSet(plan);
		plan.getDataSets().getLast().setStagingTable("stg_root");

		CommandException exception = assertThrows(CommandException.class, () -> new LegacyMigrationLoadPlanIO()
				.write(new File(temporaryDirectory, "duplicate-staging.yaml"), plan));

		assertTrue(exception.getMessage().contains("Duplicate staging table: stg_root"));
	}

	@Test
	void testRejectNonCanonicalLoadPlanOrder() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		addOmittedPlanDataSet(plan);
		Collections.swap(plan.getDataSets(), 0, 1);

		CommandException exception = assertThrows(CommandException.class, () -> new LegacyMigrationLoadPlanIO()
				.write(new File(temporaryDirectory, "unordered-load-plan.yaml"), plan));

		assertTrue(exception.getMessage().contains("ordered by loadOrder and id"));
	}

	@Test
	void testRejectReservedStagingColumns() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		plan.getDataSets().getFirst().getFields().getFirst().setStagingColumn("sqlapp_loaded_at");

		CommandException exception = assertThrows(CommandException.class, () -> new LegacyMigrationLoadPlanIO()
				.write(new File(temporaryDirectory, "reserved-staging-column.yaml"), plan));

		assertTrue(exception.getMessage().contains("uses a reserved staging column"));
	}

	@Test
	void testRejectDuplicateCsvFiles() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		addOmittedPlanDataSet(plan);
		plan.getDataSets().getLast().setFileName("ROOT.CSV");

		CommandException exception = assertThrows(CommandException.class,
				() -> new LegacyMigrationLoadPlanIO().write(new File(temporaryDirectory, "duplicate-csv.yaml"), plan));

		assertTrue(exception.getMessage().contains("Duplicate load CSV file name"));
	}

	@Test
	void testRejectDuplicateLoadKeysWithDifferentCase() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		plan.getDataSets().getFirst().getSourceBusinessKey().add("id");

		CommandException exception = assertThrows(CommandException.class,
				() -> LegacyMigrationLoadPlanIO.validate(plan));

		assertTrue(exception.getMessage().contains("sourceBusinessKey is invalid"));

		LegacyMigrationLoadPlan rootForeignKey = new LegacyMigrationLoadPlan();
		initialize(rootForeignKey, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		rootForeignKey.getDataSets().getFirst().getTargetForeignKey().add("ID");
		assertTrue(
				assertThrows(CommandException.class, () -> LegacyMigrationLoadPlanIO.validateExecution(rootForeignKey))
						.getMessage().contains("must not define targetForeignKey"));
	}

	@Test
	void testRejectUnsupportedAndInertLoadFields() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		var field = plan.getDataSets().getFirst().getFields().getFirst();
		field.setAction(null);
		assertThrows(CommandException.class, () -> LegacyMigrationLoadPlanIO.validate(plan));

		field.setAction(LegacyMigrationMapping.ColumnAction.COPY);
		field.setExtracted(false);
		field.setCsvPosition(0);
		CommandException inert = assertThrows(CommandException.class, () -> LegacyMigrationLoadPlanIO.validate(plan));
		assertTrue(inert.getMessage().contains("must be extracted or target-generated"));

		field.setExtracted(true);
		field.setCsvPosition(1);
		field.setTargetGenerated(true);
		CommandException conflicting = assertThrows(CommandException.class,
				() -> LegacyMigrationLoadPlanIO.validate(plan));
		assertTrue(conflicting.getMessage().contains("cannot be both extracted and target-generated"));
	}

	@Test
	void testResolveTargetTableByCatalogSchemaAndName() throws Exception {
		File schemaFile = new File(temporaryDirectory, "schema.xml");
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		plan.setTableOperationMode(LegacyMigrationLoadPlan.TableOperationMode.INSERT);
		plan.getDataSets().getFirst().setTargetCatalog("CATALOG_A");
		Table expected = table("CATALOG_A");
		Table other = table("CATALOG_B");

		assertDoesNotThrow(() -> LegacyMigrationLoadPlanIO.validateSchema(plan, java.util.List.of(other, expected)));

		CommandException exception = assertThrows(CommandException.class,
				() -> LegacyMigrationLoadPlanIO.validateSchema(plan, java.util.List.of(other)));
		assertTrue(exception.getMessage().contains("CATALOG_A.PUBLIC.ROOT"));
	}

	@Test
	void testRejectMissingTargetTableBeforeOpeningAConnection() throws Exception {
		File schemaFile = new File(temporaryDirectory, "empty-schema.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"PUBLIC\"/>");
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
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
		writeRootSchema(schemaFile);
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		initialize(plan, schemaFile, new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		File contractFile = new File(temporaryDirectory, "contract.yaml");
		configureViewpoint(plan);
		plan.getResolvedDataSetIds().add("root");
		File viewpointsFile = new File(plan.getViewpointsFile());
		plan.setSchemaFile(schemaFile.getName());
		plan.setContractFile(contractFile.getName());
		plan.setViewpointsFile(viewpointsFile.getName());
		File planFile = new File(temporaryDirectory, "relative-load-plan.yaml");
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand command = new LoadLegacyHierarchyCommand();
		command.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, command::run);

		assertTrue(exception.getMessage().contains("Target column was not found"));
	}

	private void initialize(LegacyMigrationLoadPlan plan, File schemaFile, String schemaFingerprint) throws Exception {
		plan.setMigrationId("test-migration");
		plan.setSchemaFile(schemaFile.getPath());
		plan.setSchemaFingerprint(schemaFingerprint);
		var dataSet = new LegacyMigrationLoadPlan.LoadDataSet();
		dataSet.setId("root");
		dataSet.setFileName("root.csv");
		dataSet.setStagingTable("STG_ROOT");
		dataSet.setTargetSchema("PUBLIC");
		dataSet.setTargetTable("ROOT");
		dataSet.getSourceBusinessKey().add("ID");
		var field = new LegacyMigrationLoadPlan.LoadField();
		field.setCsvPosition(1);
		field.setStagingColumn("ID");
		field.setTargetColumn("ID");
		field.setDataType("INT");
		field.setAction(LegacyMigrationMapping.ColumnAction.COPY);
		field.setExtracted(true);
		dataSet.getFields().add(field);
		plan.getDataSets().add(dataSet);

		var contract = new com.sqlapp.data.schemas.migration.LegacyMigrationContract();
		contract.setMigrationId(plan.getMigrationId());
		var contractDataSet = new com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet();
		contractDataSet.setId(dataSet.getId());
		contractDataSet.setSourcePath("ROOT");
		contractDataSet.setFileName(dataSet.getFileName());
		contractDataSet.setStagingTable(dataSet.getStagingTable());
		contractDataSet.setTargetSchema(dataSet.getTargetSchema());
		contractDataSet.setTargetTable(dataSet.getTargetTable());
		contractDataSet.getSourceBusinessKey().add("ID");
		var contractField = new com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field();
		contractField.setPosition(1);
		contractField.setSourcePath("ROOT.ID");
		contractField.setStagingColumn("ID");
		contractField.setTargetColumn("ID");
		contractField.setTargetDataType("INT");
		contractField.setAction(LegacyMigrationMapping.ColumnAction.COPY);
		contractField.setExtracted(true);
		contractDataSet.getFields().add(contractField);
		contract.getDataSets().add(contractDataSet);
		File contractFile = new File(temporaryDirectory, "contract.yaml");
		new LegacyMigrationContractIO().write(contractFile, contract);
		plan.setContractFile(contractFile.getPath());
		plan.setContractFingerprint(new LegacyMigrationMappingValidator().fingerprint(contractFile));
	}

	private void addOmittedContractDataSet(LegacyMigrationLoadPlan plan) {
		File contractFile = new File(plan.getContractFile());
		var contract = new LegacyMigrationContractIO().read(contractFile);
		var omitted = new com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet();
		omitted.setId("omitted");
		omitted.setSourcePath("OMITTED");
		omitted.setFileName("omitted.csv");
		omitted.setStagingTable("STG_OMITTED");
		omitted.setTargetSchema("PUBLIC");
		omitted.setTargetTable("OMITTED");
		omitted.setLoadOrder(1);
		omitted.getSourceBusinessKey().add("ID");
		var field = new com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field();
		field.setPosition(1);
		field.setSourcePath("OMITTED.ID");
		field.setStagingColumn("ID");
		field.setTargetColumn("ID");
		field.setTargetDataType("INT");
		field.setAction(LegacyMigrationMapping.ColumnAction.COPY);
		field.setExtracted(true);
		omitted.getFields().add(field);
		contract.getDataSets().add(omitted);
		new LegacyMigrationContractIO().write(contractFile, contract);
		plan.setContractFingerprint(new LegacyMigrationMappingValidator().fingerprint(contractFile));
	}

	private void addOmittedPlanDataSet(LegacyMigrationLoadPlan plan) {
		var dataSet = new LegacyMigrationLoadPlan.LoadDataSet();
		dataSet.setId("omitted");
		dataSet.setFileName("omitted.csv");
		dataSet.setStagingTable("STG_OMITTED");
		dataSet.setTargetSchema("PUBLIC");
		dataSet.setTargetTable("OMITTED");
		dataSet.setLoadOrder(1);
		dataSet.getSourceBusinessKey().add("ID");
		var field = new LegacyMigrationLoadPlan.LoadField();
		field.setCsvPosition(1);
		field.setStagingColumn("ID");
		field.setTargetColumn("ID");
		field.setDataType("INT");
		field.setAction(LegacyMigrationMapping.ColumnAction.COPY);
		field.setExtracted(true);
		dataSet.getFields().add(field);
		plan.getDataSets().add(dataSet);
	}

	private void configureViewpoint(LegacyMigrationLoadPlan plan) throws Exception {
		File viewpointsFile = new File(temporaryDirectory, "viewpoints.yaml");
		SchemaViewpoints viewpoints = new SchemaViewpoints();
		SchemaViewpoint viewpoint = new SchemaViewpoint();
		viewpoint.setId("root-only");
		viewpoint.getTables().add("PUBLIC.ROOT");
		viewpoints.getViewpoints().add(viewpoint);
		new SchemaViewpointsIO().write(viewpointsFile, viewpoints);
		plan.setViewpointId("root-only");
		plan.setViewpointsFile(viewpointsFile.getPath());
		plan.setViewpointsFingerprint(new LegacyMigrationMappingValidator().fingerprint(viewpointsFile));
		plan.getResolvedTableIds().add("PUBLIC.ROOT");
	}

	private void writeRootSchema(File schemaFile) throws Exception {
		Schema schema = new Schema("PUBLIC");
		schema.getTables().add(new Table("ROOT"));
		schema.writeXml(schemaFile);
	}

	private Table table(String catalog) {
		Table table = new Table("ROOT");
		table.setCatalogName(catalog);
		table.setSchemaName("PUBLIC");
		table.getColumns().add(new Column("ID"));
		return table;
	}
}
