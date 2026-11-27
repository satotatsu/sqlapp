package com.sqlapp.data.db.command.html;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.schemas.*;

class ProjectionHtmlDocsTest {
	@TempDir
	File directory;

	@Test
	void generatesSeparateProjectionMenuDetailsAndSourceLinksWithEscapedText() throws Exception {
		Catalog catalog = new Catalog("db");
		Schema source = new Schema("source");
		Table table = new Table("events");
		table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT));
		source.getTables().add(table);
		catalog.getSchemas().add(source);
		Schema physical = new Schema("physical");
		catalog.getSchemas().add(physical);
		Projection projection = new Projection("p & report");
		projection.setTableSchemaName("source").setTableName("events");
		projection.getColumns().add("<output>");
		projection.getSortColumns().add("<output>");
		projection.setStatement("SELECT id AS <output> FROM source.events");
		projection.setDefinition("CREATE PROJECTION p AS SELECT id FROM source.events");
		projection.getSpecifics().put("SEGMENT_EXPRESSION", "hash(id) <unsafe>");
		physical.getProjections().add(projection);
		Projection missing = new Projection("orphan");
		missing.setTableSchemaName("absent").setTableName("missing source");
		physical.getProjections().add(missing);
		var command = new GenerateHtmlDocsCommand();
		command.setCatalog(catalog);
		command.setOutputDirectory(directory);
		command.setMultiThread(false);
		command.run();
		String index = Files.readString(directory.toPath().resolve("projections.html"));
		String path = "projections/" + HtmlUtils.objectFullPath(projection) + ".html";
		String detail = Files.readString(directory.toPath().resolve(path));
		assertTrue(index.contains("p &amp; report"), index);
		assertTrue(index.contains("&lt;output&gt;"), index);
		assertFalse(detail.contains("<unsafe>"), detail);
		assertFalse(detail.contains("<output>"), detail);
		assertTrue(detail.contains("&lt;unsafe&gt;"), detail);
		assertTrue(detail.contains("../tables/" + HtmlUtils.objectFullPath(table) + ".html"), detail);
		String schema = Files.readString(directory.toPath().resolve("schemas/physical.html"));
		assertTrue(schema.contains("../projections/"), schema);
		assertTrue(Files.exists(directory.toPath().resolve("tables/" + HtmlUtils.objectFullPath(table) + ".html")));
		String orphan = Files
				.readString(directory.toPath().resolve("projections/" + HtmlUtils.objectFullPath(missing) + ".html"));
		assertTrue(orphan.contains("missing source"), orphan);
		assertFalse(orphan.contains("../tables/"), orphan);
		assertEquals(2, MenuDefinition.Projections.getDatas(catalog).size());
	}
}
