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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpoint;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpoints;
import com.sqlapp.data.db.command.viewpoint.SchemaViewpointsIO;

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

	@Test
	public void testViewpointGroupFiltersDocumentationSchemaModel() throws IOException {
		File outputDir = new File(testProjectDir, "viewpoint-html");
		Catalog catalog = SchemaUtils.readXml(new File("src/test/resources/schemas/catalog.xml"));
		SchemaViewpoints viewpoints = new SchemaViewpoints();
		SchemaViewpoint viewpoint = new SchemaViewpoint();
		viewpoint.setId("sales");
		viewpoint.getTables().add("PUBLIC.CUSTOMERS");
		viewpoint.getTables().add("PUBLIC.INVOICES");
		viewpoints.getViewpoints().add(viewpoint);
		File viewpointsFile = new File(testProjectDir, "viewpoints.yaml");
		new SchemaViewpointsIO().write(viewpointsFile, viewpoints);

		GenerateHtmlDocsCommand command = new GenerateHtmlDocsCommand();
		command.setCatalog(catalog);
		command.setOutputDirectory(outputDir);
		command.setMultiThread(false);
		command.setViewpointsFile(viewpointsFile);
		command.setViewpointId("sales");
		command.run();

		assertEquals(2, command.getResolvedViewpointTableIds().size());
		org.junit.jupiter.api.Assertions.assertTrue(catalog.getSchemas().stream()
				.flatMap(schema -> schema.getTables().stream())
				.anyMatch(table -> "CUSTOMERS".equals(table.getName())));
		java.nio.file.Path invoicePath;
		try (var paths = java.nio.file.Files.list(new File(outputDir, "tables").toPath())) {
			invoicePath = paths.filter(path -> path.getFileName().toString().contains("INVOICES"))
					.findFirst().orElseThrow();
		}
		String tableHtml = java.nio.file.Files.readString(invoicePath);
		org.junit.jupiter.api.Assertions.assertTrue(tableHtml.contains("Viewpoints"));
		org.junit.jupiter.api.Assertions.assertTrue(tableHtml.contains("viewpoint-sales.svg"));
		String relationshipsHtml = java.nio.file.Files.readString(
				new File(outputDir, "relationships.html").toPath());
		org.junit.jupiter.api.Assertions.assertTrue(relationshipsHtml.contains("AllRelationships"));
		org.junit.jupiter.api.Assertions.assertTrue(relationshipsHtml.contains("Viewpoint_sales"));
		org.junit.jupiter.api.Assertions.assertTrue(relationshipsHtml.contains("viewpoint-sales.svg"));
		try (var paths = java.nio.file.Files.list(new File(outputDir, "tables").toPath())) {
			org.junit.jupiter.api.Assertions.assertTrue(
					paths.anyMatch(path -> path.getFileName().toString().contains("CUSTOMERS")));
		}
	}

}
