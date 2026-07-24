/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;

class CompositePrimaryKeyToSurrogateKeyCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testConvertHierarchyWithIdentity() throws XMLStreamException, IOException {
		Schema schema = createHierarchy();
		File input = writeInput(schema, "schema.xml");
		File output = new File(temporaryDirectory, "output");
		File logs = new File(temporaryDirectory, "logs");

		CompositePrimaryKeyToSurrogateKeyCommand command = new CompositePrimaryKeyToSurrogateKeyCommand();
		command.setTargetFile(input);
		command.setOutputDirectory(output);
		command.setConversionLogDirectory(logs);
		command.setForeignKeyColumnNameStrategy((table, columns) -> "PARENT_ID");
		command.run();

		Schema converted = (Schema) SchemaUtils.readXml(new File(output, input.getName()));
		Table tab = converted.getTables().get("TAB");
		assertPrimaryKey(tab, "ID");
		assertTrue(tab.getColumns().get("ID").isIdentity());
		assertUnique(tab, "PK_COL1", "PK_COL2");

		Table tab1 = converted.getTables().get("TAB_1");
		assertPrimaryKey(tab1, "ID");
		assertNull(tab1.getColumns().get("PK_COL1"));
		assertNull(tab1.getColumns().get("PK_COL2"));
		assertNotNull(tab1.getColumns().get("PK_COL3"));
		assertNotNull(tab1.getColumns().get("PARENT_ID"));
		assertUnique(tab1, "PARENT_ID", "PK_COL3");
		assertEquals("TAB", tab1.getConstraints().getForeignKeyConstraints().getFirst().getRelatedTable().getName());
		assertEquals("ID", tab1.getConstraints().getForeignKeyConstraints().getFirst().getRelatedColumns()
				.getFirst().getName());

