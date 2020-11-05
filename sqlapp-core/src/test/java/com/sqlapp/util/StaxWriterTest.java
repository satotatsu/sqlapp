/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.util;

import java.io.StringWriter;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

public class StaxWriterTest {

	@Test
	public void testStaxWriterWriter() throws XMLStreamException {
		StringWriter writer = new StringWriter();
		StaxWriter stax = new StaxWriter(writer);
		stax.setLineSeparator("\n").writeStartDocument()
				.writeStartElement("html").newLine().addIndentLevel(+1)
				.indent().writeStartElement("body").newLine()
				.addIndentLevel(+1).indent().writeStartElement("span")
				.writeAttribute("id", "spanId").writeEndElement().newLine()
				.indent().writeStartElement("div").writeEmptyElement("text")
				.writeEndElement().newLine().addIndentLevel(-1).indent()
				.writeEndElement().newLine().writeEndElement()
				.writeEndDocument();
		System.out.println(writer.toString());
	}

}
