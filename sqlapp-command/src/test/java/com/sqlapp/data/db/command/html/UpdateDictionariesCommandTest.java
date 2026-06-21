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

package com.sqlapp.data.db.command.html;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

public class UpdateDictionariesCommandTest {

	protected File testProjectDir;

	@Test
	public void testRun() {
		UpdateDictionariesCommand command = new UpdateDictionariesCommand();
		command.setWithSchema((o) -> true);
		command.setTargetFile(new File("src/test/resources/schemas/Catalog.xml"));
		// command.setTargetFile(new File("src/test/resources/mysql/catalog.xml"));
		// command.setFile(new File("src/test/resources/oracle/catalog.xml"));
		command.setDirectory(testProjectDir);
		// command.setPropertyFileType("xml");
		// command.setPropertyFileType("properties");
		// command.setPropertyFileType("json");
		// command.setPropertyFileType("csv");
		command.setDictionaryFileType("xlsx");
		command.run();
		File file = new File(testProjectDir, "tables.xlsx");
		assertTrue(file.exists());
		//
		command.setDictionaryFileType("yaml");
		command.run();
		file = new File(testProjectDir, "tables.xlsx");
		assertFalse(!file.exists());
		file = new File(testProjectDir, "tables.yaml");
		assertTrue(file.exists());
		//
		command.setDictionaryFileType("json");
		command.run();
		file = new File(testProjectDir, "tables.xlsx");
		assertFalse(!file.exists());
		file = new File(testProjectDir, "tables.yaml");
		assertFalse(!file.exists());
		file = new File(testProjectDir, "tables.json");
		assertTrue(file.exists());
		command.setDictionaryFileType("csv");
		command.run();
		file = new File(testProjectDir, "tables.xlsx");
		assertFalse(!file.exists());
		file = new File(testProjectDir, "tables.yaml");
		assertFalse(!file.exists());
		file = new File(testProjectDir, "tables.json");
		assertTrue(!file.exists());
		file = new File(testProjectDir, "tables.csv");
		assertTrue(!file.exists());
	}

}
