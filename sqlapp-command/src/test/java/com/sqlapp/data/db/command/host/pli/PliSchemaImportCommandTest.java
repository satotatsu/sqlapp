/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.pli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.legacy.LegacyMigrationMappingIO;
import com.sqlapp.data.db.command.normalization.CompositePrimaryKeyToSurrogateKeyCommand;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;

class PliSchemaImportCommandTest {

	@TempDir
	File temporaryDirectory;

	private static final String COMPANY_CONFIG = """
			formatVersion: 1
			schemaName: COMPANY
			includeDeclarations: [COMPANY_MASTER]
			structures:
			  - name: COMPANY_MASTER
			    primaryKey: [COMPANY_ID]
			""";

	@Test
	void testImportNestedArraysAndRemarks() throws Exception {
		Result result = importPli("""
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
				""", COMPANY_CONFIG);
		Schema schema = result.schema;
		assertEquals(3, schema.getTables().size());
		Table company = schema.getTables().get("COMPANY_MASTER");
		assertColumns(company, "COMPANY_ID", "COMPANY_NAME");
		assertPrimaryKey(company, "COMPANY_ID");

		Table department = schema.getTables().get("DEPARTMENT_GROUP");
		assertColumns(department, "COMPANY_ID", "DEPARTMENT_GROUP_NO", "DEPT_CODE", "DEPT_NAME", "EMP_COUNT");
		assertPrimaryKey(department, "COMPANY_ID", "DEPARTMENT_GROUP_NO");
		assertEquals("階層2：最大10個の部署配列", department.getRemarks());
		assertEquals("階層3 / 階層4", department.getColumns().get("DEPT_CODE").getRemarks());
		assertEquals("COMPANY_MASTER",
				department.getConstraints().getForeignKeyConstraints().getFirst().getRelatedTable().getName());

		Table employee = schema.getTables().get("EMPLOYEE_LIST");
		assertColumns(employee, "COMPANY_ID", "DEPARTMENT_GROUP_NO", "EMPLOYEE_LIST_NO", "EMP_ID", "LAST_NAME",
				"FIRST_NAME", "SALARY", "BONUS", "STATUS_FLAGS");
		assertPrimaryKey(employee, "COMPANY_ID", "DEPARTMENT_GROUP_NO", "EMPLOYEE_LIST_NO");
		assertColumn(employee, "SALARY", DataType.DECIMAL, 9L, 2);
		assertColumn(employee, "STATUS_FLAGS", DataType.BINARY, 1L, null);
		assertTrue(employee.getColumns().get("LAST_NAME").getRemarks().contains("階層5"));

		LegacyMigrationMapping mapping = result.mapping();
		assertEquals("PL/I", mapping.getSource().getSystem());
		assertEquals("PliSchemaImportCommand", mapping.getTransformations().getFirst().getCommand());
		assertEquals(3, mapping.getTables().size());
		var employeeMapping = table(mapping, "EMPLOYEE_LIST");
		assertEquals("COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST", employeeMapping.getSource().getPath());
		assertEquals(50, number(employeeMapping.getDetails().get("occurrence"), "maximum"));
		assertTrue(employeeMapping.getColumns().stream()
				.anyMatch(column -> "EMP_ID".equals(column.getTarget())
						&& "COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST.EMP_ID".equals(column.getSourcePath())
						&& "CHAR(6)".equals(column.getConversion().get("declaration"))));
		assertTrue(String.valueOf(column(employeeMapping, "LAST_NAME").getConversion().get("remarks")).contains("階層5"));
		assertEquals(2, mapping.getRelationships().size());
		// FIXED BIN(15) is aligned by default, so offsets are not calculated.
		assertNull(column(employeeMapping, "EMP_ID").getConversion().get("storage"));
		assertTrue(mapping.getDiagnostics().getWarnings().stream().anyMatch(
				warning -> warning.getMessage().contains("EMP_COUNT") && warning.getMessage().contains("AGGREGATE")));

		File convertedDirectory = new File(temporaryDirectory, "converted");
		CompositePrimaryKeyToSurrogateKeyCommand surrogate = new CompositePrimaryKeyToSurrogateKeyCommand();
		surrogate.setTargetFile(new File(result.output, "company.xml"));
		surrogate.setOutputDirectory(convertedDirectory);
		surrogate.setMigrationMappingFile(result.mappingFile);
		surrogate.run();
		LegacyMigrationMapping accumulated = new LegacyMigrationMappingIO().read(result.mappingFile);
		assertEquals("PL/I", accumulated.getSource().getSystem());
		assertEquals(2, accumulated.getTransformations().size());
		assertTrue(table(accumulated, "EMPLOYEE_LIST").getColumns().stream()
				.anyMatch(column -> "EMP_ID".equals(column.getTarget()) && column.getSourcePath().endsWith(".EMP_ID")));
		assertTrue(accumulated.getRelationships().stream().anyMatch(relationship -> relationship.isParentIdPropagation()
				&& "PARENT_ID".equals(relationship.getTargetKeys().getFirst().getChildColumn())));
	}

