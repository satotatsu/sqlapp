/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.YamlConverter;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.exceptions.CommandException;

class FirstNormalFormCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testNormalizeSchemaXmlWithCustomNames() throws XMLStreamException, IOException {
		Schema inputSchema = new Schema("PUBLIC");
		Table source = createSourceTable("ORDERS", true);
		source.getColumns().add(new Column("NOTE_1").setDataType(DataType.VARCHAR).setLength(100));
		source.getColumns().add(new Column("NOTE_2").setDataType(DataType.VARCHAR).setLength(100));
		source.getColumns().add(new Column("NOTE_3").setDataType(DataType.VARCHAR).setLength(100));
		source.getColumns().add(new Column("CODE_1").setDataType(DataType.INT));
		source.getColumns().add(new Column("CODE_2").setDataType(DataType.INT));
		source.getColumns().add(new Column("CODE_3").setDataType(DataType.INT));
		inputSchema.getTables().add(source);

		File inputDirectory = new File(temporaryDirectory, "input");
		File outputDirectory = new File(temporaryDirectory, "output");
		File logDirectory = new File(temporaryDirectory, "logs");
		assertTrue(inputDirectory.mkdirs());
		File inputFile = new File(inputDirectory, "schema.xml");
		inputSchema.writeXml(inputFile);

		FirstNormalFormCommand command = new FirstNormalFormCommand();
		command.setTargetFile(inputFile);
		command.setOutputDirectory(outputDirectory);
		command.setNormalizationLogDirectory(logDirectory);
		command.setNormalizationLogFileName("mapping.yaml");
		command.setChildKeyColumnNameStrategy(table -> "LINE_NO");
		command.setChildTableNameStrategy((table, number) -> table.getName() + "_LINES_" + number);
		command.run();

		File outputFile = new File(outputDirectory, inputFile.getName());
		assertTrue(outputFile.isFile());
		DbCommonObject<?> outputRoot = SchemaUtils.readXml(outputFile);
		Schema outputSchema = assertInstanceOf(Schema.class, outputRoot);
		Table normalizedSource = outputSchema.getTables().get("ORDERS");
		assertNotNull(normalizedSource);
		assertNotNull(normalizedSource.getColumns().get("ID"));
		assertNotNull(normalizedSource.getColumns().get("DESCRIPTION"));
		assertNull(normalizedSource.getColumns().get("DATE_1"));
		assertNull(normalizedSource.getColumns().get("ITEM_2"));
		assertNull(normalizedSource.getColumns().get("NOTE_3"));
		assertNull(normalizedSource.getColumns().get("CODE_3"));

		Table firstChild = outputSchema.getTables().get("ORDERS_LINES_1");
		assertNotNull(firstChild);
		assertEquals(4, firstChild.getColumns().size());
		assertColumn(firstChild, "ID", DataType.INT, true);
		assertColumn(firstChild, "LINE_NO", DataType.INT, true);
		assertColumn(firstChild, "DATE", DataType.DATE, false);
		assertColumn(firstChild, "ITEM", DataType.VARCHAR, false);
		assertPrimaryKey(firstChild, "ID", "LINE_NO");
		assertForeignKey(firstChild, normalizedSource, "ID");

		Table secondChild = outputSchema.getTables().get("ORDERS_LINES_2");
		assertNotNull(secondChild);
		assertEquals(4, secondChild.getColumns().size());
		assertColumn(secondChild, "NOTE", DataType.VARCHAR, false);
		assertColumn(secondChild, "CODE", DataType.INT, false);
		assertPrimaryKey(secondChild, "ID", "LINE_NO");
		assertForeignKey(secondChild, normalizedSource, "ID");

