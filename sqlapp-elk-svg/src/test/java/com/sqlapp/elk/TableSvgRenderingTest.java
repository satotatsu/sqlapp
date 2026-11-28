/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-elk-svg.
 *
 * sqlapp-elk-svg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.sqlapp.elk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.elk.util.EdgeUtils;

class TableSvgRenderingTest {

	private static final String SVG = "http://www.w3.org/2000/svg";
	private static final String XHTML = "http://www.w3.org/1999/xhtml";

	@Test
	void escapesNamesColumnDetailsAndLinksWithoutChangingText() throws Exception {
		for (SVGDrawMode mode : SVGDrawMode.values()) {
			for (NameMode names : NameMode.values()) {
				Table table = table("T<&\"'", "C<&\"'");
				table.setDisplayName("論理<&\"'");
				table.getColumns().get(0).setDisplayName("列<&\"'");
				table.getColumns().get(0).setCheck("ID < 10 AND ID > 0");
				table.getColumns().get(0).setDefaultValue("'a&b'");
				TableSvgCreator creator = new TableSvgCreator(mode, names);
				String url = "table.html?a='x'&b=\"y\"";
				creator.setUrlFunction(t -> url);
				Document document = parse(creator.generateSvg(List.of(table)).getImage());
				assertEquals(url, ((Element) document.getElementsByTagNameNS(XHTML, "a").item(0)).getAttribute("href"));
				String text = document.getDocumentElement().getTextContent();
				assertTrue(text.contains(names.getName(table)));
				assertTrue(text.contains(names.getName(table.getColumns().get(0))));
				if (mode == SVGDrawMode.NORMAL) {
					assertTrue(text.contains("ID < 10 AND ID > 0"));
					assertTrue(text.contains("'a&b'"));
				}
			}
		}
	}

	@Test
	void schemaNamesAreEscapedAndNeverUsedAsFormatStrings() throws Exception {
		Schema schema = new Schema("S%NAME<&\"'");
		schema.getTables().add(table("T", "ID"));
		Document document = parse(new TableSvgCreator().generateSchemaSvg(List.of(schema)).getImage());
		assertEquals(schema.getName(),
				((Element) document.getElementsByTagNameNS(SVG, "g").item(0)).getAttribute("id"));
		assertEquals(schema.getName(), document.getElementsByTagNameNS(SVG, "text").item(0).getTextContent());
	}

	@Test
	void numericOutputIsIndependentOfFormattingLocale() throws Exception {
		Locale original = Locale.getDefault(Locale.Category.FORMAT);
		try {
			Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
			for (SVGDrawMode mode : SVGDrawMode.values()) {
				Schema a = new Schema("A");
				Schema b = new Schema("B");
				Table parent = table("PARENT", "ID");
				Table child = table("CHILD", "ID");
				Table other = table("OTHER", "ID");
				a.getTables().add(parent);
				a.getTables().add(child);
				b.getTables().add(other);
				child.getConstraints().addForeignKeyConstraint("FK_LOCAL", child.getColumns().get(0),
						parent.getColumns().get(0));
				other.getConstraints().addForeignKeyConstraint("FK_CROSS", other.getColumns().get(0),
						parent.getColumns().get(0));
				TableSvgCreator creator = new TableSvgCreator(mode);
				for (String image : List.of(creator.generateSvg(List.of(parent, child)).getImage(),
						creator.generateSchemaSvg(List.of(a, b)).getImage())) {
					Document document = parse(image);
					var elements = document.getElementsByTagName("*");
					for (int i = 0; i < elements.getLength(); i++) {
						Element element = (Element) elements.item(i);
						for (String attribute : List.of("x", "y", "width", "height")) {
							if (element.hasAttribute(attribute)) {
								assertTrue(Double.isFinite(Double.parseDouble(element.getAttribute(attribute))));
							}
						}
						if (element.hasAttribute("d")) {
							assertTrue(element.getAttribute("d").matches("[MLZmlz0-9.,\\s-]+"));
							assertFalse(element.getAttribute("d").matches("(?s).*\\d,\\d+,\\d.*"));
						}
						if (element.hasAttribute("style")) {
							assertFalse(element.getAttribute("style").matches("(?s).*\\d,\\d+px.*"));
						}
						if (element.getLocalName().equals("tspan")) {
							assertTrue(element.getAttribute("dy").matches("[0-9]+(?:\\.[0-9]+)?em"));
						}
					}
				}
			}
		} finally {
			Locale.setDefault(Locale.Category.FORMAT, original);
		}
	}