	@Test
	void testScalarArrayBecomesValueColumn() throws Exception {
		Result result = importPli("""
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID  CHAR(4),
				      2 SCORES(5)   FIXED DEC(5,2),   /* score per quarter */
				      2 CODES       CHAR(2) DIM(0:9);
				""", COMPANY_CONFIG);
		Table scores = result.schema.getTables().get("SCORES");
		assertColumns(scores, "COMPANY_ID", "SCORES_NO", "SCORES");
		assertPrimaryKey(scores, "COMPANY_ID", "SCORES_NO");
		assertColumn(scores, "SCORES", DataType.DECIMAL, 5L, 2);
		assertEquals("score per quarter", scores.getRemarks());
		Table codes = result.schema.getTables().get("CODES");
		assertColumns(codes, "COMPANY_ID", "CODES_NO", "CODES");
		var codesMapping = table(result.mapping(), "CODES");
		assertEquals(10, number(codesMapping.getDetails().get("occurrence"), "maximum"));
		assertEquals(0, number(codesMapping.getDetails().get("occurrence"), "lowerBound"));
		assertEquals("CHAR(2) DIM(0:9)", column(codesMapping, "CODES").getConversion().get("declaration"));
	}

	@Test
	void testDataAttributes() throws Exception {
		Result result = importPli("""
				DCL 1 REC BASED(P) UNAL,
				      2 ID          CHAR(4) INIT('A,B;'),
				      2 NAME        CHAR(20) VARYING,
				      2 NOTE        CHAR(10) VARZ,
				      2 KANJI       GRAPHIC(5) VAR,
				      2 WIDE        WIDECHAR(3),
				      2 COUNT1      BIN FIXED(31),
				      2 COUNT2      FIXED BIN,
				      2 COUNT3      FIXED BIN(31) UNSIGNED,
				      2 COUNT4      FIXED BIN(63),
				      2 AMOUNT1     DEC FIXED(7,2),
				      2 AMOUNT2     FIXED(5),
				      2 AMOUNT3     FIXED DEC(5,-2),
				      2 AMOUNT4     FIXED DEC(2,4),
				      2 RATE1       FLOAT BIN(21),
				      2 RATE2       FLOAT DEC(16),
				      2 ZONED       PIC '(5)9V99',
				      2 SIGNED_NO   PICTURE 'S999',
				      2 EDITED      PIC 'ZZ,ZZ9.V99CR',
				      2 PIC_CODE    PIC 'XXA9',
				      2 *           CHAR(2),
				      2 ACTIVE      BIT(1),
				      2 FLAGS       BIT(12);
				""", """
				schemaName: S
				structures:
				  - name: rec
				    primaryKey: [id]
				""");
		Table table = result.schema.getTables().get("REC");
		assertColumns(table, "ID", "NAME", "NOTE", "KANJI", "WIDE", "COUNT1", "COUNT2", "COUNT3", "COUNT4", "AMOUNT1",
				"AMOUNT2", "AMOUNT3", "AMOUNT4", "RATE1", "RATE2", "ZONED", "SIGNED_NO", "EDITED", "PIC_CODE", "ACTIVE",
				"FLAGS");
		assertColumn(table, "ID", DataType.CHAR, 4L, null);
		assertColumn(table, "NAME", DataType.VARCHAR, 20L, null);
		assertColumn(table, "NOTE", DataType.VARCHAR, 10L, null);
		assertColumn(table, "KANJI", DataType.NVARCHAR, 5L, null);
		assertColumn(table, "WIDE", DataType.NCHAR, 3L, null);
		assertColumn(table, "COUNT1", DataType.INT, null, null);
		assertColumn(table, "COUNT2", DataType.SMALLINT, null, null);
		assertColumn(table, "COUNT3", DataType.INT, null, null);
		assertColumn(table, "COUNT4", DataType.BIGINT, null, null);
		assertColumn(table, "AMOUNT1", DataType.DECIMAL, 7L, 2);
		assertColumn(table, "AMOUNT2", DataType.DECIMAL, 5L, 0);
		assertColumn(table, "AMOUNT3", DataType.DECIMAL, 7L, 0);
		assertColumn(table, "AMOUNT4", DataType.DECIMAL, 4L, 4);
		assertColumn(table, "RATE1", DataType.REAL, null, null);
		assertColumn(table, "RATE2", DataType.DOUBLE, null, null);
		assertColumn(table, "ZONED", DataType.DECIMAL, 7L, 2);
		assertColumn(table, "SIGNED_NO", DataType.DECIMAL, 3L, 0);
		assertColumn(table, "EDITED", DataType.DECIMAL, 7L, 2);
		assertColumn(table, "PIC_CODE", DataType.CHAR, 4L, null);
		assertColumn(table, "ACTIVE", DataType.BOOLEAN, null, null);
		assertColumn(table, "FLAGS", DataType.BINARY, 2L, null);

		var mapping = table(result.mapping(), "REC");
		assertEquals("CHAR(20) VARYING", column(mapping, "NAME").getConversion().get("declaration"));
		assertEquals("PIC '(5)9V99'", column(mapping, "ZONED").getConversion().get("declaration"));
		// UNALIGNED structure: offsets are resolved, including filler and bit
		// storage.
		assertStorage(mapping, "ID", 0, 4);
		assertStorage(mapping, "NAME", 4, 22);
		assertStorage(mapping, "NOTE", 26, 11);
		assertStorage(mapping, "KANJI", 37, 12);
		assertStorage(mapping, "WIDE", 49, 6);
		assertStorage(mapping, "COUNT1", 55, 4);
		assertStorage(mapping, "COUNT2", 59, 2);
		assertStorage(mapping, "COUNT3", 61, 4);
		assertStorage(mapping, "COUNT4", 65, 8);
		assertStorage(mapping, "AMOUNT1", 73, 4);
		assertStorage(mapping, "AMOUNT2", 77, 3);
		assertStorage(mapping, "AMOUNT3", 80, 3);
		assertStorage(mapping, "AMOUNT4", 83, 2);
		assertStorage(mapping, "RATE1", 85, 4);
		assertStorage(mapping, "RATE2", 89, 8);
		assertStorage(mapping, "ZONED", 97, 7);
		assertStorage(mapping, "SIGNED_NO", 104, 4);
		assertStorage(mapping, "EDITED", 108, 11);
		assertStorage(mapping, "PIC_CODE", 119, 4);
		// 2 bytes of filler follow PIC_CODE.
		assertStorage(mapping, "ACTIVE", 125, 1);
		assertEquals(0, number(column(mapping, "ACTIVE").getConversion().get("storage"), "bitOffset"));
		assertEquals(1, number(column(mapping, "ACTIVE").getConversion().get("storage"), "bitLength"));
		assertStorage(mapping, "FLAGS", 125, 2);
		assertEquals(1, number(column(mapping, "FLAGS").getConversion().get("storage"), "bitOffset"));
		assertEquals(127, number(mapping.getDetails().get("storage"), "recordLength"));
	}