		File logFile = new File(logDirectory, "mapping.yaml");
		assertTrue(logFile.isFile());
		@SuppressWarnings("unchecked")
		Map<String, Object> log = new YamlConverter().fromJsonString(logFile, Map.class);
		assertEquals(1, log.get("formatVersion"));
		List<Map<String, Object>> tableLogs = (List<Map<String, Object>>) log.get("tables");
		Map<String, Object> tableLog = tableLogs.getFirst();
		assertEquals("ORDERS", ((Map<?, ?>) tableLog.get("sourceTable")).get("name"));
		List<Map<String, Object>> generatedTables = (List<Map<String, Object>>) tableLog.get("generatedTables");
		Map<String, Object> generatedTable = generatedTables.getFirst();
		assertEquals("ORDERS_LINES_1", generatedTable.get("name"));
		assertEquals("LINE_NO", ((Map<?, ?>) generatedTable.get("keyMapping")).get("sequenceColumn") instanceof Map<?, ?> sequence
				? sequence.get("name")
				: null);
		List<Map<String, Object>> columnMappings = (List<Map<String, Object>>) generatedTable.get("columnMappings");
		assertEquals("DATE", columnMappings.getFirst().get("targetColumn"));
		assertEquals("DATE_1",
				((List<Map<String, Object>>) columnMappings.getFirst().get("sourceColumns")).getFirst().get("column"));
		assertEquals(List.of("ORDERS_LINES_1.ID = ORDERS.ID"),
				((Map<?, ?>) generatedTable.get("migrationGuidance")).get("joinCondition"));
	}

	@Test
	void testSkipTableWithoutPrimaryKey() throws XMLStreamException, IOException {
		Schema schema = new Schema("PUBLIC");
		Table source = createSourceTable("NO_PRIMARY_KEY", false);
		schema.getTables().add(source);
		File inputFile = new File(temporaryDirectory, "without-pk.xml");
		File outputDirectory = new File(temporaryDirectory, "output-without-pk");
		schema.writeXml(inputFile);

		FirstNormalFormCommand command = new FirstNormalFormCommand();
		command.setTargetFile(inputFile);
		command.setOutputDirectory(outputDirectory);
		command.run();

		Schema output = SchemaUtils.readXml(new File(outputDirectory, inputFile.getName()));
		Table unchanged = output.getTables().get("NO_PRIMARY_KEY");
		assertNotNull(unchanged.getColumns().get("DATE_1"));
		assertNotNull(unchanged.getColumns().get("ITEM_2"));
		assertEquals(1, output.getTables().size());
	}

	@Test
	void testNormalizeSingleRepeatingColumnTypeWithConfiguredThreshold() throws XMLStreamException, IOException {
		Schema schema = new Schema("PUBLIC");
		Table source = new Table("CONTACTS");
		Column id = new Column("ID").setDataType(DataType.INT).setNotNull(true);
		source.getColumns().add(id);
		source.getColumns().add(new Column("PHONE_1").setDataType(DataType.VARCHAR).setLength(30));
		source.getColumns().add(new Column("PHONE_2").setDataType(DataType.VARCHAR).setLength(30));
		source.setPrimaryKey("PK_CONTACTS", id);
		schema.getTables().add(source);
		File inputFile = new File(temporaryDirectory, "single-type.xml");
		File outputDirectory = new File(temporaryDirectory, "output-single-type");
		schema.writeXml(inputFile);

		FirstNormalFormCommand command = new FirstNormalFormCommand();
		command.setTargetFile(inputFile);
		command.setOutputDirectory(outputDirectory);
		command.setMinimumColumnCount(1);
		command.run();

		Schema output = SchemaUtils.readXml(new File(outputDirectory, inputFile.getName()));
		Table normalizedSource = output.getTables().get("CONTACTS");
		assertNull(normalizedSource.getColumns().get("PHONE_1"));
		assertNull(normalizedSource.getColumns().get("PHONE_2"));
		Table child = output.getTables().get("CONTACTS_DETAIL_1");
		assertNotNull(child);
		assertColumn(child, "PHONE", DataType.VARCHAR, false);
	}

	@Test
	void testNormalizeThenConvertCompositePrimaryKeys() throws XMLStreamException, IOException {
		Schema schema = new Schema("PUBLIC");
		Table source = new Table("ORDERS");
		source.getColumns().add(new Column("TENANT_CODE").setDataType(DataType.VARCHAR).setLength(20));
		source.getColumns().add(new Column("ORDER_NO").setDataType(DataType.VARCHAR).setLength(20));
		source.getColumns().add(new Column("ITEM_1").setDataType(DataType.VARCHAR).setLength(100));
		source.getColumns().add(new Column("ITEM_2").setDataType(DataType.VARCHAR).setLength(100));
		source.setPrimaryKey("PK_ORDERS", source.getColumns().get("TENANT_CODE"),
				source.getColumns().get("ORDER_NO"));
		schema.getTables().add(source);
		File inputDirectory = new File(temporaryDirectory, "integrated-input");
		File outputDirectory = new File(temporaryDirectory, "integrated-output");
		assertTrue(inputDirectory.mkdirs());
		File inputFile = new File(inputDirectory, "schema.xml");
		schema.writeXml(inputFile);

		FirstNormalFormCommand command = new FirstNormalFormCommand();
		command.setTargetFile(inputFile);
		command.setOutputDirectory(outputDirectory);
		command.setMinimumColumnCount(1);
		command.setConvertCompositePrimaryKey(true);
		command.run();

		Schema output = (Schema) SchemaUtils.readXml(new File(outputDirectory, inputFile.getName()));
		Table parent = output.getTables().get("ORDERS");
		assertPrimaryKey(parent, "ID");
		assertNotNull(parent.getColumns().get("TENANT_CODE"));
		assertNotNull(parent.getColumns().get("ORDER_NO"));
		Table child = output.getTables().get("ORDERS_DETAIL_1");
		assertPrimaryKey(child, "ID");
		assertNotNull(child.getColumns().get("PARENT_ID"));
		assertNotNull(child.getColumns().get("ROW_NO"));
		assertNull(child.getColumns().get("TENANT_CODE"));
		assertNull(child.getColumns().get("ORDER_NO"));
	}

	@Test
	void testRejectIndexReferencingRepeatingColumn() throws XMLStreamException, IOException {
		Schema schema = new Schema("PUBLIC");
		Table source = createSourceTable("CONSTRAINED_TABLE", true);
		source.getIndexes().add("IDX_CONSTRAINED_DATE", source.getColumns().get("DATE_1"));
		schema.getTables().add(source);
		File inputFile = new File(temporaryDirectory, "constraint.xml");
		File outputDirectory = new File(temporaryDirectory, "output-constraint");
		schema.writeXml(inputFile);

		FirstNormalFormCommand command = new FirstNormalFormCommand();
		command.setTargetFile(inputFile);
		command.setOutputDirectory(outputDirectory);
		assertThrows(CommandException.class, command::run);
		assertFalse(new File(outputDirectory, inputFile.getName()).exists());
	}

	private Table createSourceTable(String name, boolean primaryKey) {
		Table table = new Table(name);
		Column id = new Column("ID").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(new Column("DESCRIPTION").setDataType(DataType.VARCHAR).setLength(200));
		table.getColumns().add(new Column("DATE_1").setDataType(DataType.DATE));
		table.getColumns().add(new Column("ITEM_1").setDataType(DataType.VARCHAR).setLength(50));
		table.getColumns().add(new Column("DATE_2").setDataType(DataType.DATE));
		table.getColumns().add(new Column("ITEM_2").setDataType(DataType.VARCHAR).setLength(50));
		if (primaryKey) {
			table.setPrimaryKey("PK_" + name, id);
		}
		return table;
	}

	private void assertColumn(Table table, String name, DataType dataType, boolean notNull) {
		Column column = table.getColumns().get(name);
		assertNotNull(column);
		assertEquals(dataType, column.getDataType());
		assertEquals(notNull, column.isNotNull());
	}

	private void assertPrimaryKey(Table table, String... columnNames) {
		UniqueConstraint primaryKey = table.getPrimaryKeyConstraint();
		assertNotNull(primaryKey);
		assertEquals(columnNames.length, primaryKey.getColumns().size());
		for (int i = 0; i < columnNames.length; i++) {
			assertEquals(columnNames[i], primaryKey.getColumns().get(i).getName());
		}
	}

	private void assertForeignKey(Table childTable, Table parentTable, String columnName) {
		assertEquals(1, childTable.getConstraints().getForeignKeyConstraints().size());
		ForeignKeyConstraint foreignKey = childTable.getConstraints().getForeignKeyConstraints().get(0);
		assertEquals(columnName, foreignKey.getColumns().get(0).getName());
		assertEquals(parentTable.getName(), foreignKey.getRelatedTableName());
		assertEquals(columnName, foreignKey.getRelatedColumns().get(0).getName());
	}
}
