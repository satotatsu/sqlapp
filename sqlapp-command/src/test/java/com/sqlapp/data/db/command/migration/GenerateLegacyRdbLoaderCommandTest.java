/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;

import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.AncestorKey;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.KeyColumn;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpoint;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpoints;
import com.sqlapp.data.db.command.viewpoint.SchemaViewpointsIO;
import com.sqlapp.exceptions.CommandException;

class GenerateLegacyRdbLoaderCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testGenerateRestartableRdbLoaderArtifacts() throws Exception {
		File contractFile = new File(temporaryDirectory, "company-contract.yaml");
		new LegacyMigrationContractIO().write(contractFile, contract());
		File schemaFile = new File(temporaryDirectory, "company.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"COMPANY\"/>");
		File output = new File(temporaryDirectory, "loader");
		GenerateLegacyRdbLoaderCommand command = new GenerateLegacyRdbLoaderCommand();
		command.setContractFile(contractFile);
		command.setSchemaFile(schemaFile);
		command.setOutputDirectory(output);
		command.setTableOperationMode("merge");
		command.setRootBatchSize(100);
		command.setCommitEveryRootBatches(200);
		command.setStagingTablePrefix("STG_");
		command.setGenerateRunnerTemplate(true);
		command.setRunnerClassName("CompanyLoader");
		command.run();

		File planFile = new File(output, "company-load-plan.yaml");
		assertTrue(planFile.isFile());
		var plan = new LegacyMigrationLoadPlanIO().read(planFile);
		assertEquals(LegacyMigrationLoadPlan.TableOperationMode.MERGE, plan.getTableOperationMode());
		assertEquals("STG_", plan.getStagingTablePrefix());
		assertEquals(100, plan.getRootBatchSize());
		assertEquals(200, plan.getCommitEveryRootBatches());
		assertEquals(LegacyMigrationLoadPlan.CommitUnit.ROOT_BATCH, plan.getTransaction().getCommitUnit());
		assertEquals(LegacyMigrationLoadPlan.RootCursorStrategy.DIALECT, plan.getRootCursorStrategy());
		final String planYaml = Files.readString(planFile.toPath());
		assertTrue(planYaml.contains("tableOperationMode: \"MERGE\"")
				|| planYaml.contains("tableOperationMode: MERGE"));
		assertTrue(planYaml.contains("rootCursorStrategy: \"DIALECT\"")
				|| planYaml.contains("rootCursorStrategy: DIALECT"));
		assertTrue(planYaml.contains("commitUnit: \"ROOT_BATCH\"")
				|| planYaml.contains("commitUnit: ROOT_BATCH"));
		assertTrue(planYaml.contains("stagingDeleteTiming: \"BEFORE_COMMIT\"")
				|| planYaml.contains("stagingDeleteTiming: BEFORE_COMMIT"));
		assertTrue(planYaml.contains("restartUnit: \"ROOT\"")
				|| planYaml.contains("restartUnit: ROOT"));
		final File unknownMode = new File(output, "unknown-mode.yaml");
		Files.writeString(unknownMode.toPath(), planYaml.replace("MERGE", "UNKNOWN_MODE"));
		assertThrows(CommandException.class, () -> new LegacyMigrationLoadPlanIO().read(unknownMode));
		final File unknownCursor = new File(output, "unknown-cursor.yaml");
		Files.writeString(unknownCursor.toPath(), planYaml.replace("DIALECT", "UNKNOWN_CURSOR"));
		assertThrows(CommandException.class, () -> new LegacyMigrationLoadPlanIO().read(unknownCursor));
		assertUnknownLoadPlanEnum(planYaml, "commitUnit", "ROOT_BATCH", "UNKNOWN_COMMIT_UNIT",
				"unknown-commit-unit.yaml");
		assertUnknownLoadPlanEnum(planYaml, "stagingDeleteTiming", "BEFORE_COMMIT", "UNKNOWN_DELETE_TIMING",
				"unknown-delete-timing.yaml");
		assertUnknownLoadPlanEnum(planYaml, "restartUnit", "ROOT", "UNKNOWN_RESTART_UNIT",
				"unknown-restart-unit.yaml");
		assertEquals(new File("..", contractFile.getName()).getPath(), plan.getContractFile());
		assertEquals(new File("..", schemaFile.getName()).getPath(), plan.getSchemaFile());
		assertTrue(plan.getTransaction().isTargetAndStagingDeleteAtomic());
		assertEquals("STG_COMPANY_MASTER", plan.getDataSets().getFirst().getStagingTable());
		assertEquals(java.util.List.of("PARENT_ID"),
				plan.getDataSets().getLast().getTargetForeignKey());
		assertTrue(plan.getDataSets().getLast().getFields().stream()
				.anyMatch(field -> field.isTargetGenerated() && "ID".equals(field.getTargetColumn())));

