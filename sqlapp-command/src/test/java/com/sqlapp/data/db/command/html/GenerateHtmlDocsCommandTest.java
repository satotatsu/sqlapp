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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import com.sqlapp.util.YamlConverter;

public class GenerateHtmlDocsCommandTest {
	@TempDir
	protected File testProjectDir;
	// protected File testProjectDir = new File("./");

	@Test
	void encodesMermaidDownloadFileNames() {
		RelationImageHolder holder = new RelationImageHolder(new File("diagram.svg"), "", "");
		holder.setMermaidFile(new File("図 & 100%+#.mmd"));
		assertEquals("%E5%9B%B3%20%26%20100%25%2B%23.mmd", holder.getMermaidFileUrl());
	}

	@Test
	void passesTheConfiguredYamlConverterToTheSharedTableReader() {
		final var converter = new YamlConverter();
		final var command = new GenerateHtmlDocsCommand();
		command.setYamlConverter(converter);

		assertSame(converter, command.createTableFileReader().getYamlConverter());
	}

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
		assertMermaidDownloads(outputDir.toPath());
		assertTableDetailsHaveDdlAndNoRelations(outputDir.toPath());
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
		assertTrue(tableHtml.contains("href=\"#DDL\""));
		assertTrue(tableHtml.contains("CREATE"));
		assertFalse(tableHtml.contains("id=\"Relationships\""));
		assertFalse(tableHtml.contains("id=\"Viewpoints\""));
		assertFalse(tableHtml.contains("viewpoint-sales.svg"));
		String relationshipsHtml = java.nio.file.Files.readString(new File(outputDir, "relationships.html").toPath());
		assertTrue(relationshipsHtml.contains("AllRelationships"));
		assertTrue(relationshipsHtml.contains("Viewpoint_sales"));
		assertTrue(relationshipsHtml.contains("viewpoint-sales.svg"));
		assertTrue(relationshipsHtml.contains("viewpoint-sales.mmd"));
		assertFalse(tableHtml.contains("viewpoint-sales.mmd"));
		assertMermaidDownloads(outputDir.toPath());
		try (var paths = java.nio.file.Files.list(new File(outputDir, "tables").toPath())) {
			assertTrue(paths.anyMatch(path -> path.getFileName().toString().contains("CUSTOMERS")));
		}
	}

	@Test
	void downloadsInheritanceAndPartitionRelationsFromXml() throws Exception {
		Catalog catalog = new Catalog("CAT");
		Schema schema = new Schema("PUBLIC");
		catalog.getSchemas().add(schema);
		Table parent = new Table("BASE");
		Table inheritanceOnlyParent = new Table("OTHER_BASE");
		Table child = new Table("DERIVED");
		Table partition = new Table("PARTITION");
		for (Table table : java.util.List.of(parent, inheritanceOnlyParent, child, partition)) {
			schema.getTables().add(table);
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			table.setDisplayName("論理_" + table.getName());
		}
		child.getInherits().add(parent);
		child.getInherits().add(inheritanceOnlyParent);
		partition.setPartitionParent(parent, "1", "10");
		parent.getColumns().get("ID").setDefaultValue("'<unsafe>'");
		Path xml = testProjectDir.toPath().resolve("hierarchy.xml");
		try (var stream = Files.newOutputStream(xml)) {
			catalog.writeXml(stream);
		}
		Path dictionaries = Files.createDirectory(testProjectDir.toPath().resolve("hierarchy-dictionaries"));
		var names = new java.util.Properties();
		for (Table table : schema.getTables()) {
			names.setProperty(table.getName() + ".displayName", table.getDisplayName());
		}
		try (var stream = Files.newOutputStream(dictionaries.resolve("tables.xml"))) {
			names.storeToXML(stream, "Logical names", java.nio.charset.StandardCharsets.UTF_8);
		}
		Path output = testProjectDir.toPath().resolve("hierarchy-html");
		GenerateHtmlDocsCommand command = new GenerateHtmlDocsCommand();
		command.setTargetFile(xml.toFile());
		command.setDictionaryFileDirectory(dictionaries.toFile());
		command.setOutputDirectory(output.toFile());
		command.setMultiThread(false);
		command.run();
		assertMermaidDownloads(output);
		for (String suffix : java.util.List.of("", "_logical")) {
			String all = Files.readString(output.resolve("diagrams/_summary_relations_large" + suffix + ".mmd"));
			assertTrue(all.contains(": \"inherits\""));
			assertTrue(all.contains(": \"partition of\""));
		}
		assertTableDetailsHaveDdlAndNoRelations(output);
		String parentHtml = Files.readString(
				output.resolve("tables/" + HtmlUtils.objectFullPath(parent) + ".html"));
		assertTrue(parentHtml.contains("&lt;unsafe&gt;"));
		assertFalse(parentHtml.contains("<unsafe>"));
		assertFalse(Files.exists(output.resolve("diagrams/" + HtmlUtils.objectFullPath(parent) + ".svg")));
	}

	@Test
	void createsDdlWithoutDatabaseProductMetadata() {
		Catalog catalog = new Catalog("CAT");
		Schema schema = new Schema("PUBLIC");
		Table table = new Table("SAMPLE");
		catalog.getSchemas().add(schema);
		schema.getTables().add(table);
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("VALUE").setDataType(DataType.VARCHAR).setLength(30));
		table.setPrimaryKey("PK_SAMPLE", table.getColumns().get("ID"));

		String ddl = new GenerateHtmlDocsCommand().createTableDdl(table);

		assertTrue(ddl.contains("CREATE TABLE"));
		assertTrue(ddl.contains("SAMPLE"));
		assertTrue(ddl.contains("PRIMARY KEY"));
	}

	private void assertTableDetailsHaveDdlAndNoRelations(Path output) throws IOException {
		try (var paths = Files.list(output.resolve("tables"))) {
			for (Path html : paths.filter(path -> path.toString().endsWith(".html")).toList()) {
				String content = Files.readString(html);
				assertTrue(content.contains("href=\"#DDL\""), html.toString());
				assertTrue(content.contains("<div id=\"DDL\">"), html.toString());
				assertTrue(content.contains("CREATE"), html.toString());
				assertFalse(content.contains("id=\"Relationships\""), html.toString());
				assertFalse(content.contains("id=\"Viewpoints\""), html.toString());
			}
		}
	}

	private void assertMermaidDownloads(Path output) throws IOException {
		try (var paths = Files.walk(output.resolve("diagrams"))) {
			for (Path svg : paths.filter(path -> path.toString().endsWith(".svg")).toList()) {
				Path mmd = svg.resolveSibling(svg.getFileName().toString().replaceFirst("\\.svg$", ".mmd"));
				assertTrue(Files.readString(mmd).startsWith("erDiagram\n"), mmd.toString());
			}
		}
		int links = 0;
		var pattern = java.util.regex.Pattern.compile("href=\"([^\"]+\\.mmd)\" download");
		try (var paths = Files.walk(output)) {
			for (Path html : paths.filter(path -> path.toString().endsWith(".html")).toList()) {
				var matcher = pattern.matcher(Files.readString(html));
				while (matcher.find()) {
					String target = java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
					assertTrue(Files.isRegularFile(html.getParent().resolve(target)), target);
					links++;
				}
			}
		}
		assertTrue(links > 0);
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
		table.getTemporalPeriods().add(new TemporalPeriod("SYSTEM_TIME").setPeriodType(TemporalPeriodType.SYSTEM_TIME)
				.setStartColumnName("ROW_START").setEndColumnName("ROW_END"));
		table.setSystemVersioning(new SystemVersioning().setPeriodName("SYSTEM_TIME")
				.setHistoryTableName("AUDIT_LOG_HISTORY").setTransactionIdColumnName("TRANSACTION_ID"));

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
			tablePath = paths.filter(path -> path.getFileName().toString().contains("AUDIT_LOG")).findFirst()
					.orElseThrow();
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
		Column column = new Column("CUSTOMER_ID").setDataType(DataType.BIGINT).setNotNull(true);
		catalog.getSchemas().add(schema);
		schema.getTables().add(table);
		table.getColumns().add(column);
		table.getConstraints().add(new NotNullConstraint("NN_CUSTOMERS_CUSTOMER_ID", column).setNoInherit(true));

		GenerateHtmlDocsCommand command = new GenerateHtmlDocsCommand();
		command.setCatalog(catalog);
		command.setOutputDirectory(outputDir);
		command.setMultiThread(false);
		command.run();

		Path tablePath;
		try (var paths = Files.list(new File(outputDir, "tables").toPath())) {
			tablePath = paths.filter(path -> path.getFileName().toString().contains("CUSTOMERS")).findFirst()
					.orElseThrow();
		}
		String tableHtml = Files.readString(tablePath);
		assertTrue(tableHtml.contains("NN_CUSTOMERS_CUSTOMER_ID"));
		assertTrue(tableHtml.contains("NOT NULL Constraint") || tableHtml.contains("NOT NULL制約名"));
	}

	@Test
	public void testVectorDocumentation() throws IOException {
		File outputDir = new File(testProjectDir, "vector-html");
		Catalog catalog = new Catalog("CATALOG");
		Schema schema = new Schema("PUBLIC");
		Table table = new Table("DOCUMENTS");
		Column vector = new Column("EMBEDDING").setDataType(DataType.VECTOR).setVectorElementDataType(DataType.REAL)
				.setVectorDimension(768);
		Index index = new Index("IDX_DOCUMENTS_EMBEDDING", vector).setIndexType(IndexType.Vector)
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
			tablePath = paths.filter(path -> path.getFileName().toString().contains("DOCUMENTS")).findFirst()
					.orElseThrow();
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
