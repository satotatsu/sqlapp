package com.sqlapp.data.db.command.html;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.schemas.*;

class MviewHtmlDocsTest {
	@TempDir
	File directory;

	@Test
	void displaysEscapedMaterializedViewOwner() throws Exception {
		var catalog = new Catalog("db");
		var schema = new Schema("reports");
		catalog.getSchemas().add(schema);
		var view = new Mview("totals").setOwnerName("<Reporting & Owners>");
		view.setStatement("SELECT 7 AS total");
		schema.getMviews().add(view);
		var command = new GenerateHtmlDocsCommand();
		command.setCatalog(catalog);
		command.setOutputDirectory(directory);
		command.setMultiThread(false);
		command.run();
		String detail = Files
				.readString(directory.toPath().resolve("mviews/" + HtmlUtils.objectFullPath(view) + ".html"));
		assertTrue(detail.contains("&lt;Reporting &amp; Owners&gt;"), detail);
		assertFalse(detail.contains("<Reporting & Owners>"), detail);
	}
}