		Table tab11 = converted.getTables().get("TAB_1_1");
		assertPrimaryKey(tab11, "ID");
		assertNull(tab11.getColumns().get("PK_COL1A"));
		assertNull(tab11.getColumns().get("PK_COL2A"));
		assertNull(tab11.getColumns().get("PK_COL3A"));
		assertNotNull(tab11.getColumns().get("PK_COL4A"));
		assertUnique(tab11, "PARENT_ID", "PK_COL4A");
		assertTrue(new File(logs, "schema-surrogate-key.yaml").isFile());
	}

	@Test
	void testSequenceAndCustomNames() throws XMLStreamException, IOException {
		Schema schema = createHierarchy();
		File input = writeInput(schema, "sequence.xml");
		File output = new File(temporaryDirectory, "sequence-output");

		CompositePrimaryKeyToSurrogateKeyCommand command = new CompositePrimaryKeyToSurrogateKeyCommand();
		command.setTargetFile(input);
		command.setOutputDirectory(output);
		command.setGenerationType(SurrogateKeyGenerationType.SEQUENCE);
		command.setPrimaryKeyColumnNameStrategy(table -> "SURROGATE_ID");
		command.setPrimaryKeyDataTypeStrategy(table -> DataType.BIGINT);
		command.setSequenceNameStrategy(table -> table.getName() + "_SEQ");
		command.run();

		Schema converted = (Schema) SchemaUtils.readXml(new File(output, input.getName()));
		Column id = converted.getTables().get("TAB").getColumns().get("SURROGATE_ID");
		assertFalse(id.isIdentity());
		assertEquals(DataType.BIGINT, id.getDataType());
		assertEquals("TAB_SEQ", id.getSequenceName());
		assertNotNull(converted.getSequences().get("TAB_SEQ"));
	}

	@Test
	void testRejectRows() {
		Schema schema = createHierarchy();
		Table table = schema.getTables().get("TAB");
		table.getRows().add(table.newRow());
		CompositePrimaryKeyToSurrogateKeyCommand command = new CompositePrimaryKeyToSurrogateKeyCommand();
		assertThrows(CommandException.class, () -> command.transform(schema));
	}

	@Test
	void testRejectIndexUsingReplacedForeignKeyColumn() {
		Schema schema = createHierarchy();
		Table table = schema.getTables().get("TAB_1");
		table.getIndexes().add(new Index("IDX_TAB_1_PARENT", table.getColumns().get("PK_COL1")));
		CompositePrimaryKeyToSurrogateKeyCommand command = new CompositePrimaryKeyToSurrogateKeyCommand();
		CommandException exception = assertThrows(CommandException.class, () -> command.transform(schema));
		assertTrue(exception.getMessage().contains("IDX_TAB_1_PARENT"));
	}

	@Test
	void testConvertNonIdentifyingForeignKeyAndPreserveNullability() {
		Schema schema = createHierarchy();
		Table parent = schema.getTables().get("TAB");
		Table notes = new Table("NOTES");
		Column noteId = new Column("NOTE_ID").setDataType(DataType.INT).setNotNull(true);
		notes.getColumns().add(noteId);
		notes.getColumns().add(new Column("TAB_KEY1").setDataType(DataType.VARCHAR).setLength(256));
		notes.getColumns().add(new Column("TAB_KEY2").setDataType(DataType.VARCHAR).setLength(256));
		notes.setPrimaryKey("PK_NOTES", noteId);
		schema.getTables().add(notes);
		notes.getConstraints().addForeignKeyConstraint("FK_NOTES_TAB",
				new Column[] { notes.getColumns().get("TAB_KEY1"), notes.getColumns().get("TAB_KEY2") },
				new Column[] { parent.getColumns().get("PK_COL1"), parent.getColumns().get("PK_COL2") });

		new CompositePrimaryKeyToSurrogateKeyCommand().transform(schema);

		assertNull(notes.getColumns().get("TAB_KEY1"));
		assertNull(notes.getColumns().get("TAB_KEY2"));
		Column parentId = notes.getColumns().get("PARENT_ID");
		assertNotNull(parentId);
		assertFalse(parentId.isNotNull());
		assertPrimaryKey(notes, "NOTE_ID");
		assertEquals("ID",
				notes.getConstraints().getForeignKeyConstraints().getFirst().getRelatedColumns().getFirst().getName());
	}

	private Schema createHierarchy() {
		Schema schema = new Schema("PUBLIC");
		Table tab = table("TAB", "PK_COL1", "PK_COL2");
		schema.getTables().add(tab);

		Table tab1 = table("TAB_1", "PK_COL1", "PK_COL2", "PK_COL3");
		schema.getTables().add(tab1);
		tab1.getConstraints().addForeignKeyConstraint("FK_TAB_1_TAB",
				new Column[] { tab1.getColumns().get("PK_COL1"), tab1.getColumns().get("PK_COL2") },
				new Column[] { tab.getColumns().get("PK_COL1"), tab.getColumns().get("PK_COL2") });

		Table tab11 = table("TAB_1_1", "PK_COL1A", "PK_COL2A", "PK_COL3A", "PK_COL4A");
		schema.getTables().add(tab11);
		tab11.getConstraints().addForeignKeyConstraint("FK_TAB_1_1_TAB_1",
				new Column[] { tab11.getColumns().get("PK_COL1A"), tab11.getColumns().get("PK_COL2A"),
						tab11.getColumns().get("PK_COL3A") },
				new Column[] { tab1.getColumns().get("PK_COL1"), tab1.getColumns().get("PK_COL2"),
						tab1.getColumns().get("PK_COL3") });
		return schema;
	}

	private Table table(String name, String... primaryKeyNames) {
		Table table = new Table(name);
		for (String columnName : primaryKeyNames) {
			table.getColumns().add(new Column(columnName).setDataType(DataType.VARCHAR).setLength(256));
		}
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(256));
		table.getColumns().add(new Column("CREATED_AT").setDataType(DataType.DATETIME).setNotNull(true));
		table.setPrimaryKey("PK_" + name,
				java.util.Arrays.stream(primaryKeyNames).map(item -> table.getColumns().get(item))
						.toArray(Column[]::new));
		return table;
	}

	private File writeInput(Schema schema, String name) throws XMLStreamException, IOException {
		File directory = new File(temporaryDirectory, "input-" + name);
		assertTrue(directory.mkdirs());
		File input = new File(directory, name);
		schema.writeXml(input);
		return input;
	}

	private void assertPrimaryKey(Table table, String... names) {
		assertEquals(java.util.List.of(names),
				table.getPrimaryKeyConstraint().getColumns().toColumns().stream().map(Column::getName).toList());
	}

	private void assertUnique(Table table, String... names) {
		assertTrue(table.getConstraints().getUniqueConstraints(item -> !item.isPrimaryKey()).stream()
				.anyMatch(item -> item.getColumns().toColumns().stream().map(Column::getName).toList()
						.equals(java.util.List.of(names))));
	}
}
