/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.schemas.postgres;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.SchemaUtils;

public class SchemaUtilsTest extends com.sqlapp.data.schemas.SchemaUtilsTest {

	@Test
	public void test() throws XMLStreamException, IOException {
		testDb();
	}
	
	@Test
	public void testDb() throws XMLStreamException, IOException {
		Catalog obj1 = SchemaUtils.readXml(this.getClass(), "Catalog.xml");
		StringWriter stringWriter = new StringWriter();
		Catalog obj2 = new Catalog();
		obj1.writeXml(stringWriter);
		StringReader stringReader = new StringReader(stringWriter.toString());
		obj2.loadXml(stringReader);
		obj2.getSchemas().get("pg_catalog").getFunctions().remove("record_recv(tsquery,BOOL,int4)");
		DbObjectDifference diff=obj1.diff(obj2);
		System.out.println(diff.toString());
	}
}