	@Test
	void disablingFilterKeepsRowCountHeightAndEdgePositionsConsistent() throws Exception {
		Table table = table("T", "FIRST");
		table.getColumns().add(new Column("SECOND").setDataType(DataType.INT));
		TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.SIMPLE);
		creator.setTableNodeConsumer(node -> {
			node.setColumnFilter(c -> false);
			node.setColumnFilterEnabled(false);
			assertEquals(2, node.getColumnSize());
			assertEquals(List.copyOf(table.getColumns()), node.getColumns());
			assertEquals(TableSvgCreator.HEADER_HEIGHT + TableSvgCreator.ROW_HEIGHT * 1.5,
					EdgeUtils.calulucateY(node, List.of(table.getColumns().get(1))));
		});
		Document document = parse(creator.generateSvg(List.of(table)).getImage());
		Element body = (Element) document.getElementsByTagNameNS(SVG, "foreignObject").item(0);
		assertTrue(Double.parseDouble(body.getAttribute("height")) >= TableSvgCreator.HEADER_HEIGHT
				+ 2 * TableSvgCreator.ROW_HEIGHT);
		assertTrue(body.getTextContent().contains("FIRST"));
		assertTrue(body.getTextContent().contains("SECOND"));
	}

	@Test
	void canvasContainsSchemaBackgroundsIncludingEmptySchemas() throws Exception {
		for (double padding : List.of(20.0, 30.0, 60.0)) {
			for (boolean populated : List.of(false, true)) {
				Schema schema = new Schema("S");
				if (populated) {
					schema.getTables().add(table("T", "ID"));
				}
				TableSvgCreator creator = new TableSvgCreator();
				creator.setPadding(padding);
				var result = creator.generateSchemaSvg(List.of(schema, new Schema("EMPTY")));
				Document document = parse(result.getImage());
				var groups = document.getElementsByTagNameNS(SVG, "g");
				for (int i = 0; i < groups.getLength(); i++) {
					Element group = (Element) groups.item(i);
					if (!group.hasAttribute("id")) {
						continue;
					}
					String[] offset = group.getAttribute("transform").replace("translate(", "").replace(")", "")
							.split(",");
					Element rect = (Element) group.getElementsByTagNameNS(SVG, "rect").item(0);
					assertTrue(Double.parseDouble(offset[0]) + Double.parseDouble(rect.getAttribute("width")) < result
							.getTotalWidth());
					assertTrue(Double.parseDouble(offset[1]) + Double.parseDouble(rect.getAttribute("height")) < result
							.getTotalHeight());
				}
			}
		}
	}

	@Test
	void schemaHeadersFollowNameModeInBothDrawingModes() throws Exception {
		Schema schema = new Schema("PHYSICAL");
		schema.setDisplayName("論理<&");
		schema.getTables().add(table("T", "ID"));
		for (SVGDrawMode mode : SVGDrawMode.values()) {
			for (NameMode names : NameMode.values()) {
				Document document = parse(
						new TableSvgCreator(mode, names).generateSchemaSvg(List.of(schema)).getImage());
				assertEquals(names.getName(schema),
						document.getElementsByTagNameNS(SVG, "text").item(0).getTextContent());
			}
		}
	}

	private Table table(String name, String columnName) {
		Table table = new Table(name);
		table.getColumns().add(new Column(columnName).setDataType(DataType.INT));
		return table;
	}

	private Document parse(String image) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(image)));
	}
}
