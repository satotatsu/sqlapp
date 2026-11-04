/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util.xml;

import java.io.StringWriter;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.StaxWriter;

public class SimpleXMLStreamWriterTest {
	@Test
	public void preservesSupplementaryCharactersInTextAndCdata() throws Exception {
		final String value = "𠮷-start\n\t<& end-😀";
		for (boolean cdata : java.util.List.of(false, true)) {
			final var output = new StringWriter();
			final var writer = new SimpleXMLStreamWriter(output);
			writer.writeStartElement("test");
			if (cdata) writer.writeCData(value);
			else writer.writeCharacters(value);
			writer.writeEndElement();
			writer.flush();
			final var reader = javax.xml.stream.XMLInputFactory.newFactory()
					.createXMLStreamReader(new java.io.StringReader(output.toString()));
			try {
				reader.nextTag();
				org.junit.jupiter.api.Assertions.assertEquals(value, reader.getElementText());
			} finally { reader.close(); }
		}
	}
	@Test
	public void preservesAttributeWhitespaceAndSupplementaryCharacters() throws Exception {
		final String value = "𠮷<&\"\tfirst\r\nsecond\nthird\rfourth";
		final var output = new StringWriter();
		final var writer = new SimpleXMLStreamWriter(output);
		writer.writeStartElement("test");
		writer.writeAttribute("value", value);
		writer.writeEndElement();
		writer.flush();
		final var reader = javax.xml.stream.XMLInputFactory.newFactory()
				.createXMLStreamReader(new java.io.StringReader(output.toString()));
		try {
			reader.nextTag();
			org.junit.jupiter.api.Assertions.assertEquals(value, reader.getAttributeValue(null, "value"));
		} finally {
			reader.close();
		}
	}

	@Test
	public void testStaxWriterWriter() throws XMLStreamException {
		StringWriter writer = new StringWriter();
		SimpleXMLStreamWriter simpleXMLStreamWriter = new SimpleXMLStreamWriter(writer);
		StaxWriter stax = new StaxWriter(simpleXMLStreamWriter);
		stax.setLineSeparator("\n").writeStartDocument().writeStartElement("html").newLine().addIndentLevel(+1).indent()
				.writeStartElement("body").newLine().addIndentLevel(+1).indent().writeStartElement("span")
				.writeAttribute("id", "spanId").writeEndElement().newLine().indent().writeStartElement("div")
				.writeEmptyElement("text").writeEndElement().newLine().indent().writeStartElement("div")
				.writeEndElement().newLine().indent().writeStartElement("table").writeStartElement("tr")
				.writeStartElement("td").writeEndElement().writeEndElement().writeEndElement().addIndentLevel(-1)
				.newLine().indent().writeEndElement().addIndentLevel(-1).newLine().indent().writeEndElement()
				.writeEndDocument();
		System.out.println(writer.toString());
	}

}
