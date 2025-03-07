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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.StaxWriter;

public class ColumnTest extends AbstractDbObjectTest<Column> {

	@Override
	protected Column getObject() {
		final Column column = new Column();
		column.setName("A").setLength(1).setNullable(false).setDataType(DataType.BIT);
		column.getExtendedProperties().put("INITIAL", "TRUE");
		column.getSpecifics().put("TABLE_SPACE", "TABLE_SPACEA");
		column.getSpecifics().put("DUMMY", "DUMMYA");
		column.getValues().add("1");
		column.getValues().add("2");
		column.getValues().add("3");
		column.setSchemaName("schemaName1");
		column.setCollation("utf8-general-ci");
		column.setMaskingFunction("default()");
		column.setCharacterSemantics(CharacterSemantics.Char);
		column.setRemarks("comment");
		column.addDefinition("DDL1行目");
		column.addDefinition("DDL2行目");
		column.getValues().add("");
		column.getValues().add("b");
		column.getValues().add("c");
		return column;
	}

	@Test
	public void testHandle2() throws XMLStreamException, UnsupportedEncodingException {
		final Column obj = getObject();
		//
		final StringWriter writer = new StringWriter();
		final StaxWriter stax = new StaxWriter(writer);
		stax.writeStartDocument();
		obj.writeXml(stax);
		//
		final StringReader reader = new StringReader(writer.toString());
		final Column obj2 = new Column();
		obj2.loadXml(reader);
		assertEquals(obj, obj2);
	}

	@Test
	public void testReadXml() throws XMLStreamException, IOException {
		final Column obj = SchemaUtils.readXml(this.getClass(), "column.xml");
		assertEquals(obj.getValues().size(), 6);
		final String[] array = obj.getValues().toArray(new String[0]);
		int i = 0;
		assertEquals(array[i++], "1");
		assertEquals(array[i++], "2");
		assertEquals(array[i++], "3");
		assertEquals(array[i++], "");
		assertEquals(array[i++], "b");
		assertEquals(array[i++], "c");
	}

	@Override
	protected void testDiffString(final Column obj1, final Column obj2) {
		obj2.setName("b");
		obj2.setRemarks("コメントB");
		obj2.getSpecifics().put("DUMMY", "DUMMYB");
		obj2.getSpecifics().put("TABLE_SPACE", "TABLE_SPACEB");
		final DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