		String ddl = Files.readString(new File(output, "company-staging.sql").toPath());
		assertTrue(ddl.contains("CREATE TABLE STG_COMPANY_MASTER"));
		assertTrue(ddl.contains("SQLAPP_LOAD_STATUS VARCHAR(16) DEFAULT 'PENDING' NOT NULL"));
		assertTrue(ddl.contains("IX_STG_COMPANY_MASTER_PENDING"), ddl);
		assertTrue(ddl.contains("IX_STG_COMPANY_MASTER_KEY"), ddl);
		assertTrue(ddl.contains("IX_STG_EMPLOYEE_LIST_KEY"), ddl);
		assertFalse(ddl.contains(" ID INT"));
		String csv = Files.readString(new File(output, "company-csv-import.yaml").toPath());
		assertTrue(csv.contains("encoding: \"MS932\""));
		assertTrue(csv.contains("position: 3, name: \"EMPLOYEE_LIST_NO\""));
		String runner = Files.readString(new File(output, "CompanyLoader.java.template").toPath());
		assertTrue(runner.contains("new LoadLegacyHierarchyCommand()"));
		assertTrue(runner.contains("command.setDataSource(dataSource)"));
		assertTrue(runner.contains("command.setLoadPlanFile(loadPlanFile)"));
		assertFalse(new File(output, "company-staging.sql.tmp").exists());

