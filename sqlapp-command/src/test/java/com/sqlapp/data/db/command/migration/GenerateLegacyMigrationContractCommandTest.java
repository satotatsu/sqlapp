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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnAction;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnDefinition;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnPair;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.RelationshipMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableMapping;
import com.sqlapp.exceptions.CommandException;

class GenerateLegacyMigrationContractCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testGenerateCsvStagingAndHierarchyContract() {
		File mappingFile = new File(temporaryDirectory, "company-legacy-migration.yaml");
		LegacyMigrationMapping mapping = mapping();
		new LegacyMigrationMappingIO().write(mappingFile, mapping);
		File output = new File(temporaryDirectory, "contract");

		GenerateLegacyMigrationContractCommand command = new GenerateLegacyMigrationContractCommand();
		command.setMappingFile(mappingFile);
		command.setOutputDirectory(output);
		command.setEncoding("MS932");
		command.setNullValue("\\N");
		command.run();

		File contractFile = new File(output, "company-contract.yaml");
		assertTrue(contractFile.isFile());
		var contract = new LegacyMigrationContractIO().read(contractFile);
		assertEquals("sqlapp-legacy-migration-contract", contract.getFormat());
		assertEquals("MS932", contract.getCsv().getEncoding());
		assertEquals("\\N", contract.getCsv().getNullValue());
		assertEquals(2, contract.getDataSets().size());
		var child = contract.getDataSets().get(1);
		assertEquals("table-department", child.getParentDataSetId());
		assertEquals("employee_list.csv", child.getFileName());
		assertEquals("TMP_EMPLOYEE_LIST", child.getStagingTable());
		assertEquals(2, child.getHierarchyDepth());
		assertEquals(50, child.getMaximumOccurrences());
		assertTrue(child.getFields().stream().anyMatch(field -> "EMP_ID".equals(field.getTargetColumn())
				&& field.isExtracted() && field.getSourcePath().endsWith(".EMP_ID")));
		assertTrue(child.getFields().stream().anyMatch(field -> "ID".equals(field.getTargetColumn())
				&& field.isGenerated() && !field.isExtracted()));
		assertTrue(child.getFields().stream().anyMatch(field -> "COMPANY_ID".equals(field.getStagingColumn())
				&& "DROP".equals(field.getAction()) && field.isExtracted()));
		assertTrue(child.getFields().stream().anyMatch(field -> field.isOccurrenceIndex()
				&& field.isExtracted() && "EMPLOYEE_LIST_NO".equals(field.getTargetColumn())));
		assertEquals("table-department", child.getAncestorKeys().getFirst().getAncestorDataSetId());
		assertEquals("COMPANY_ID", child.getAncestorKeys().getFirst().getColumns().getFirst().getSourceColumn());
		assertFalse(new File(output, "company-contract.yaml.tmp").exists());
	}

	@Test
	void testRejectMissingMappingFile() {
		GenerateLegacyMigrationContractCommand command = new GenerateLegacyMigrationContractCommand();
		command.setMappingFile(new File(temporaryDirectory, "missing.yaml"));
		command.setOutputDirectory(temporaryDirectory);
		assertThrows(CommandException.class, command::run);
	}

	private LegacyMigrationMapping mapping() {
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		mapping.getMigration().setId("company-migration");
		TableMapping department = table("table-department", "DEPARTMENT_GROUP",
				"COMPANY_MASTER.DEPARTMENT_GROUP");
		department.getKeys().getTargetPrimaryKey().add("ID");
		department.getColumns().add(column("COMPANY_ID", "COMPANY_MASTER.COMPANY_ID", "COMPANY_ID",
				ColumnAction.COPY, "VARCHAR"));
		mapping.getTables().add(department);

		TableMapping employee = table("table-employee", "EMPLOYEE_LIST",
				"COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST");
		employee.getKeys().getTargetPrimaryKey().add("ID");
		employee.getKeys().getBusinessKey().add("EMP_ID");
		employee.getColumns().add(column("EMP_ID",
				"COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST.EMP_ID", "EMP_ID",
				ColumnAction.COPY, "VARCHAR"));
		employee.getColumns().add(column(null,
				"COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST.$index", "EMPLOYEE_LIST_NO",
				ColumnAction.GENERATE, "INT"));
		employee.getColumns().getLast().getConversion().put("type", "OCCURRENCE_NUMBER");
		employee.getColumns().add(column(null, null, "ID", ColumnAction.GENERATE, "INT"));
		employee.getColumns().add(column("COMPANY_ID",
				"COMPANY_MASTER.DEPARTMENT_GROUP.COMPANY_ID", null, ColumnAction.DROP, "VARCHAR"));
		employee.getDetails().put("occurrence",
				java.util.Map.of("column", "EMPLOYEE_LIST_NO", "maximum", 50));
		mapping.getTables().add(employee);

		RelationshipMapping relationship = new RelationshipMapping();
		relationship.setId("rel-department-employee");
		relationship.setParentMappingId(department.getId());
		relationship.setChildMappingId(employee.getId());
		relationship.setDepth(2);
		relationship.setLoadOrder(2);
		relationship.getSourceKeys().add(new ColumnPair("COMPANY_ID", "COMPANY_ID"));
		relationship.getTargetKeys().add(new ColumnPair("ID", "PARENT_ID"));
		mapping.getRelationships().add(relationship);
		return mapping;
	}

	private TableMapping table(String id, String name, String path) {
		TableMapping table = new TableMapping();
		table.setId(id);
		table.getSource().setPath(path);
		table.getTarget().setSchema("COMPANY");
		table.getTarget().setTable(name);
		return table;
	}

	private ColumnMapping column(String source, String sourcePath, String target, ColumnAction action,
			String dataType) {
		ColumnMapping column = new ColumnMapping();
		column.setSource(source);
		column.setSourcePath(sourcePath);
		column.setTarget(target);
		column.setAction(action);
		ColumnDefinition definition = new ColumnDefinition();
		definition.setDataType(dataType);
		column.setTargetDefinition(definition);
		return column;
	}
}
