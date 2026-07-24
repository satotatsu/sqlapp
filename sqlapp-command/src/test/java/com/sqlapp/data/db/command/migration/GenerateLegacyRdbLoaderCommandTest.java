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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.AncestorKey;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.KeyColumn;
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
		command.setRunnerClassName("CompanyLoader");
		command.run();

		File planFile = new File(output, "company-load-plan.yaml");
		assertTrue(planFile.isFile());
		var plan = new LegacyMigrationLoadPlanIO().read(planFile);
		assertEquals("MERGE", plan.getTableOperationMode());
		assertEquals(100, plan.getRootBatchSize());
		assertEquals(200, plan.getCommitEveryRootBatches());
		assertEquals("ROOT_BATCH", plan.getTransaction().getCommitUnit());
		assertTrue(plan.getTransaction().isTargetAndStagingDeleteAtomic());
		assertEquals("STG_COMPANY_MASTER", plan.getDataSets().getFirst().getStagingTable());
		assertEquals("PARENT_ID",
				plan.getDataSets().getLast().getParentJoinKeys().getFirst().getTargetForeignKeyColumn());
		assertTrue(plan.getDataSets().getLast().getFields().stream()
				.anyMatch(field -> field.isTargetGenerated() && "ID".equals(field.getTargetColumn())));

		String ddl = Files.readString(new File(output, "company-staging.sql").toPath());
		assertTrue(ddl.contains("CREATE TABLE STG_COMPANY_MASTER"));
		assertTrue(ddl.contains("SQLAPP_LOAD_STATUS VARCHAR(16) DEFAULT 'PENDING' NOT NULL"));
		assertTrue(ddl.contains("CREATE INDEX IX_STG_EMPLOYEE_LIST_KEY"));
		assertFalse(ddl.contains(" ID INT"));
		String csv = Files.readString(new File(output, "company-csv-import.yaml").toPath());
		assertTrue(csv.contains("encoding: \"MS932\""));
		assertTrue(csv.contains("position: 2, name: \"EMPLOYEE_LIST_NO\""));
		String runner = Files.readString(new File(output, "CompanyLoader.java.template").toPath());
		assertTrue(runner.contains("TableOperationMode.MERGE"));
		assertTrue(runner.contains(
				"session.setCommitEveryRootBatches(COMMIT_EVERY_ROOT_BATCHES)"));
		assertTrue(runner.contains("session.setBeforeCommitEveryRootBatchesHandler"));
		assertTrue(runner.contains("deleteStagingHierarchy(connection, committedRoots)"));
		assertTrue(runner.contains("connection.rollback()"));
		assertFalse(new File(output, "company-staging.sql.tmp").exists());
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
		employee.getSourceBusinessKey().add("COMPANY_ID");
		employee.getSourceBusinessKey().add("EMP_ID");
		employee.getFields().add(field(1, "COMPANY_MASTER.EMPLOYEE_LIST.COMPANY_ID",
				"COMPANY_ID", null, "VARCHAR", 4L, false, false));
		employee.getFields().getLast().setAction("DROP");
		employee.getFields().add(field(2, "COMPANY_MASTER.EMPLOYEE_LIST.$index",
				"EMPLOYEE_LIST_NO", "EMPLOYEE_LIST_NO", "INT", null, true, false));
		employee.getFields().add(field(0, null, "ID", "ID", "INT", null, false, true));
		AncestorKey ancestor = new AncestorKey();
		ancestor.setAncestorDataSetId(company.getId());
		ancestor.setAncestorTable("COMPANY_MASTER");
		ancestor.getColumns().add(new KeyColumn("COMPANY_ID", "COMPANY_ID", "PARENT_ID"));
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
		field.setAction(generated || occurrence ? "GENERATE" : "COPY");
		field.setExtracted(path != null);
		field.setGenerated(generated || occurrence);
		field.setOccurrenceIndex(occurrence);
		return field;
	}
}
