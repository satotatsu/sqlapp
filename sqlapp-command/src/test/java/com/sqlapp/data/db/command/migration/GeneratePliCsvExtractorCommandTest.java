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

import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.AncestorKey;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.KeyColumn;
import com.sqlapp.exceptions.CommandException;

class GeneratePliCsvExtractorCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testGenerateSpecificationAndPliTemplate() throws Exception {
		File contractFile = new File(temporaryDirectory, "company-contract.yaml");
		new LegacyMigrationContractIO().write(contractFile, contract());
		File output = new File(temporaryDirectory, "pli");
		GeneratePliCsvExtractorCommand command = new GeneratePliCsvExtractorCommand();
		command.setContractFile(contractFile);
		command.setOutputDirectory(output);
		command.setProgramName("cmpyext");
		command.run();

		File specification = new File(output, "CMPYEXT-extraction-spec.md");
		File template = new File(output, "CMPYEXT.pli.template");
		assertTrue(specification.isFile());
		assertTrue(template.isFile());
		String spec = Files.readString(specification.toPath());
		assertTrue(spec.contains("COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST.EMP_ID"));
		assertTrue(spec.contains("Propagate every listed ancestor key"));
		assertTrue(spec.contains("1..50"));
		assertTrue(spec.contains("EMP_ID"));
		String pli = Files.readString(template.toPath());
		assertTrue(pli.contains("CMPYEXT: PROCEDURE OPTIONS(MAIN);"));
		assertTrue(pli.contains("PROCESS-CHILDREN-TABLE-EMPLOYEE"));
		assertTrue(pli.contains("OCCURRENCE-INDEX-TABLE-EMPLOYEE"));
		assertTrue(pli.contains("CSV-WRITE-FIELD(FILE-TABLE-EMPLOYEE"));
		assertTrue(pli.contains("EMP_ID OF EMPLOYEE_LIST OF DEPARTMENT_GROUP OF COMPANY_MASTER"));
		assertTrue(pli.contains("double embedded quotes"));
		assertTrue(!new File(output, "CMPYEXT.pli.template.tmp").exists());
	}

	@Test
	void testRejectInvalidProgramName() {
		File contractFile = new File(temporaryDirectory, "company-contract.yaml");
		new LegacyMigrationContractIO().write(contractFile, contract());
		GeneratePliCsvExtractorCommand command = new GeneratePliCsvExtractorCommand();
		command.setContractFile(contractFile);
		command.setOutputDirectory(temporaryDirectory);
		command.setProgramName("TOO-LONG-NAME");
		assertThrows(CommandException.class, command::run);
	}

	private LegacyMigrationContract contract() {
		LegacyMigrationContract contract = new LegacyMigrationContract();
		contract.setMigrationId("company-migration");
		contract.setMappingFingerprint("sha256:sample");
		contract.getCsv().setEncoding("MS932");
		contract.getCsv().setNullValue("\\N");
		DataSet root = dataSet("table-company", "COMPANY_MASTER", "company_master.csv",
				"COMPANY_MASTER", null, 0);
		root.getFields().add(field(1, "COMPANY_MASTER.COMPANY_ID", "COMPANY_ID", false));
		contract.getDataSets().add(root);
		DataSet employee = dataSet("table-employee", "EMPLOYEE_LIST", "employee_list.csv",
				"COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST", root.getId(), 1);
		employee.setMaximumOccurrences(50);
		employee.setOccurrenceColumn("EMPLOYEE_LIST_NO");
		employee.getFields().add(field(1,
				"COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST.EMP_ID", "EMP_ID", false));
		employee.getFields().add(field(2,
				"COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST.$index", "EMPLOYEE_LIST_NO", true));
		AncestorKey ancestor = new AncestorKey();
		ancestor.setAncestorDataSetId(root.getId());
		ancestor.setAncestorTable("COMPANY_MASTER");
		ancestor.setDepth(1);
		ancestor.getColumns().add(new KeyColumn("COMPANY_ID", "COMPANY_ID", "PARENT_ID"));
		employee.getAncestorKeys().add(ancestor);
		contract.getDataSets().add(employee);
		return contract;
	}

	private DataSet dataSet(String id, String table, String fileName, String path, String parent, int depth) {
		DataSet dataSet = new DataSet();
		dataSet.setId(id);
		dataSet.setTargetSchema("COMPANY");
		dataSet.setTargetTable(table);
		dataSet.setStagingTable("TMP_" + table);
		dataSet.setFileName(fileName);
		dataSet.setSourcePath(path);
		dataSet.setParentDataSetId(parent);
		dataSet.setHierarchyDepth(depth);
		dataSet.setLoadOrder(depth);
		return dataSet;
	}

	private Field field(int position, String path, String column, boolean occurrence) {
		Field field = new Field();
		field.setPosition(position);
		field.setSourcePath(path);
		field.setSourceColumn(column);
		field.setStagingColumn(column);
		field.setTargetColumn(column);
		field.setAction(occurrence ? "GENERATE" : "COPY");
		field.setExtracted(true);
		field.setGenerated(occurrence);
		field.setOccurrenceIndex(occurrence);
		return field;
	}
}
