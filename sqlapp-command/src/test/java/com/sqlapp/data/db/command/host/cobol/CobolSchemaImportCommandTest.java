/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.legacy.GenerateLegacyMigrationContractCommand;
import com.sqlapp.data.db.command.migration.legacy.LegacyMigrationContractIO;
import com.sqlapp.data.db.command.migration.legacy.LegacyMigrationMappingIO;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;

class CobolSchemaImportCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void importsRecordsOccursTablesAndLayout() throws Exception {
		File output = importCustomer("""
				formatVersion: 1
				schemaName: HOST
				records:
				  - name: CUSTOMER-REC
				    tableName: CUSTOMER
				    primaryKey: [CUST-ID]
				""");

		Schema schema = (Schema) SchemaUtils.readXml(new File(output, "customer.xml"));
		assertEquals(2, schema.getTables().size());
		Table customer = schema.getTables().get("CUSTOMER");
		assertColumns(customer, "CUST_ID", "CUST_NAME", "CUST_STATUS", "BALANCE", "CREDIT_LIMIT", "BIRTH_YY",
				"BIRTH_MM", "BIRTH_DD", "POINTS", "ORDER_COUNT");
		assertColumn(customer, "CUST_ID", DataType.CHAR, 8L, null);
		assertColumn(customer, "CUST_NAME", DataType.NCHAR, 10L, null);
		assertColumn(customer, "BALANCE", DataType.DECIMAL, 9L, 2);
		assertColumn(customer, "CREDIT_LIMIT", DataType.INT, null, null);
		assertColumn(customer, "BIRTH_YY", DataType.DECIMAL, 4L, 0);
		assertColumn(customer, "ORDER_COUNT", DataType.SMALLINT, null, null);
		assertEquals("CUSTOMER NAME (KANJI)", customer.getColumns().get("CUST_NAME").getRemarks());
		assertEquals(List.of("CUST_ID"), primaryKey(customer));

		Table orders = schema.getTables().get("ORDERS");
		assertColumns(orders, "CUST_ID", "ORDERS_NO", "ORDER_NO", "ORDER_AMT");
		assertEquals(List.of("CUST_ID", "ORDERS_NO"), primaryKey(orders));
		assertColumn(orders, "ORDER_AMT", DataType.DECIMAL, 7L, 2);
		assertEquals("CUSTOMER",
				orders.getConstraints().getForeignKeyConstraints().getFirst().getRelatedTable().getName());

		File mappingFile = new File(temporaryDirectory, "output/customer-legacy-migration.yaml");
		LegacyMigrationMapping mapping = new LegacyMigrationMappingIO().read(mappingFile);
		assertEquals("COBOL", mapping.getSource().getSystem());
		assertEquals("CobolSchemaImportCommand", mapping.getTransformations().getFirst().getCommand());
		assertTrue(mapping.getDiagnostics().getWarnings().stream()
				.anyMatch(warning -> "COBOL_IMPORT_WARNING".equals(warning.getCode())
						&& warning.getMessage().contains("CUSTOMER-REC.BIRTH-DATE-N")));
		var customerMapping = table(mapping, "CUSTOMER");
		assertEquals(Map.of("recordLength", 106, "variableLength", true), customerMapping.getDetails().get("storage"));
		var balance = column(customerMapping, "BALANCE");
		assertEquals("CUSTOMER-REC.BALANCE", balance.getSourcePath());
		assertEquals("COBOL", balance.getConversion().get("language"));
		assertEquals("PIC S9(7)V99 COMP-3", balance.getConversion().get("declaration"));
		assertEquals(Map.of("offset", 29, "length", 5, "usage", "COMP-3", "digits", 9, "scale", 2, "signed", true),
				balance.getConversion().get("storage"));
		assertEquals("BALANCE", balance.getConversion().get("cobolName"));
		assertEquals(
				Map.of("offset", 48, "length", 6, "usage", "DISPLAY", "digits", 5, "scale", 0, "signed", true,
						"signPosition", "LEADING", "signSeparate", true),
				column(customerMapping, "POINTS").getConversion().get("storage"));
		assertEquals("CUST-STATUS", column(customerMapping, "CUST_STATUS").getConversion().get("cobolName"));
		assertEquals(
				List.of(Map.of("name", "ACTIVE", "values", List.of("'A'")),
						Map.of("name", "CLOSED", "values", List.of("'C'", "'X'"))),
				column(customerMapping, "CUST_STATUS").getConversion().get("conditions"));