	@Test
	void testOffsetsInsideArrayElements() throws Exception {
		Result result = importPli("""
				DCL 1 COMPANY_MASTER UNALIGNED,
				      2 COMPANY_ID  CHAR(4),
				      2 DEPT(3),
				        3 DEPT_CODE CHAR(5),
				        3 HEADCOUNT FIXED BIN(15),
				        3 MEMBER(2),
				          4 MEMBER_ID PIC '99999',
				      2 TRAILER     CHAR(1);
				""", COMPANY_CONFIG);
		LegacyMigrationMapping mapping = result.mapping();
		assertStorage(table(mapping, "COMPANY_MASTER"), "COMPANY_ID", 0, 4);
		assertStorage(table(mapping, "COMPANY_MASTER"), "TRAILER", 4 + 3 * 17, 1);
		assertEquals(56, number(table(mapping, "COMPANY_MASTER").getDetails().get("storage"), "recordLength"));
		var dept = table(mapping, "DEPT");
		assertEquals(4, number(dept.getDetails().get("occurrence"), "offset"));
		assertEquals(17, number(dept.getDetails().get("occurrence"), "elementLength"));
		assertStorage(dept, "DEPT_CODE", 0, 5);
		assertStorage(dept, "HEADCOUNT", 5, 2);
		var member = table(mapping, "MEMBER");
		assertEquals(7, number(member.getDetails().get("occurrence"), "offset"));
		assertEquals(5, number(member.getDetails().get("occurrence"), "elementLength"));
		assertStorage(member, "MEMBER_ID", 0, 5);
	}

