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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
		assertEquals(24.0, Math.abs(coordinates[1] - coordinates[5]), 0.001);

		Path output = Path.of("./build/test-results/cross-schema.svg");
		Files.createDirectories(output.getParent());
		Files.writeString(output, svg, StandardCharsets.UTF_8);
	}

	@Test
	void tableInheritance() throws Exception {
		Schema schema = new Schema("PUBLIC");
		Table parent = new Table("BASE_ENTITY");
		parent.getColumns().add(new Column("ID").setDataType(DataType.BIGINT).setNotNull(true));
		parent.getColumns().add(new Column("CREATED_AT").setDataType(DataType.DATETIME).setNotNull(true));
		parent.setPrimaryKey("PK_BASE_ENTITY", parent.getColumns().get("ID"));
		schema.getTables().add(parent);

		Table child = new Table("CUSTOMER_ENTITY");
		child.getColumns().add(new Column("ID").setDataType(DataType.BIGINT).setNotNull(true));
		child.getColumns().add(new Column("CUSTOMER_NAME").setDataType(DataType.VARCHAR).setLength(100));
		child.setPrimaryKey("PK_CUSTOMER_ENTITY", child.getColumns().get("ID"));
		child.getInherits().add(parent);
		schema.getTables().add(child);

		TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.NORMAL);
		String svg = creator.generateSvg(schema.getTables()).getImage();

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		assertEquals("svg", factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg)))
				.getDocumentElement().getLocalName());
		assertTrue(svg.contains("class='relation inherits'"));
		assertTrue(svg.contains("marker-end='url(#inherits)'"));
		assertTrue(svg.contains("refX='12' refY='6' orient='auto'"));
		assertTrue(svg.contains("d='M0,0 L12,6 L0,12 Z'"));
		assertFalse(svg.contains("NaN"));
		assertFalse(svg.contains("Infinity"));

		Path output = Path.of("./build/test-results/inherits.svg");
		Files.createDirectories(output.getParent());
		Files.writeString(output, svg, StandardCharsets.UTF_8);
	}

	@Test
	void columnCellsStayAlignedForDifferentKeyIconCombinations() throws Exception {
		Schema schema = new Schema("PUBLIC");
		Table parent = new Table("PARENT_TABLE");
		parent.getColumns().add(new Column("ID").setDataType(DataType.BIGINT).setNotNull(true));
		parent.getColumns().add(new Column("DESCRIPTION").setDataType(DataType.VARCHAR).setLength(100));
		parent.setPrimaryKey("PK_PARENT_TABLE", parent.getColumns().get("ID"));
		schema.getTables().add(parent);

		Table child = new Table("CHILD_TABLE");
		child.getColumns().add(new Column("ID").setDataType(DataType.BIGINT).setNotNull(true));
		child.getColumns().add(new Column("PLAIN_COLUMN").setDataType(DataType.VARCHAR).setLength(50));
		child.setPrimaryKey("PK_CHILD_TABLE", child.getColumns().get("ID"));
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT", child.getColumns().get("ID"),
				parent.getColumns().get("ID"));
		schema.getTables().add(child);

		TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.NORMAL);
		String svg = creator.generateSvg(schema.getTables()).getImage();

		assertEquals(4, count(svg, "<div class='table-cell key-icons'>"));
		assertEquals(4, count(svg, "<div class='table-cell related-key-icon'>"));
		assertEquals(4, count(svg, "style='grid-template-columns: 32.000000px "));
		assertTrue(svg.contains("<div class='table-cell key-icons'>🔗🔑</div>"));
		assertTrue(svg.contains("<div class='table-cell key-icons'></div>"));
		assertTrue(svg.contains("<div class='table-cell related-key-icon'>🔗</div>"));
		assertFalse(svg.contains("⠀"));

		Path output = Path.of("./build/test-results/column-alignment.svg");
		Files.createDirectories(output.getParent());
		Files.writeString(output, svg, StandardCharsets.UTF_8);
	}

	@Test
	void relationConnectsToCenterOfLowerColumnRow() throws Exception {
		Schema schema = new Schema("PUBLIC");
		Table receipts = new Table("RECEIPTS");
		receipts.getColumns().add(new Column("RECEIPT_ID").setDataType(DataType.BIGINT).setNotNull(true));
		receipts.getColumns().add(new Column("RECEIPT_NO").setDataType(DataType.VARCHAR).setLength(30));
		receipts.getColumns().add(new Column("CUSTOMER_ID").setDataType(DataType.BIGINT));
		receipts.getColumns().add(new Column("RECEIPT_DATE").setDataType(DataType.DATE));
		receipts.getColumns().add(new Column("RECEIVED_AMOUNT").setDataType(DataType.DECIMAL));
		receipts.setPrimaryKey("PK_RECEIPTS", receipts.getColumns().get("RECEIPT_ID"));
		schema.getTables().add(receipts);

		Table allocations = new Table("RECEIPT_ALLOCATIONS");
		allocations.getColumns().add(new Column("RECEIPT_ALLOCATION_ID").setDataType(DataType.BIGINT).setNotNull(true));
		allocations.getColumns().add(new Column("INVOICE_ID").setDataType(DataType.BIGINT));
		allocations.getColumns().add(new Column("RECEIPT_ID").setDataType(DataType.BIGINT).setNotNull(true));
		allocations.getColumns().add(new Column("ALLOCATED_AMOUNT").setDataType(DataType.DECIMAL));
		allocations.setPrimaryKey("PK_RECEIPT_ALLOCATIONS", allocations.getColumns().get("RECEIPT_ALLOCATION_ID"));
		allocations.getConstraints().addForeignKeyConstraint("FK_RECEIPT_ALLOCATIONS_RECEIPT",
				allocations.getColumns().get("RECEIPT_ID"), receipts.getColumns().get("RECEIPT_ID"));
		schema.getTables().add(allocations);

		TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.NORMAL);
		String svg = creator.generateSvg(schema.getTables()).getImage();

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		assertEquals("svg", factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg)))
				.getDocumentElement().getLocalName());
		assertTrue(svg.contains("FK_RECEIPT_ALLOCATIONS_RECEIPT"));
		assertTrue(svg.contains(
				"<marker id=\"many\" markerWidth=\"14\" markerHeight=\"14\" refX=\"2\" refY=\"7\" orient=\"0\">"));
		assertTrue(svg.contains("M13,2  L2,7"));
		assertFalse(svg.contains("NaN"));
		assertFalse(svg.contains("Infinity"));
		double receiptsY = tableY(svg, "RECEIPTS");
		double allocationsY = tableY(svg, "RECEIPT_ALLOCATIONS");
		Matcher relationPath = Pattern
				.compile("<path d='M[^,]+,([0-9.]+).*marker-start='url\\(#one\\)' marker-end='url\\(#many\\)'")
				.matcher(svg);
		assertTrue(relationPath.find());
		double relationY = Double.parseDouble(relationPath.group(1));
		assertEquals(receiptsY + TableSvgCreator.HEADER_HEIGHT + TableSvgCreator.ROW_HEIGHT / 2.0, relationY, 0.001);
		assertEquals(allocationsY + TableSvgCreator.HEADER_HEIGHT + TableSvgCreator.ROW_HEIGHT * 2
				+ TableSvgCreator.ROW_HEIGHT / 2.0, relationY, 0.001);

		Path output = Path.of("./build/test-results/relation-connection-alignment.svg");
		Files.createDirectories(output.getParent());
		Files.writeString(output, svg, StandardCharsets.UTF_8);
	}

	@Test
	void multipleRelationsOnSameColumnUseSeparatePorts() throws Exception {
		Schema schema = new Schema("PUBLIC");
		Table warehouses = parentTable(schema, "WAREHOUSES", "WAREHOUSE_ID");
		Table products = parentTable(schema, "PRODUCTS", "PRODUCT_ID");
		for (String tableName : List.of("INVENTORY_BALANCES", "INVENTORY_TRANSACTIONS", "SHIPMENTS")) {
			Table child = new Table(tableName);
			child.getColumns().add(new Column("ID").setDataType(DataType.BIGINT).setNotNull(true));
			child.getColumns().add(new Column("WAREHOUSE_ID").setDataType(DataType.BIGINT).setNotNull(true));
			child.getColumns().add(new Column("PRODUCT_ID").setDataType(DataType.BIGINT).setNotNull(true));
			child.setPrimaryKey("PK_" + tableName, child.getColumns().get("ID"));
			child.getConstraints().addForeignKeyConstraint("FK_" + tableName + "_WAREHOUSE",
					child.getColumns().get("WAREHOUSE_ID"), warehouses.getColumns().get("WAREHOUSE_ID"));
			child.getConstraints().addForeignKeyConstraint("FK_" + tableName + "_PRODUCT",
					child.getColumns().get("PRODUCT_ID"), products.getColumns().get("PRODUCT_ID"));
			schema.getTables().add(child);
		}

		String svg = new TableSvgCreator(SVGDrawMode.NORMAL).generateSvg(schema.getTables()).getImage();

		Map<Double, Set<Double>> sourcePorts = new HashMap<>();
		Matcher paths = Pattern.compile("<path d='M([0-9.]+),([0-9.]+).*?stroke='#333'").matcher(svg);
		while (paths.find()) {
			sourcePorts.computeIfAbsent(Double.parseDouble(paths.group(1)), k -> new HashSet<>())
					.add(Double.parseDouble(paths.group(2)));
		}

		Path output = Path.of("./build/test-results/multiple-relation-ports.svg");
		Files.createDirectories(output.getParent());
		Files.writeString(output, svg, StandardCharsets.UTF_8);
		assertEquals(6, sourcePorts.values().stream().mapToLong(Set::size).sum());
		assertEquals(6, count(svg, "marker-start='url(#oneCompact)'"));
		assertEquals(6, count(svg, "marker-end='url(#many)'"));
	}

	@Test
	void catalogRelationsResolveReferencedColumnsByName() throws Exception {
		Catalog catalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get("PUBLIC");
		String svg = new TableSvgCreator(SVGDrawMode.NORMAL).generateSvg(schema.getTables()).getImage();

		assertFalse(svg.contains("NaN"));
		assertFalse(svg.contains("Infinity"));
		assertTrue(svg.contains("marker-start='url(#oneCompact)'"));

		Path output = Path.of("./build/test-results/catalog-relation-ports.svg");
		Files.createDirectories(output.getParent());
		Files.writeString(output, svg, StandardCharsets.UTF_8);
	}

	@Test
	void simpleCatalogRelationsConnectToDisplayedColumnRows() throws Exception {
		Catalog catalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get("PUBLIC");
		String svg = new TableSvgCreator(SVGDrawMode.SIMPLE).generateSvg(schema.getTables()).getImage();

		assertFalse(svg.contains("NaN"));
		assertFalse(svg.contains("Infinity"));
		assertTrue(svg.contains("SALES_RETURNS"));

		Path output = Path.of("./build/test-results/simple-catalog-relation-ports.svg");
		Files.createDirectories(output.getParent());
		Files.writeString(output, svg, StandardCharsets.UTF_8);
	}

	@Test
	void simpleMultiSchemaRelationsConnectToDisplayedColumnRows() throws Exception {
		Catalog firstCatalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Catalog secondCatalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema firstSchema = firstCatalog.getSchemas().get("PUBLIC");
		Schema secondSchema = secondCatalog.getSchemas().get("PUBLIC");
		secondSchema.setName("Dummy");
		String svg = new TableSvgCreator(SVGDrawMode.SIMPLE)
				.generateSchemaSvg(List.of(firstSchema, secondSchema)).getImage();

		assertFalse(svg.contains("NaN"));
		assertFalse(svg.contains("Infinity"));
		assertEquals(2, count(svg, ">SALES_RETURNS</div>"));

		Path output = Path.of("./build/test-results/schemas-simple-current.svg");
		Files.createDirectories(output.getParent());
		Files.writeString(output, svg, StandardCharsets.UTF_8);
	}

	private Table parentTable(Schema schema, String tableName, String idColumnName) {
		Table table = new Table(tableName);
		table.getColumns().add(new Column(idColumnName).setDataType(DataType.BIGINT).setNotNull(true));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(100));
		table.setPrimaryKey("PK_" + tableName, table.getColumns().get(idColumnName));
		schema.getTables().add(table);
		return table;
	}

	private double tableY(String svg, String tableName) {
		Matcher matcher = Pattern.compile(
				"<g transform='translate\\([^,]+, ([0-9.]+)\\)'>(?:(?!</g>).)*?<div[^>]*>" + tableName + "</div>",
				Pattern.DOTALL).matcher(svg);
		assertTrue(matcher.find());
		return Double.parseDouble(matcher.group(1));
	}

	private int count(String value, String search) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(search, index)) >= 0) {
			count++;
			index += search.length();
		}
		return count;
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

	private boolean enabled = true;

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
