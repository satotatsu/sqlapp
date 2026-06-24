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
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

public class GenerateHtmlDocsCommandTest {
	@TempDir
	protected File testProjectDir;
	// protected File testProjectDir = new File("./");

	@Test
	public void testRun() throws IOException {
		File outputDir = new File(testProjectDir, "html");
		File dicDir = new File(testProjectDir, "dictionaries");
		Catalog catalog = SchemaUtils.readXml(new File("src/test/resources/schemas/catalog.xml"));
		GenerateHtmlDocsCommand command = new GenerateHtmlDocsCommand();
		Schema schema = catalog.getSchemas().get("PUBLIC");
		schema.getTables().forEach(t -> {
			t.getColumns().forEach(c -> {
				c.getSpecifics().put("DUMMY_SPEC", 10);
			});
			t.getColumns().forEach(c -> {
				c.getStatistics().put("DUMMY_STAT1", 10);
				c.getStatistics().put("DUMMY_STAT2", 20);
			});
		});
		command.setCatalog(catalog);
		command.setOutputDirectory(outputDir);
		command.setDictionaryFileDirectory(dicDir);
		// command.setDiagramFont("ＭＳ ゴシック");
		command.setPlaceholders(true);
		command.setMultiThread(true);
		command.run();
	}

}
