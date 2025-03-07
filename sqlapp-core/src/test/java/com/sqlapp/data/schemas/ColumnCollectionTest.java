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

package com.sqlapp.data.schemas;

import static com.sqlapp.util.CommonUtils.first;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.xml.ResultHandler;

public class ColumnCollectionTest {

	@Test
	public void testHandle() throws XMLStreamException {
		Column column = new Column();
		column.setName("A").setLength(1).setNullable(false)
				.setDataType(DataType.BIT).setRemarks("カラムA");
		column.getExtendedProperties().put("INITIAL", "TRUE");
		column.getSpecifics().put("TABLE_SPACE", "TABLE_SPACEA");
		column.getValues().add("1");
		column.getValues().add("2");
		column.getValues().add("3");
		column.setRemarks("コメント");
		column.addDefinition("DDL1行目");
		column.addDefinition("DDL2行目");
		//
		ColumnCollection columns = new ColumnCollection();
		columns.add(column);
		//
		StringWriter writer = new StringWriter();
		StaxWriter stax = new StaxWriter(writer);
		stax.writeStartDocument();
		columns.writeXml(stax);
		//
		StringReader reader = new StringReader(writer.toString());
		StaxReader staxReader = new StaxReader(reader);
		AbstractBaseDbObjectCollectionXmlReaderHandler<?> handler = new ColumnCollection().getDbObjectXmlReaderHandler();
		ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, null);
		List<Object> list = resultHandler.getResult();
		assertEquals(columns, first(list));
		System.out.println(writer.toString());
	}

	@Test
	public void testType() {
		ColumnCollection columns = new ColumnCollection();
		assertEquals(Column.class, columns.getType());
	}
}
