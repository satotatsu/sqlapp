/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 *
 * sqlapp-core-mariadb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mariadb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mariadb.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.SeparatedStringBuilder;

public class SchemaUtilsTest {

	protected void testDb() throws XMLStreamException, IOException {
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

	/**
	 * クラス取得テスト
	 */
	@Test
	public void testgetSubClasses1() {
		Set<Class<?>> classes = SchemaUtils.getNamedObjectClasses();
		SeparatedStringBuilder builder = new SeparatedStringBuilder("\n ,");
		builder.setOpenQuate("\"").setCloseQuate("\"");
		System.out
				.println("*****************************************************");
		for (Class<?> clazz : classes) {
			builder.add(clazz.getSimpleName());
		}
		System.out
				.println("*****************************************************");
		System.out.println(classes);
		System.out.println(builder.toString());
		assertEquals(49, classes.size());
	}

	/**
	 * クラス取得テスト
	 */
	@Test
	public void testgetSubClasses2() {
		Set<Class<?>> classes = SchemaUtils.getSchemaObjectClasses();
		SeparatedStringBuilder builder = new SeparatedStringBuilder("\n ,");
		builder.setOpenQuate("\"").setCloseQuate("\"");
		System.out
				.println("*****************************************************");
		for (Class<?> clazz : classes) {
			builder.add(clazz.getSimpleName());
		}
		System.out
				.println("*****************************************************");
		System.out.println(classes);
		System.out.println(builder.toString());
		assertEquals(35, classes.size());
	}
}
