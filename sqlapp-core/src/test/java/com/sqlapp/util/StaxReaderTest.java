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

package com.sqlapp.util;

import static com.sqlapp.util.CommonUtils.set;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

public class StaxReaderTest {

	@Test
	public void testReadElement() throws XMLStreamException {
		Table table = new Table();
		Column column = table.newColumn();
		column.setName("A").setLength(1).setNullable(false)
				.setDataType(DataType.BIT).setRemarks("カラムA\n1");
		table.getColumns().add(column);
		//
		column = table.newColumn();
		column.setName("B").setLength(100).setNullable(false)
				.setDataType(DataType.VARCHAR).setRemarks("カラムB\n1");
		table.getColumns().add(column);
		//
		column = table.newColumn();
		column.setName("C").setNullable(false).setDataType(DataType.ENUM)
				.setValues(set("a", "b"));
		table.getColumns().add(column);
		//
		Row row = table.newRow();
		row.put("B", "A2$'\"<>");
		table.getRows().add(row);
		//
		StringWriter writer = new StringWriter();
		StaxWriter stax = new StaxWriter(writer);
		stax.writeStartDocument();
		table.writeXml(stax);
		//
		System.out.println(writer.toString());
		StringReader reader = new StringReader(writer.toString());
		StaxReader staxReader = new StaxReader(reader);
		while (staxReader.hasNext()) {
			int ret = staxReader.next();
			if (staxReader.isCharacters()) {
				System.out.println("value=" + staxReader.getText());
			}
			if (staxReader.isStartElement()) {
				int attrSize = staxReader.getAttributeCount();
				for (int i = 0; i < attrSize; i++) {
					String name = staxReader.getAttributeLocalName(i);
					String val = staxReader.getAttributeValue(i);
					System.out.println(name + "=" + val);
				}
			}
		}
		System.out.println(writer.toString());

	}

}
