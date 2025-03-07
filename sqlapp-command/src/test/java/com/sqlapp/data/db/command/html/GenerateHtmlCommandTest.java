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

import java.io.File;

import org.junit.jupiter.api.Test;

public class GenerateHtmlCommandTest {

	@Test
	public void testRun() {
		GenerateHtmlCommand command = new GenerateHtmlCommand();
		command.setTargetFile(new File("src/test/resources/postgres/Catalog.xml"));
		// command.setTargetFile(new File("src/test/resources/mysql/Catalog.xml"));
		// command.setTargetFile(new File("src/test/resources/mysql/schemas.xml"));
		// command.setTargetFile(new File("src/test/resources/oracle/Catalog.xml"));
		// command.setTargetFile(new File("src/test/resources/sqlserver/Catalog.xml"));
		command.setOutputDirectory(new File("bin/html"));
		command.setDictionaryFileDirectory(new File("bin/dictionaries"));
		command.setDictionaryFileType("xml");
		// command.setDiagramFont("ＭＳ ゴシック");
		command.setPlaceholders(true);
		command.setMultiThread(true);
		// command.setFileDirectory(new File("src/test/resources/dbresources"));
		// command.setDirectory(new File("src/test/resources/export"));
		// command.setForeignKeyDefinitionDirectory(new File("src/test/resources/fk"));
		command.run();
	}

}