	@Test
	void testCommentPlacement() throws Exception {
		String source = """
				/* Copyright banner */

				/* Company master record */
				DCL 1 COMPANY_MASTER,
				  /* Company identifier */
				  2 COMPANY_ID /* inline */ CHAR(4),  /* trailing id */
				  2 COMPANY_NAME CHAR(40);  /* trailing name
				                               continues */
				""";
		Result auto = importPli(source, COMPANY_CONFIG);
		Table table = auto.schema.getTables().get("COMPANY_MASTER");
		assertEquals("Company master record", table.getRemarks());
		assertEquals("Company identifier inline trailing id", table.getColumns().get("COMPANY_ID").getRemarks());
		assertEquals("trailing name continues", table.getColumns().get("COMPANY_NAME").getRemarks());

		Result trailing = importPli(source, COMPANY_CONFIG + "commentPlacement: TRAILING\n");
		table = trailing.schema.getTables().get("COMPANY_MASTER");
		assertEquals("Copyright banner Company master record Company identifier", table.getRemarks());
		assertEquals("inline trailing id", table.getColumns().get("COMPANY_ID").getRemarks());

		Result leading = importPli(source, COMPANY_CONFIG + "commentPlacement: LEADING\n");
		table = leading.schema.getTables().get("COMPANY_MASTER");
		assertEquals("Copyright banner Company master record", table.getRemarks());
		assertEquals("Company identifier inline", table.getColumns().get("COMPANY_ID").getRemarks());
		assertEquals("trailing id", table.getColumns().get("COMPANY_NAME").getRemarks());
	}

