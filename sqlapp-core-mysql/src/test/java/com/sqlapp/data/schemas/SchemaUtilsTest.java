/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.FileUtils;
import com.sqlapp.util.SeparatedStringBuilder;

public class SchemaUtilsTest {

	protected void testDb() throws XMLStreamException, IOException {
		InputStream stream=FileUtils.getInputStream(this.getClass(), "catalog.xml");
		if (stream==null){
			return;
		}
		Catalog obj1 = SchemaUtils.readXml(this.getClass(), "catalog.xml");
		StringWriter stringWriter = new StringWriter();
		Catalog obj2 = new Catalog();
		obj1.writeXml(stringWriter);
		StringReader stringReader = new StringReader(stringWriter.toString());
		obj2.loadXml(stringReader);
		assertEquals(obj1, obj2);
	}

	@Test
	public void test() throws IOException, XMLStreamException,
			InterruptedException {
	}

	/**
	 * 複数形テスト
	 */
	@Test
	public void testGetPluralName() {
		assertEquals("indexes", SchemaUtils.getPluralName("index"));
		assertEquals("assemblies", SchemaUtils.getPluralName("assembly"));
		assertEquals("tables", SchemaUtils.getPluralName("table"));
	}

	/**
	 * 単数形テスト
	 */
	@Test
	public void testGetSingularName() {
		assertEquals("index", SchemaUtils.getSingularName("indexes"));
		assertEquals("assembly", SchemaUtils.getSingularName("assemblies"));
		assertEquals("table", SchemaUtils.getSingularName("tables"));
	}

}