		plan.getDataSets().getLast().setParentDataSetId("missing-parent");
		assertThrows(CommandException.class, () -> new LegacyMigrationLoadPlanIO().write(
				new File(output, "invalid-plan.yaml"), plan));
	}

	private void assertUnknownLoadPlanEnum(final String yaml, final String property, final String knownValue,
			final String unknownValue, final String fileName) throws Exception {
		final File file = new File(temporaryDirectory, fileName);
		final String quoted = property + ": \"" + knownValue + "\"";
		final String plain = property + ": " + knownValue;
		final String replacement = property + ": \"" + unknownValue + "\"";
		Files.writeString(file.toPath(), yaml.replace(quoted, replacement).replace(plain, replacement));
		assertThrows(CommandException.class, () -> new LegacyMigrationLoadPlanIO().read(file));
	}

	@Test
	void testRejectUnsafeBatchConfiguration() throws Exception {
		File contractFile = new File(temporaryDirectory, "company-contract.yaml");
		new LegacyMigrationContractIO().write(contractFile, contract());
		File schemaFile = new File(temporaryDirectory, "company.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"COMPANY\"/>");
		GenerateLegacyRdbLoaderCommand command = new GenerateLegacyRdbLoaderCommand();
		command.setContractFile(contractFile);
		command.setSchemaFile(schemaFile);
		command.setOutputDirectory(temporaryDirectory);
		command.setCommitEveryRootBatches(0);
		assertThrows(CommandException.class, command::run);
	}

	@Test
	void testRejectAncestorChainThatDoesNotStartWithParent() {
		var contract = contract();
		contract.getDataSets().getLast().getAncestorKeys().getFirst()
				.setAncestorDataSetId("different-parent");

		CommandException exception = assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(contract));

		assertTrue(exception.getMessage().contains("ancestor chain is invalid"));
	}

	@Test
	void testRejectFieldPositionAndStagingColumnAmbiguity() {
		var invalidPosition = contract();
		invalidPosition.getDataSets().getFirst().getFields().getFirst().setPosition(2);
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(invalidPosition))
				.getMessage().contains("Field positions must match"));

		var duplicateStaging = contract();
		var duplicate = field(2, "COMPANY_MASTER.OTHER", "COMPANY_ID", "OTHER",
				"VARCHAR", 4L, false, false);
		duplicateStaging.getDataSets().getFirst().getFields().add(duplicate);
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(duplicateStaging))
				.getMessage().contains("staging columns must be unique"));
	}

	@Test
	void testRejectInvalidCsvAndOccurrenceConfiguration() {
		var invalidCsv = contract();
		invalidCsv.getCsv().setDelimiter("||");
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(invalidCsv))
				.getMessage().contains("CSV format is invalid"));

		var invalidOccurrence = contract();
		invalidOccurrence.getDataSets().getLast().setMaximumOccurrences(0);
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(invalidOccurrence))
				.getMessage().contains("occurrence configuration is invalid"));
	}

	@Test
	void testRejectContradictoryFieldSemantics() {
		var generatedFlagMismatch = contract();
		generatedFlagMismatch.getDataSets().getFirst().getFields().getFirst()
				.setGenerated(true);
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(generatedFlagMismatch))
				.getMessage().contains("action and generated flag disagree"));

		var missingTarget = contract();
		missingTarget.getDataSets().getFirst().getFields().getFirst().setTargetColumn(null);
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(missingTarget))
				.getMessage().contains("requires targetColumn"));
	}

	@Test
	void testRejectKeysThatReferenceUnknownFields() {
		var missingRootBusinessKey = contract();
		missingRootBusinessKey.getDataSets().getFirst().getSourceBusinessKey().clear();
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(missingRootBusinessKey))
				.getMessage().contains("requires sourceBusinessKey for restart"));

		var duplicateKeyWithDifferentCase = contract();
		duplicateKeyWithDifferentCase.getDataSets().getFirst().getSourceBusinessKey()
				.add("company_id");
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(duplicateKeyWithDifferentCase))
				.getMessage().contains("keys are invalid"));

		var invalidBusinessKey = contract();
		invalidBusinessKey.getDataSets().getFirst().getSourceBusinessKey()
				.set(0, "UNKNOWN_SOURCE");
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(invalidBusinessKey))
				.getMessage().contains("business key does not reference"));

		var invalidAncestorKey = contract();
		invalidAncestorKey.getDataSets().getLast().getAncestorKeys().getFirst()
				.getColumns().getFirst().setSourceColumn("UNKNOWN_CHILD_COLUMN");
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(invalidAncestorKey))
				.getMessage().contains("Ancestor key references an unknown field"));
	}

	@Test
	void testRejectNonCanonicalDataSetOrderAndInvalidDepth() {
		var wrongOrder = contract();
		Collections.swap(wrongOrder.getDataSets(), 0, 1);
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(wrongOrder))
				.getMessage().contains("ordered by loadOrder and id"));

		var invalidDepth = contract();
		invalidDepth.getDataSets().getLast().setHierarchyDepth(0);
		assertTrue(assertThrows(CommandException.class,
				() -> new LegacyMigrationContractValidator().validate(invalidDepth))
				.getMessage().contains("hierarchy is inconsistent"));
	}

	@Test
	void testRejectStagingTableCollisionBeforeWritingArtifacts() throws Exception {
		var contract = contract();
		contract.getDataSets().getLast().setTargetTable("COMPANY_MASTER");
		File contractFile = new File(temporaryDirectory, "collision-contract.yaml");
		new LegacyMigrationContractIO().write(contractFile, contract);
		File schemaFile = new File(temporaryDirectory, "company.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"COMPANY\"/>");
		File output = new File(temporaryDirectory, "collision-loader");
		GenerateLegacyRdbLoaderCommand command = new GenerateLegacyRdbLoaderCommand();
		command.setContractFile(contractFile);
		command.setSchemaFile(schemaFile);
		command.setOutputDirectory(output);
		command.setStagingTablePrefix("TMP_");

		CommandException exception = assertThrows(CommandException.class, command::run);

		assertTrue(exception.getMessage().contains("Duplicate staging table"));
		assertFalse(new File(output, "collision-load-plan.yaml").exists());
		assertFalse(new File(output, "collision-staging.sql").exists());
	}

	@Test
	void testUsesContractStagingNamesAndCarriesTargetCatalogByDefault() throws Exception {
		var contract = contract();
		contract.getDataSets().getFirst().setStagingTable("LOAD_COMPANY");
		contract.getDataSets().getLast().setStagingTable("LOAD_EMPLOYEE");
		contract.getDataSets().forEach(dataSet -> dataSet.setTargetCatalog("APPLICATION"));
		File contractFile = new File(temporaryDirectory, "catalog-contract.yaml");
		new LegacyMigrationContractIO().write(contractFile, contract);
		File schemaFile = new File(temporaryDirectory, "company.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"COMPANY\"/>");
		File output = new File(temporaryDirectory, "catalog-loader");
		GenerateLegacyRdbLoaderCommand command = new GenerateLegacyRdbLoaderCommand();
		command.setContractFile(contractFile);
		command.setSchemaFile(schemaFile);
		command.setOutputDirectory(output);

		command.run();

		var plan = new LegacyMigrationLoadPlanIO().read(
				new File(output, "catalog-load-plan.yaml"));
		assertEquals("LOAD_COMPANY", plan.getDataSets().getFirst().getStagingTable());
		assertEquals(null, plan.getStagingTablePrefix());
		assertEquals("LOAD_EMPLOYEE", plan.getDataSets().getLast().getStagingTable());
		assertEquals("APPLICATION", plan.getDataSets().getFirst().getTargetCatalog());
		String ddl = Files.readString(new File(output, "catalog-staging.sql").toPath());
		assertTrue(ddl.contains("CREATE TABLE LOAD_COMPANY"));
	}

	@Test
	void testRejectInvalidLoadPlanBeforeExecution() throws Exception {
		File file = new File(temporaryDirectory, "invalid-load-plan.yaml");
		Files.writeString(file.toPath(), "format: wrong\nversion: 1\n");

		assertThrows(CommandException.class, () -> new LegacyMigrationLoadPlanIO().read(file));
		assertThrows(CommandException.class, () -> new LegacyMigrationLoadPlanIO()
				.read(new File(temporaryDirectory, "missing.yaml")));
	}

	@Test
	void testRejectLoadPlanJoinThatDisagreesWithContract() throws Exception {
		File contractFile = new File(temporaryDirectory, "company-contract.yaml");
		new LegacyMigrationContractIO().write(contractFile, contract());
		File schemaFile = new File(temporaryDirectory, "company.xml");
		Files.writeString(schemaFile.toPath(), "<schema name=\"COMPANY\"/>");
		File output = new File(temporaryDirectory, "loader");
		GenerateLegacyRdbLoaderCommand generator = new GenerateLegacyRdbLoaderCommand();
		generator.setContractFile(contractFile);
		generator.setSchemaFile(schemaFile);
		generator.setOutputDirectory(output);
		generator.run();
		File planFile = new File(output, "company-load-plan.yaml");
		var plan = new LegacyMigrationLoadPlanIO().read(planFile);
		plan.getDataSets().getLast().setTargetForeignKey(java.util.List.of("ID"));
		new LegacyMigrationLoadPlanIO().write(planFile, plan);
		LoadLegacyHierarchyCommand loader = new LoadLegacyHierarchyCommand();
		loader.setLoadPlanFile(planFile);

		CommandException exception = assertThrows(CommandException.class, loader::run);

		assertTrue(exception.getMessage()
				.contains("data set disagrees with its contract: table-employee"));
	}

	@Test
	void testViewpointGroupSelectionIncludesAncestorAndIsRecorded() throws Exception {
		File contractFile = new File(temporaryDirectory, "company-contract.yaml");
		new LegacyMigrationContractIO().write(contractFile, contract());
		File schemaFile = new File(temporaryDirectory, "company.xml");
		Schema schema = new Schema("COMPANY");
		schema.getTables().add(new Table("COMPANY_MASTER"));
		schema.getTables().add(new Table("EMPLOYEE_LIST"));
		schema.writeXml(schemaFile);
		File viewpointsFile = new File(temporaryDirectory, "viewpoints.yaml");
		SchemaViewpoints viewpoints = new SchemaViewpoints();
		SchemaViewpoint viewpoint = new SchemaViewpoint();
		viewpoint.setId("company-load");
		viewpoint.getTables().add("COMPANY.EMPLOYEE_LIST");
		viewpoints.getViewpoints().add(viewpoint);
		new SchemaViewpointsIO().write(viewpointsFile, viewpoints);

		File output = new File(temporaryDirectory, "selected-loader");
		GenerateLegacyRdbLoaderCommand command = new GenerateLegacyRdbLoaderCommand();
		command.setContractFile(contractFile);
		command.setSchemaFile(schemaFile);
		command.setOutputDirectory(output);
		command.setViewpointsFile(viewpointsFile);
		command.setViewpointId("company-load");
		command.run();

		var plan = new LegacyMigrationLoadPlanIO().read(
				new File(output, "company-load-plan.yaml"));
		assertEquals("company-load", plan.getViewpointId());
		assertEquals(java.util.List.of("table-company", "table-employee"),
				plan.getResolvedDataSetIds());
		assertEquals(2, plan.getDataSets().size());
		assertFalse(plan.getViewpointsFingerprint().isBlank());
		assertEquals(new File("..", viewpointsFile.getName()).getPath(),
				plan.getViewpointsFile());
	}

	private LegacyMigrationContract contract() {
		LegacyMigrationContract contract = new LegacyMigrationContract();
		contract.setMigrationId("company-migration");
		contract.getCsv().setEncoding("MS932");
		DataSet company = dataSet("table-company", "COMPANY_MASTER", "company_master.csv",
				"COMPANY_MASTER", null, 0);
		company.getSourceBusinessKey().add("COMPANY_ID");
		company.getFields().add(field(1, "COMPANY_MASTER.COMPANY_ID", "COMPANY_ID",
				"COMPANY_ID", "VARCHAR", 4L, false, false));
		contract.getDataSets().add(company);
		DataSet employee = dataSet("table-employee", "EMPLOYEE_LIST", "employee_list.csv",
				"COMPANY_MASTER.EMPLOYEE_LIST", company.getId(), 1);
		employee.setMaximumOccurrences(50);
		employee.setOccurrenceColumn("EMPLOYEE_LIST_NO");
		employee.getSourceBusinessKey().add("COMPANY_ID");
		employee.getSourceBusinessKey().add("EMP_ID");
		employee.getFields().add(field(1, "COMPANY_MASTER.EMPLOYEE_LIST.COMPANY_ID",
				"COMPANY_ID", null, "VARCHAR", 4L, false, false));
		employee.getFields().getLast().setAction(LegacyMigrationMapping.ColumnAction.DROP);
		employee.getFields().add(field(2, "COMPANY_MASTER.EMPLOYEE_LIST.EMP_ID",
				"EMP_ID", "EMP_ID", "VARCHAR", 6L, false, false));
		employee.getFields().add(field(3, "COMPANY_MASTER.EMPLOYEE_LIST.$index",
				"EMPLOYEE_LIST_NO", "EMPLOYEE_LIST_NO", "INT", null, true, false));
		employee.getFields().add(field(4, null, "ID", "ID", "INT", null, false, true));
		AncestorKey ancestor = new AncestorKey();
		ancestor.setAncestorDataSetId(company.getId());
		ancestor.setAncestorTable("COMPANY_MASTER");
		ancestor.setDepth(1);
		ancestor.getTargetForeignKey().add("PARENT_ID");
		ancestor.getColumns().add(new KeyColumn("COMPANY_ID", "COMPANY_ID"));
		employee.getAncestorKeys().add(ancestor);
		contract.getDataSets().add(employee);
		return contract;
	}

	private DataSet dataSet(String id, String table, String file, String path, String parent, int depth) {
		DataSet dataSet = new DataSet();
		dataSet.setId(id);
		dataSet.setTargetSchema("COMPANY");
		dataSet.setTargetTable(table);
		dataSet.setStagingTable("TMP_" + table);
		dataSet.setFileName(file);
		dataSet.setSourcePath(path);
		dataSet.setParentDataSetId(parent);
		dataSet.setHierarchyDepth(depth);
		dataSet.setLoadOrder(depth);
		return dataSet;
	}

	private Field field(int position, String path, String staging, String target, String type,
			Long length, boolean occurrence, boolean generated) {
		Field field = new Field();
		field.setPosition(position);
		field.setSourcePath(path);
		field.setSourceColumn(staging);
		field.setStagingColumn(staging);
		field.setTargetColumn(target);
		field.setTargetDataType(type);
		field.setLength(length);
		field.setAction(generated || occurrence ? LegacyMigrationMapping.ColumnAction.GENERATE
				: LegacyMigrationMapping.ColumnAction.COPY);
		field.setExtracted(path != null);
		field.setGenerated(generated || occurrence);
		field.setOccurrenceIndex(occurrence);
		return field;
	}
}
