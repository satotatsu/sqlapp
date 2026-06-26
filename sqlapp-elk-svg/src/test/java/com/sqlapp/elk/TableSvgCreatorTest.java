/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

class TableSvgCreatorTest {

	@Test
	void testSample() throws IOException {
		Catalog catalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get("PUBLIC");
		TableSvgCreator creator = new TableSvgCreator();
		String svg = creator.generateSvg(schema.getTables()).getImage();
		String expected = FileUtils.readText(new File("./src/test/resources/svg/sample.svg"), Charset.forName("UTF8"));
		assertEquals(expected, svg);
	}

	@Test
	void testSimple() throws IOException {
		Catalog catalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get("PUBLIC");
		TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.SIMPLE);
		String svg = creator.generateSvg(schema.getTables()).getImage();
		String expected = FileUtils.readText(new File("./src/test/resources/svg/simple.svg"), Charset.forName("UTF8"));
		assertEquals(expected, svg);
	}

	@Test
	void testSchemaSimple() throws IOException {
		Catalog catalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Catalog catalog1 = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get("PUBLIC");
		Schema schema2 = catalog1.getSchemas().get("PUBLIC");
		schema2.setName("Dummy");
		List<Schema> schemas = CommonUtils.list();
		schemas.add(schema);
		schemas.add(schema2);
		TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.SIMPLE);
		String svg = creator.generateSchemaSvg(schemas).getImage();
		String expected = FileUtils.readText(new File("./src/test/resources/svg/schemasSimple.svg"),
				Charset.forName("UTF8"));
		assertEquals(expected, svg);
	}

	@Test
	void testSchema() throws IOException {
		Catalog catalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Catalog catalog1 = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get("PUBLIC");
		Schema schema2 = catalog1.getSchemas().get("PUBLIC");
		schema2.setName("Dummy");
		List<Schema> schemas = CommonUtils.list();
		schemas.add(schema);
		schemas.add(schema2);
		TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.NORMAL);
		String svg = creator.generateSchemaSvg(schemas).getImage();
		String expected = FileUtils.readText(new File("./src/test/resources/svg/schemas.svg"), Charset.forName("UTF8"));
		assertEquals(expected, svg);
	}
}