		var ordersMapping = table(mapping, "ORDERS");
		assertEquals("CUSTOMER-REC.ORDERS", ordersMapping.getSource().getPath());
		assertEquals(Map.of("maximum", 5, "column", "ORDERS_NO", "minimum", 0, "offset", 56, "length", 50,
				"elementLength", 10, "dependingOn", "CUSTOMER-REC.ORDER-COUNT"),
				ordersMapping.getDetails().get("occurrence"));
		assertEquals(6, ((Map<?, ?>) column(ordersMapping, "ORDER_AMT").getConversion().get("storage")).get("offset"));
		assertEquals(1, mapping.getRelationships().size());

		File contractDirectory = new File(temporaryDirectory, "contract");
		GenerateLegacyMigrationContractCommand contract = new GenerateLegacyMigrationContractCommand();
		contract.setMappingFile(mappingFile);
		contract.setOutputDirectory(contractDirectory);
		contract.run();
		var generated = new LegacyMigrationContractIO().read(new File(contractDirectory, "customer-contract.yaml"));
		assertEquals(List.of("CUSTOMER", "ORDERS"),
				generated.getDataSets().stream().map(dataSet -> dataSet.getTargetTable()).toList());
	}

	@Test
	void selectsRedefinesAndRenamesColumns() throws Exception {
		File output = importCustomer("""
				formatVersion: 1
				schemaName: HOST
				redefines:
				  BIRTH-DATE: BIRTH-DATE-N
				columnNames:
				  CUSTOMER-REC.ORDERS.ORDER-NO: ORDER_ID
				generatedTableNames:
				  CUSTOMER-REC.ORDERS: CUSTOMER_ORDER
				occurrenceColumnNamePattern: ${groupName}_SEQ
				records:
				  - name: CUSTOMER-REC
				    primaryKey: [CUST_ID]
				""");
		Schema schema = (Schema) SchemaUtils.readXml(new File(output, "customer.xml"));
		Table customer = schema.getTables().get("CUSTOMER_REC");
		assertColumns(customer, "CUST_ID", "CUST_NAME", "CUST_STATUS", "BALANCE", "CREDIT_LIMIT", "BIRTH_DATE_N",
				"POINTS", "ORDER_COUNT");
		assertColumn(customer, "BIRTH_DATE_N", DataType.DECIMAL, 8L, 0);
		assertColumns(schema.getTables().get("CUSTOMER_ORDER"), "CUST_ID", "ORDERS_SEQ", "ORDER_ID", "ORDER_AMT");
	}

	@Test
	void rejectsConfigurationThatMatchesNothingAndDuplicateNames() throws Exception {
		for (String extra : List.of("redefines: {CUST-ID: X}", "redefines: {BIRTH-DATE: CUST-ID}",
				"columnNames: {CUSTOMER-REC.NOPE: X}", "generatedTableNames: {CUSTOMER-REC.NOPE: X}",
				"columnNames: {CUSTOMER-REC.POINTS: BALANCE}")) {
			assertThrows(RuntimeException.class, () -> importCustomer("""
					formatVersion: 1
					schemaName: HOST
					%s
					records:
					  - name: CUSTOMER-REC
					    primaryKey: [CUST_ID]
					""".formatted(extra)), extra);
		}
	}

	private File importCustomer(String configuration) throws Exception {
		File source = new File(temporaryDirectory, "customer.cpy");
		Files.writeString(source.toPath(), CobolCopybookParserTest.CUSTOMER);
		File config = new File(temporaryDirectory, "customer.yaml");
		Files.writeString(config.toPath(), configuration);
		File output = new File(temporaryDirectory, "output");
		CobolSchemaImportCommand command = new CobolSchemaImportCommand();
		command.setTargetFile(source);
		command.setConfigurationFile(config);
		command.setOutputDirectory(output);
		command.run();
		return output;
	}

	private static LegacyMigrationMapping.TableMapping table(LegacyMigrationMapping mapping, String name) {
		return mapping.getTables().stream().filter(item -> name.equals(item.getTarget().getTable())).findFirst()
				.orElseThrow();
	}

	private static LegacyMigrationMapping.ColumnMapping column(LegacyMigrationMapping.TableMapping table, String name) {
		return table.getColumns().stream().filter(item -> name.equals(item.getTarget())).findFirst().orElseThrow();
	}

	private static void assertColumns(Table table, String... names) {
		assertEquals(List.of(names), table.getColumns().stream().map(Column::getName).toList());
	}

	private static List<String> primaryKey(Table table) {
		return table.getPrimaryKeyConstraint().getColumns().toColumns().stream().map(Column::getName).toList();
	}

	private static void assertColumn(Table table, String name, DataType type, Long length, Integer scale) {
		Column column = table.getColumns().get(name);
		assertEquals(type, column.getDataType(), name);
		if (length != null) {
			assertEquals(length, column.getLength(), name);
		}
		if (scale != null) {
			assertEquals(scale, column.getScale(), name);
		}
	}
}
