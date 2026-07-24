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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

class TableSvgCreatorTest {

	@Test
	void japanese() throws IOException {
		Schema schema = createSchema();
		TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.SIMPLE);
		String svg = creator.generateSvg(schema.getTables()).getImage();
		String expected = FileUtils.readText(new File("./src/test/resources/svg/japanese.svg"),
				Charset.forName("UTF8"));
		assertEqualsValue(expected, svg);
	}

	@Test
	void sample() throws IOException {
		Catalog catalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get("PUBLIC");
		TableSvgCreator creator = new TableSvgCreator();
		String svg = creator.generateSvg(schema.getTables()).getImage();
		String expected = FileUtils.readText(new File("./src/test/resources/svg/sample.svg"), Charset.forName("UTF8"));
		assertEqualsValue(expected, svg);
	}

	@Test
	void simple() throws IOException {
		Catalog catalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get("PUBLIC");
		TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.SIMPLE);
		String svg = creator.generateSvg(schema.getTables()).getImage();
		String expected = FileUtils.readText(new File("./src/test/resources/svg/simple.svg"), Charset.forName("UTF8"));
		assertEqualsValue(expected, svg);
	}

	@Test
	void schemaSimple() throws IOException {
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
		assertEqualsValue(expected, svg);
	}

	@Test
	void schema() throws IOException {
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
		assertEqualsValue(expected, svg);
	}

	@Test
	void crossSchemaRelation() throws Exception {
		Schema masterSchema = new Schema("MASTER");
		Table customer = new Table("CUSTOMER");
		customer.getColumns().add(new Column("ID").setDataType(DataType.BIGINT).setNotNull(true));
		customer.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(100));
		customer.setPrimaryKey("PK_CUSTOMER", customer.getColumns().get("ID"));
		masterSchema.getTables().add(customer);

		Schema salesSchema = new Schema("SALES");
		Table orders = new Table("ORDERS");
		orders.getColumns().add(new Column("ID").setDataType(DataType.BIGINT).setNotNull(true));
		orders.getColumns().add(new Column("CUSTOMER_ID").setDataType(DataType.BIGINT).setNotNull(true));
		orders.setPrimaryKey("PK_ORDERS", orders.getColumns().get("ID"));
		salesSchema.getTables().add(orders);
		orders.getConstraints().addForeignKeyConstraint("FK_ORDERS_CUSTOMER", orders.getColumns().get("CUSTOMER_ID"),
				customer.getColumns().get("ID"));

		TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.NORMAL);
		String svg = creator.generateSchemaSvg(List.of(masterSchema, salesSchema)).getImage();

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		assertEquals("svg", factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg)))
				.getDocumentElement().getLocalName());
		assertTrue(svg.contains("class='relation cross-schema'"));
		assertTrue(svg.contains("data-constraint='FK_ORDERS_CUSTOMER'"));
		assertFalse(svg.contains("NaN"));
		assertFalse(svg.contains("Infinity"));
		Matcher pathMatcher = Pattern.compile("class='relation cross-schema'[^>]* d='M([^']+)'").matcher(svg);
		assertTrue(pathMatcher.find());
		Matcher numberMatcher = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?").matcher(pathMatcher.group(1));
		double[] coordinates = new double[8];
		for (int i = 0; i < coordinates.length; i++) {
			assertTrue(numberMatcher.find());
			coordinates[i] = Double.parseDouble(numberMatcher.group());
		}
		assertEquals(25.0, Math.abs(coordinates[1] - coordinates[5]), 0.001);

		Path output = Path.of("./build/test-results/cross-schema.svg");
		Files.createDirectories(output.getParent());
		Files.writeString(output, svg, StandardCharsets.UTF_8);
	}

	private Schema createSchema() {
		Schema schema = new Schema("Japanese");
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setDataType(DataType.VARCHAR);
			c.setName("あいうえお");
		});
		schema.getTables().add(table);
		table = new Table("tabB");
		table.getColumns().add(c -> {
			c.setDataType(DataType.VARCHAR);
			c.setName("abcdef");
		});
		schema.getTables().add(table);
		table = new Table("tabC");
		table.getColumns().add(c -> {
			c.setDataType(DataType.VARCHAR);
			c.setName("abcdef");
		});
		table.getColumns().add(c -> {
			c.setDataType(DataType.VARCHAR);
			c.setName("WWWWWW");
		});
		schema.getTables().add(table);
		return schema;
	}

	private boolean enabled = false;

	private void assertEqualsValue(String expected, String value) {
		if (enabled) {
			// JDKやフォントに依存するので無効化する
			assertEquals(expected.replace("\r\n", "\n").trim(), value.replace("\r\n", "\n").trim());
		}
	}

	@Test
	public void testEnv() {
		System.out.println(java.awt.GraphicsEnvironment.isHeadless());
		System.out.println(System.getProperty("java.version"));
		System.out.println(System.getProperty("java.vendor"));
		System.out.println(System.getProperty("java.awt.headless"));
		System.out.println(Locale.getDefault());
		java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
		System.out.println(Arrays.toString(ge.getAvailableFontFamilyNames()));
	}
}