	@Test
	void testMarginsAndSkippedStatements() throws Exception {
		String source = card(" %INCLUDE OTHER;", 100) + card(" DCL WORK CHAR(10);", 200)
				+ card(" DCL 1 COMPANY_MASTER,", 300) + card("       2 COMPANY_ID CHAR(4),", 400)
				+ card("       2 COMPANY_NAME CHAR(40);", 500) + card(" MAIN: PROC OPTIONS(MAIN);", 600)
				+ card(" END MAIN;", 700);
		Result result = importPli(source, COMPANY_CONFIG + "margins: [2, 72]\n");
		assertColumns(result.schema.getTables().get("COMPANY_MASTER"), "COMPANY_ID", "COMPANY_NAME");
		List<String> warnings = result.mapping().getDiagnostics().getWarnings().stream()
				.map(warning -> warning.getMessage()).toList();
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("%INCLUDE OTHER")), warnings::toString);
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("WORK CHAR(10)")), warnings::toString);
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("2 non-declaration")), warnings::toString);
	}

	@Test
	void testItemConfiguration() throws Exception {
		Result result = importPli("""
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID   CHAR(4),
				      2 HOME,
				        3 CODE       CHAR(2),
				      2 WORK,
				        3 CODE       CHAR(2),
				      2 NEXT_PTR     POINTER,
				      2 DEPT(10),
				        3 DEPT_NO    CHAR(3);
				""", """
				schemaName: COMPANY
				includeDeclarations: [company_master]
				structures:
				  - name: Company_Master
				    tableName: COMPANY
				    primaryKey: [company_id]
				excludeItems: [company_master.next_ptr]
				columnNames:
				  company_master.home.code: HOME_CODE
				  company_master.work.code: WORK_CODE
				generatedTableNames:
				  company_master.dept: COMPANY_DEPT
				occurrenceColumnNames:
				  company_master.dept: DEPT_SEQ
				""");
		assertColumns(result.schema.getTables().get("COMPANY"), "COMPANY_ID", "HOME_CODE", "WORK_CODE");
		assertColumns(result.schema.getTables().get("COMPANY_DEPT"), "COMPANY_ID", "DEPT_SEQ", "DEPT_NO");
		assertTrue(result.schema.getTables().get("COMPANY").getColumns().get("COMPANY_ID").isNotNull());
	}

	@Test
	void testUnsupportedItems() throws Exception {
		assertFails("NEXT_PTR", """
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID CHAR(4),
				      2 NEXT_PTR   POINTER;
				""", COMPANY_CONFIG);
		assertFails("multi-dimensional", """
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID CHAR(4),
				      2 GRID(3,4)  CHAR(1);
				""", COMPANY_CONFIG);
		assertFails("REFER", """
				DCL 1 COMPANY_MASTER BASED,
				      2 COMPANY_ID CHAR(4),
				      2 N          FIXED BIN(15),
				      2 ITEMS(N REFER(N)) CHAR(1);
				""", COMPANY_CONFIG);
		assertFails("neither data attributes nor members", """
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID CHAR(4),
				      2 EMPTY_GROUP;
				""", COMPANY_CONFIG);
		assertFails("Level-1 array declarations are not supported", """
				DCL 1 COMPANY_MASTER(10),
				      2 COMPANY_ID CHAR(4);
				""", COMPANY_CONFIG);
		assertFails("has data attributes but contains members", """
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID CHAR(4),
				        3 PART     CHAR(2);
				""", COMPANY_CONFIG);
		assertFails("Unterminated PL/I comment", """
				DCL 1 COMPANY_MASTER, /* open
				      2 COMPANY_ID CHAR(4);
				""", COMPANY_CONFIG);
	}

	@Test
	void testConflicts() throws Exception {
		String source = """
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID CHAR(4),
				      2 HOME,
				        3 CODE     CHAR(2),
				      2 WORK,
				        3 CODE     CHAR(2);
				""";
		assertFails("Rename it with columnNames", source, COMPANY_CONFIG);
		assertFails("Table name conflicts", """
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID CHAR(4),
				      2 A(2),
				        3 ITEM(2)  CHAR(1),
				      2 B(2),
				        3 ITEM(2)  CHAR(1);
				""", COMPANY_CONFIG);
		assertFails("Occurrence column name conflicts", """
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID CHAR(4),
				      2 ITEM(2),
				        3 CODE     CHAR(1),
				        3 ITEM_X(2),
				          4 VALUE  CHAR(1);
				""", COMPANY_CONFIG + """
				occurrenceColumnNames:
				  COMPANY_MASTER.ITEM.ITEM_X: ITEM_NO
				""");
	}

	@Test
	void testConfigurationValidation() throws Exception {
		String source = """
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID CHAR(4),
				      2 DEPT(2),
				        3 CODE     CHAR(1);
				DCL 1 OTHER,
				      2 X          CHAR(1);
				""";
		assertFails("unknown declaration", source, COMPANY_CONFIG + "excludeDeclarations: [MISSING]\n");
		assertFails("not found in the PL/I source", source, """
				schemaName: S
				structures:
				  - name: MISSING
				    primaryKey: [ID]
				""");
		assertFails("No structures entry", source, """
				schemaName: S
				includeDeclarations: [COMPANY_MASTER, OTHER]
				structures:
				  - name: COMPANY_MASTER
				    primaryKey: [COMPANY_ID]
				""");
		assertFails("unknown item path", source, COMPANY_CONFIG + "excludeItems: [COMPANY_MASTER.NOPE]\n");
		assertFails("must refer to an array item", source,
				COMPANY_CONFIG + "generatedTableNames:\n  COMPANY_MASTER.COMPANY_ID: T\n");
		assertFails("Unknown primary key column", source, """
				schemaName: S
				structures:
				  - name: COMPANY_MASTER
				    primaryKey: [NOPE]
				""");
		assertFails("formatVersion", source, COMPANY_CONFIG.replace("formatVersion: 1", "formatVersion: 2"));
		assertFails("margins", source, COMPANY_CONFIG + "margins: [10, 2]\n");

		// Without includeDeclarations, declarations without structures are skipped
		// with a warning.
		Result result = importPli(source, """
				schemaName: S
				structures:
				  - name: COMPANY_MASTER
				    primaryKey: [COMPANY_ID]
				""");
		assertEquals(2, result.schema.getTables().size());
		assertTrue(result.mapping().getDiagnostics().getWarnings().stream()
				.anyMatch(warning -> warning.getMessage().contains("No structure mapping was provided: OTHER")));
	}

	/**
	 * Formats an 80-column source line with a sequence number in columns 73-80.
	 */
	private static String card(String text, int sequence) {
		return String.format("%-72s%08d%n", text, sequence);
	}

	private record Result(Schema schema, File output, File mappingFile) {
		LegacyMigrationMapping mapping() {
			return new LegacyMigrationMappingIO().read(mappingFile);
		}
	}

	private int counter;

	private Result importPli(String source, String configuration) throws Exception {
		File directory = new File(temporaryDirectory, "run" + (++counter));
		directory.mkdirs();
		File sourceFile = new File(directory, "company.pli");
		Files.writeString(sourceFile.toPath(), source);
		File configFile = new File(directory, "company.yaml");
		Files.writeString(configFile.toPath(), configuration);
		File output = new File(directory, "output");
		File logs = new File(directory, "logs");
		PliSchemaImportCommand command = new PliSchemaImportCommand();
		command.setTargetFile(sourceFile);
		command.setConfigurationFile(configFile);
		command.setOutputDirectory(output);
		command.setMigrationMappingDirectory(logs);
		command.run();
		File mappingFile = new File(logs, "company-legacy-migration.yaml");
		assertTrue(mappingFile.isFile());
		return new Result((Schema) SchemaUtils.readXml(new File(output, "company.xml")), output, mappingFile);
	}

	private void assertFails(String expectedMessage, String source, String configuration) {
		Throwable thrown = assertThrows(Throwable.class, () -> importPli(source, configuration));
		StringBuilder messages = new StringBuilder();
		for (Throwable current = thrown; current != null; current = current.getCause()) {
			messages.append(current.getMessage()).append('\n');
		}
		assertTrue(messages.toString().contains(expectedMessage), messages::toString);
	}

	private static LegacyMigrationMapping.TableMapping table(LegacyMigrationMapping mapping, String name) {
		return mapping.getTables().stream().filter(item -> name.equals(item.getTarget().getTable())).findFirst()
				.orElseThrow();
	}

	private static LegacyMigrationMapping.ColumnMapping column(LegacyMigrationMapping.TableMapping table, String name) {
		return table.getColumns().stream().filter(column -> name.equals(column.getTarget())).findFirst().orElseThrow();
	}

	private static int number(Object map, String key) {
		Object value = ((Map<?, ?>) map).get(key);
		assertNotNull(value, () -> key + " is missing in " + map);
		return ((Number) value).intValue();
	}

	private static void assertStorage(LegacyMigrationMapping.TableMapping table, String column, int offset,
			int length) {
		Object storage = column(table, column).getConversion().get("storage");
		assertNotNull(storage, () -> "storage is missing: " + column);
		assertEquals(offset, number(storage, "offset"), () -> column + " offset: " + storage);
		assertEquals(length, number(storage, "length"), () -> column + " length: " + storage);
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
		assertNotNull(column, name);
		assertEquals(type, column.getDataType(), name);
		assertEquals(length, column.getLength(), name);
		assertEquals(scale, column.getScale(), name);
		assertFalse(column.getName().isBlank());
	}
}
