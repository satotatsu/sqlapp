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

package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
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
		final InputStream stream=FileUtils.getInputStream(this.getClass(), "catalog.xml");
		if (stream==null){
			return;
		}
		final Catalog obj1 = SchemaUtils.readXml(this.getClass(), "catalog.xml");
		final StringWriter stringWriter = new StringWriter();
		final Catalog obj2 = new Catalog();
		obj1.writeXml(stringWriter);
		final StringReader stringReader = new StringReader(stringWriter.toString());
		obj2.loadXml(stringReader);
		assertEquals(obj1, obj2);
	}

	@Test
	public void test() throws FileNotFoundException, XMLStreamException,
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
		assertEquals("schemas", SchemaUtils.getPluralName("schemas"));
	}

	/**
	 * 単数形テスト
	 */
	@Test
	public void testGetSingularName() {
		assertEquals("index", SchemaUtils.getSingularName("indexes"));
		assertEquals("assembly", SchemaUtils.getSingularName("assemblies"));
		assertEquals("table", SchemaUtils.getSingularName("tables"));
		assertEquals("schema", SchemaUtils.getSingularName("schemas"));
	}

	/**
	 * getDroppableClassesテスト
	 */
	@Test
	public void testGetDroppableClasses() {
		System.out
		.println("*****************************************************");
		final Set<Class<?>> clazzes=SchemaUtils.getDroppableClasses();
		clazzes.forEach(c->{
			System.out.println(c);
		});
	}

	/**
	 * クラス取得テスト
	 */
	@Test
	public void testgetSubClasses1() {
		final Set<Class<?>> classes = SchemaUtils.getNamedObjectClasses();
		final SeparatedStringBuilder builder = new SeparatedStringBuilder("\n ,");
		builder.setOpenQuate("\"").setCloseQuate("\"");
		System.out
				.println("*****************************************************");
		for (final Class<?> clazz : classes) {
			builder.add(clazz.getSimpleName());
		}
		System.out
				.println("*****************************************************");
		System.out.println(classes);
		System.out.println(builder.toString());
		assertTrue(classes.size()>=57, "classes.size()="+classes.size());
	}

	/**
	 * クラス取得テスト
	 */
	@Test
	public void testgetSubClasses2() {
		final Set<Class<?>> classes = SchemaUtils.getSchemaObjectClasses();
		final SeparatedStringBuilder builder = new SeparatedStringBuilder("\n ,");
		builder.setOpenQuate("\"").setCloseQuate("\"");
		System.out
				.println("*****************************************************");
		for (final Class<?> clazz : classes) {
			builder.add(clazz.getSimpleName());
		}
		System.out
				.println("*****************************************************");
		System.out.println(classes);
		System.out.println(builder.toString());
		assertTrue(classes.size()>=40, "classes.size()="+classes.size());
	}
	
	@Test
	public void testgetProductInfo() throws XMLStreamException {
		final Catalog cc = new Catalog();
		cc.setCharacterSet("utf8");
		cc.setCharacterSemantics(CharacterSemantics.Char);
		cc.setCollation("utf8_bin");
		cc.setProductName("mysql");
		cc.setProductMajorVersion(5);
		cc.setProductMinorVersion(6);
		cc.setProductRevision(7);
		assertEquals("mysql 5.6.7", SchemaUtils.getProductInfo(cc));
	}
	
	@Test
	public void testCreateInstance() throws XMLStreamException {
		final Object obj=SchemaUtils.createInstance("settings");
		assertTrue(obj instanceof SettingCollection);
	}

	@Test
	public void testNewInstanceAtSchemas() throws XMLStreamException {
		final SettingCollection obj=SchemaUtils.newInstanceAtSchemas(SettingCollection.class);
		assertTrue(obj instanceof SettingCollection);
	}

}
