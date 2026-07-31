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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.viewpoint.SchemaViewpointsIO;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.SystemVersioning;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TemporalPeriod;
import com.sqlapp.data.schemas.TemporalPeriodType;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpoint;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpoints;

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
		assertTrue(catalog.getSchemas().stream().flatMap(schema -> schema.getTables().stream())
				.anyMatch(table -> "CUSTOMERS".equals(table.getName())));
		Path invoicePath;
		try (var paths = Files.list(new File(outputDir, "tables").toPath())) {
			invoicePath = paths.filter(path -> path.getFileName().toString().contains("INVOICES")).findFirst()
					.orElseThrow();
		}
		String tableHtml = java.nio.file.Files.readString(invoicePath);
		assertTrue(tableHtml.contains("Viewpoints"));
		assertTrue(tableHtml.contains("viewpoint-sales.svg"));
		String relationshipsHtml = java.nio.file.Files.readString(new File(outputDir, "relationships.html").toPath());
		assertTrue(relationshipsHtml.contains("AllRelationships"));
		assertTrue(relationshipsHtml.contains("Viewpoint_sales"));
		assertTrue(relationshipsHtml.contains("viewpoint-sales.svg"));
		try (var paths = java.nio.file.Files.list(new File(outputDir, "tables").toPath())) {
			assertTrue(paths.anyMatch(path -> path.getFileName().toString().contains("CUSTOMERS")));
		}
	}

	@Test
	public void testTemporalTableDocumentation() throws IOException {
		File outputDir = new File(testProjectDir, "temporal-html");
		Catalog catalog = new Catalog("CATALOG");
		Schema schema = new Schema("PUBLIC");
		Table table = new Table("AUDIT_LOG");
		catalog.getSchemas().add(schema);
		schema.getTables().add(table);
		table.getColumns().add("ID");
		table.getColumns().add("ROW_START");
		table.getColumns().add("ROW_END");
		table.getTemporalPeriods().add(new TemporalPeriod("SYSTEM_TIME")
				.setPeriodType(TemporalPeriodType.SYSTEM_TIME)
				.setStartColumnName("ROW_START")
				.setEndColumnName("ROW_END"));
		table.setSystemVersioning(new SystemVersioning()
				.setPeriodName("SYSTEM_TIME")
				.setHistoryTableName("AUDIT_LOG_HISTORY")
				.setTransactionIdColumnName("TRANSACTION_ID"));

		GenerateHtmlDocsCommand command = new GenerateHtmlDocsCommand();
		command.setCatalog(catalog);
		command.setOutputDirectory(outputDir);
		command.setMultiThread(false);
		command.run();

		String tablesHtml = Files.readString(new File(outputDir, "tables.html").toPath());
		assertTrue(tablesHtml.contains("AUDIT_LOG"));
		assertTrue(tablesHtml.contains("Temporal") || tablesHtml.contains("テンポラル"));

		Path tablePath;
		try (var paths = Files.list(new File(outputDir, "tables").toPath())) {
			tablePath = paths.filter(path -> path.getFileName().toString().contains("AUDIT_LOG"))
					.findFirst().orElseThrow();
		}
		String tableHtml = Files.readString(tablePath);
		assertTrue(tableHtml.contains("href=\"#Temporal\""));
		assertTrue(tableHtml.contains("id=\"Temporal\""));
		assertTrue(tableHtml.contains("SYSTEM_TIME"));
		assertTrue(tableHtml.contains("ROW_START"));
		assertTrue(tableHtml.contains("ROW_END"));
		assertTrue(tableHtml.contains("AUDIT_LOG_HISTORY"));
		assertTrue(tableHtml.contains("TRANSACTION_ID"));
	}

	@Test
	public void testNamedNotNullConstraintDocumentation() throws IOException {
		File outputDir = new File(testProjectDir, "named-not-null-html");
		Catalog catalog = new Catalog("CATALOG");
		Schema schema = new Schema("PUBLIC");
		Table table = new Table("CUSTOMERS");
		Column column = new Column("CUSTOMER_ID")
				.setDataType(DataType.BIGINT).setNotNull(true);
		catalog.getSchemas().add(schema);
		schema.getTables().add(table);
		table.getColumns().add(column);
		table.getConstraints().add(new NotNullConstraint(
				"NN_CUSTOMERS_CUSTOMER_ID", column).setNoInherit(true));

		GenerateHtmlDocsCommand command = new GenerateHtmlDocsCommand();
		command.setCatalog(catalog);
		command.setOutputDirectory(outputDir);
		command.setMultiThread(false);
		command.run();

		Path tablePath;
		try (var paths = Files.list(new File(outputDir, "tables").toPath())) {
			tablePath = paths
					.filter(path -> path.getFileName().toString()
							.contains("CUSTOMERS"))
					.findFirst().orElseThrow();
		}
		String tableHtml = Files.readString(tablePath);
		assertTrue(tableHtml.contains("NN_CUSTOMERS_CUSTOMER_ID"));
		assertTrue(tableHtml.contains("NOT NULL Constraint")
				|| tableHtml.contains("NOT NULL制約名"));
	}

	@Test
	public void testVectorDocumentation() throws IOException {
		File outputDir = new File(testProjectDir, "vector-html");
		Catalog catalog = new Catalog("CATALOG");
		Schema schema = new Schema("PUBLIC");
		Table table = new Table("DOCUMENTS");
		Column vector = new Column("EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorElementDataType(DataType.REAL)
				.setVectorDimension(768);
		Index index = new Index("IDX_DOCUMENTS_EMBEDDING", vector)
				.setIndexType(IndexType.Vector)
				.setVectorDistanceType(VectorDistanceType.Cosine);
		catalog.getSchemas().add(schema);
		schema.getTables().add(table);
		table.getColumns().add(vector);
		table.getIndexes().add(index);

		GenerateHtmlDocsCommand command = new GenerateHtmlDocsCommand();
		command.setCatalog(catalog);
		command.setOutputDirectory(outputDir);
		command.setMultiThread(false);
		command.run();

		String tablesHtml = Files.readString(new File(outputDir, "tables.html").toPath());
		assertTrue(tablesHtml.contains("DOCUMENTS"));
		assertTrue(tablesHtml.contains("Vector") || tablesHtml.contains("ベクトル"));

		Path tablePath;
		try (var paths = Files.list(new File(outputDir, "tables").toPath())) {
			tablePath = paths.filter(path -> path.getFileName().toString().contains("DOCUMENTS"))
					.findFirst().orElseThrow();
		}
		String tableHtml = Files.readString(tablePath);
		assertTrue(tableHtml.contains("href=\"#Vector\""));
		assertTrue(tableHtml.contains("id=\"Vector\""));
		assertTrue(tableHtml.contains("EMBEDDING"));
		assertTrue(tableHtml.contains("REAL"));
		assertTrue(tableHtml.contains("768"));
		assertTrue(tableHtml.contains("IDX_DOCUMENTS_EMBEDDING"));
		assertTrue(tableHtml.contains("Cosine") || tableHtml.contains("COSINE"));
	}

}
