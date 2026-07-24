/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.pli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class PliSchemaImportCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testImportNestedArraysAndRemarks() throws Exception {
		File source = new File(temporaryDirectory, "company.pli");
		Files.writeString(source.toPath(), """
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID        CHAR(4),
				      2 COMPANY_NAME      CHAR(40),
				      2 DEPARTMENT_GROUP(10),                   /* 階層2：最大10個の部署配列 */
				        3 DEPT_INFO,                           /* 階層3 */
				          4 DEPT_CODE     CHAR(5),             /* 階層4 */
				          4 DEPT_NAME     CHAR(20),            /* 階層4 */
				        3 EMP_COUNT       FIXED BIN(15),       /* 階層3 */
				        3 EMPLOYEE_LIST(50),                   /* 階層3：部署ごとに最大50人の社員配列 */
				          4 EMP_ID        CHAR(6),             /* 階層4 */
				          4 EMP_NAME,                          /* 階層4 */
				            5 LAST_NAME   CHAR(15),            /* 階層5：最も深い階層 */
				            5 FIRST_NAME  CHAR(15),            /* 階層5：最も深い階層 */
				          4 PAYROLL_INFO,                      /* 階層4 */
				            5 SALARY      FIXED DEC(9,2),      /* 階層5：最も深い階層 */
				            5 BONUS       FIXED DEC(9,2),      /* 階層5：最も深い階層 */
				          4 STATUS_FLAGS  BIT(8);              /* 階層4：在籍状態などのビット列 */
				""");
		File config = new File(temporaryDirectory, "company.yaml");
		Files.writeString(config.toPath(), """
				formatVersion: 1
				schemaName: COMPANY
				includeDeclarations: [COMPANY_MASTER]
				structures:
				  - name: COMPANY_MASTER
				    primaryKey: [COMPANY_ID]
				""");
		File output = new File(temporaryDirectory, "output");
		File logs = new File(temporaryDirectory, "logs");
		PliSchemaImportCommand command = new PliSchemaImportCommand();
		command.setTargetFile(source);
		command.setConfigurationFile(config);
		command.setOutputDirectory(output);
		command.setImportLogDirectory(logs);
		command.run();

		Schema schema = (Schema) SchemaUtils.readXml(new File(output, "company.xml"));
		assertEquals(3, schema.getTables().size());
		Table company = schema.getTables().get("COMPANY_MASTER");
		assertColumns(company, "COMPANY_ID", "COMPANY_NAME");
		assertPrimaryKey(company, "COMPANY_ID");

		Table department = schema.getTables().get("DEPARTMENT_GROUP");
		assertColumns(department, "COMPANY_ID", "DEPARTMENT_GROUP_NO", "DEPT_CODE", "DEPT_NAME", "EMP_COUNT");
		assertPrimaryKey(department, "COMPANY_ID", "DEPARTMENT_GROUP_NO");
		assertEquals("階層2：最大10個の部署配列", department.getRemarks());
		assertTrue(department.getColumns().get("DEPT_CODE").getRemarks().contains("階層4"));
		assertEquals("COMPANY_MASTER",
				department.getConstraints().getForeignKeyConstraints().getFirst().getRelatedTable().getName());

		Table employee = schema.getTables().get("EMPLOYEE_LIST");
		assertColumns(employee, "COMPANY_ID", "DEPARTMENT_GROUP_NO", "EMPLOYEE_LIST_NO", "EMP_ID",
				"LAST_NAME", "FIRST_NAME", "SALARY", "BONUS", "STATUS_FLAGS");
		assertPrimaryKey(employee, "COMPANY_ID", "DEPARTMENT_GROUP_NO", "EMPLOYEE_LIST_NO");
		assertColumn(employee, "SALARY", DataType.DECIMAL, 9L, 2);
		assertColumn(employee, "STATUS_FLAGS", DataType.BINARY, 1L, null);
		assertTrue(employee.getColumns().get("LAST_NAME").getRemarks().contains("階層5"));
		assertTrue(new File(logs, "company-pli-import.yaml").isFile());
	}

	private void assertColumns(Table table, String... names) {
		assertEquals(List.of(names), table.getColumns().stream().map(Column::getName).toList());
	}

	private void assertPrimaryKey(Table table, String... names) {
		assertEquals(List.of(names),
				table.getPrimaryKeyConstraint().getColumns().toColumns().stream().map(Column::getName).toList());
	}

	private void assertColumn(Table table, String name, DataType type, Long length, Integer scale) {
		Column column = table.getColumns().get(name);
		assertNotNull(column);
		assertEquals(type, column.getDataType());
		assertEquals(length, column.getLength());
		assertEquals(scale, column.getScale());
	}
}
