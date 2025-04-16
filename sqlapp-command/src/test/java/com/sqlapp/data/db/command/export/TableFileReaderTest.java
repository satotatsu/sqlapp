/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.data.db.command.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.export.TableFileReader.TableFilesPair;
import com.sqlapp.data.schemas.Catalog;

class TableFileReaderTest {

	@TempDir
	private File testDirectory;

	@Test
	public void test() throws IOException {
		TableFileReader reader = new TableFileReader();
		reader.setDirectory(testDirectory);
		reader.setUseSchemaNameDirectory(false);
		Catalog catalog = createCatalog();
		List<TableFilesPair> list = reader.getTableFilesPairs(catalog);
		assertEquals(0, list.size());
		File file = new File(testDirectory, "table1_1.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(1, list.size());
		assertEquals("table1_1", list.get(0).getName());
		//
		file = new File(testDirectory, "table1_2.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(2, list.size());
		//
		file = new File(testDirectory, "table2_1.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(3, list.size());
		//
		file = new File(testDirectory, "table2_2.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(4, list.size());
	}

	@Test
	public void testUseSchemaNameDirectory1() throws IOException {
		TableFileReader reader = new TableFileReader();
		reader.setDirectory(testDirectory);
		reader.setUseSchemaNameDirectory(true);
		Catalog catalog = createCatalog();
		List<TableFilesPair> list = reader.getTableFilesPairs(catalog);
		assertEquals(0, list.size());
		File file = new File(testDirectory, "table1_1.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(0, list.size());
		//
		file = new File(testDirectory, "table1_2.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(0, list.size());
		//
		file = new File(testDirectory, "table2_1.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(0, list.size());
		//
		file = new File(testDirectory, "table2_2.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(0, list.size());
	}

	@Test
	public void testUseSchemaNameDirectory2() throws IOException {
		TableFileReader reader = new TableFileReader();
		reader.setDirectory(testDirectory);
		reader.setUseSchemaNameDirectory(true);
		Catalog catalog = createCatalog();
		List<TableFilesPair> list = reader.getTableFilesPairs(catalog);
		assertEquals(0, list.size());
		File parent1 = new File(testDirectory, "schema1");
		parent1.mkdirs();
		File parent2 = new File(testDirectory, "schema2");
		parent2.mkdirs();
		File file = new File(parent1, "table1_1.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(1, list.size());
		//
		file = new File(parent1, "table1_2.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(2, list.size());
		//
		file = new File(parent2, "table2_1.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(3, list.size());
		//
		file = new File(parent2, "table2_2.xlsx");
		file.createNewFile();
		list = reader.getTableFilesPairs(catalog);
		assertEquals(4, list.size());
	}

	private Catalog createCatalog() {
		Catalog catalog = new Catalog();
		catalog.getSchemas().add(s -> {
			s.setName("schema1");
			s.getTables().add(t -> {
				t.setName("table1_1");
			});
			s.getTables().add(t -> {
				t.setName("table1_2");
			});
		});
		catalog.getSchemas().add(s -> {
			s.setName("schema2");
			s.getTables().add(t -> {
				t.setName("table2_1");
			});
			s.getTables().add(t -> {
				t.setName("table2_2");
			});
		});
		return catalog;
	}

}
