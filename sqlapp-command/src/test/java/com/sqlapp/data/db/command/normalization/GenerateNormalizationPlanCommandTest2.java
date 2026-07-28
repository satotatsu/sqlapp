/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class GenerateNormalizationPlanCommandTest2 {

	@TempDir
	File temporaryDirectory;

	@Test
	void testNormalizeSchemaXmlWithCustomNames() throws XMLStreamException, IOException {
		Schema schema = new Schema("PUBLIC");
		Table tab = createTable();
		schema.getTables().add(tab);
		Table tab1 = createTable1();
		tab1.getConstraints().addForeignKeyConstraint("FK" + tab1.getName(),
				new Column[] { tab1.getColumns().get("PK1"), tab1.getColumns().get("PK2"),
						tab1.getColumns().get("PK3") },
				new Column[] { tab.getColumns().get("PK1"), tab.getColumns().get("PK2"), tab.getColumns().get("PK3") });
		schema.getTables().add(tab1);
		//
		Table tab1_1 = createTable1_1();
		tab1_1.getConstraints().addForeignKeyConstraint("FK" + tab1_1.getName(),
				new Column[] { tab1_1.getColumns().get("PK1"), tab1_1.getColumns().get("PK2"),
						tab1_1.getColumns().get("PK3"), tab1_1.getColumns().get("PK4") },
				new Column[] { tab1.getColumns().get("PK1"), tab1.getColumns().get("PK2"), tab1.getColumns().get("PK3"),
						tab1.getColumns().get("PK4") });
		schema.getTables().add(tab1_1);
		//
		Table tab1_2 = createTable1_1();
		tab1_2.getConstraints().addForeignKeyConstraint("FK" + tab1_2.getName(),
				new Column[] { tab1_2.getColumns().get("PK1"), tab1_2.getColumns().get("PK2"),
						tab1_2.getColumns().get("PK3"), tab1_2.getColumns().get("PK4") },
				new Column[] { tab1.getColumns().get("PK1"), tab1.getColumns().get("PK2"), tab1.getColumns().get("PK3"),
						tab1.getColumns().get("PK4") });
		schema.getTables().add(tab1_1);
		//
		Table tab2 = createTable2();
		tab2.getConstraints().addForeignKeyConstraint("FK" + tab2.getName(),
				new Column[] { tab2.getColumns().get("PK1"), tab2.getColumns().get("PK2"),
						tab1.getColumns().get("PK3") },
				new Column[] { tab.getColumns().get("PK1"), tab.getColumns().get("PK2"), tab.getColumns().get("PK3") });
		schema.getTables().add(tab2);

		File source = new File(temporaryDirectory, "legacy.xml");
		schema.writeXml(source);
		File output = new File(temporaryDirectory, "plan");

		GenerateNormalizationPlanCommand command = new GenerateNormalizationPlanCommand();
		command.setTargetFile(source);
		command.setOutputDirectory(output);
		command.setLocale(Locale.ENGLISH);
		command.run();
		File outFile = new File(output, "legacy-normalization-preview.xml");
		Schema resultSchema = new Schema();
		resultSchema.loadXml(outFile);
		System.out.println(resultSchema.asXml());
		checkResult(resultSchema);
	}

	private void checkResult(Schema schema) {
		Table tab = schema.getTable("tab");
		assertEquals(4, tab.getColumns().size());
		Table tab1 = schema.getTable("tab1");
		assertEquals(3, tab1.getColumns().size());
		Table tab1_1 = schema.getTable("tab1_1");
		assertEquals(3, tab1_1.getColumns().size());
		Table tab2 = schema.getTable("tab2");
		assertEquals(3, tab2.getColumns().size());
		Table tab1_DETAIL_1 = schema.getTable("tab1_DETAIL_1");
		assertEquals(5, tab1_DETAIL_1.getColumns().size());
	}

	private Table createTable() {
		Table tab = new Table("tab");
		tab.getColumns().add(c -> {
			c.setName("PK1");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK2");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK3");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.setPrimaryKey(tab.getColumns().get("PK1"), tab.getColumns().get("PK2"), tab.getColumns().get("PK3"));
		return tab;
	}

	private Table createTable1() {
		Table tab = new Table("tab1");
		tab.getColumns().add(c -> {
			c.setName("PK1");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK2");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK3");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK4");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		//
		tab.getColumns().add(c -> {
			c.setName("COLA_1");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("COLA_2");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		//
		tab.getColumns().add(c -> {
			c.setName("COLB_1");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("COLB_2");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.setPrimaryKey(tab.getColumns().get("PK1"), tab.getColumns().get("PK2"), tab.getColumns().get("PK3"),
				tab.getColumns().get("PK4"));
		return tab;
	}

	private Table createTable2() {
		Table tab = new Table("tab2");
		tab.getColumns().add(c -> {
			c.setName("PK1");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK2");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK3");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK4");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.setPrimaryKey(tab.getColumns().get("PK1"), tab.getColumns().get("PK2"), tab.getColumns().get("PK3"),
				tab.getColumns().get("PK4"));
		return tab;
	}

	private Table createTable1_1() {
		Table tab = new Table("tab1_1");
		tab.getColumns().add(c -> {
			c.setName("PK1");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK2");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK3");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK4");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK5");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.setPrimaryKey(tab.getColumns().get("PK1"), tab.getColumns().get("PK2"), tab.getColumns().get("PK3"),
				tab.getColumns().get("PK4"), tab.getColumns().get("PK5"));
		return tab;
	}

	private Table createTable1_2() {
		Table tab = new Table("tab1_2");
		tab.getColumns().add(c -> {
			c.setName("PK1");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK2");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK3");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK4");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.getColumns().add(c -> {
			c.setName("PK5");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		tab.setPrimaryKey(tab.getColumns().get("PK1"), tab.getColumns().get("PK2"), tab.getColumns().get("PK3"),
				tab.getColumns().get("PK4"), tab.getColumns().get("PK5"));
		return tab;
	}

}
