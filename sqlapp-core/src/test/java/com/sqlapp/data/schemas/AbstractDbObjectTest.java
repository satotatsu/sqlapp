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

import static com.sqlapp.util.CommonUtils.first;
import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.test.AbstractTest;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.xml.ResultHandler;

public abstract class AbstractDbObjectTest<T extends AbstractBaseDbObject<? super T>> extends AbstractTest{

	@Test
	public void testXml() throws XMLStreamException,
			UnsupportedEncodingException {
		T obj = getObject();
		//
		StringWriter writer = new StringWriter();
		StaxWriter stax = new StaxWriter(writer);
		stax.writeStartDocument();
		obj.writeXml(stax);
		//
		System.out.println(writer.toString());
		StringReader reader = new StringReader(writer.toString());
		StaxReader staxReader = new StaxReader(reader);
		AbstractBaseDbObjectXmlReaderHandler<?> handler = getHandler();
		ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, null);
		List<Object> list = resultHandler.getResult();
		Object readObj = first(list);
		assertTrue(obj.equals(readObj,new TestEqualsHansler()));
		System.out.println(writer.toString());
	}

	protected abstract T getObject();

	protected AbstractBaseDbObjectXmlReaderHandler<?> getHandler() {
		T obj=this.getObject();
		if (obj instanceof AbstractBaseDbObject){
			return ((AbstractBaseDbObject<?>)obj).getDbObjectXmlReaderHandler();
		}
		return null;
	}
	
	@Test
	public void testDiff() throws XMLStreamException,
			UnsupportedEncodingException {
		Timestamp created = toTimestamp("2011-01-02 10:20:11");
		Timestamp lastAltered = toTimestamp("2011-01-02 10:20:35");
		T obj1 = getObject();
		obj1.setCreatedAt(created);
		obj1.setLastAlteredAt(lastAltered);
		T obj2 = getObject();
		obj2.setCreatedAt(created);
		obj2.setLastAlteredAt(lastAltered);
		DbObjectDifference diff = obj1.diff(obj2,new TestEqualsHansler());
		assertEquals("", diff.toString());
		testDiffString(obj1, obj2);
	}

	@Test
	public void testClone() throws XMLStreamException,
			UnsupportedEncodingException {
		T obj = getObject();
		Object obj2 = obj.clone();
		assertTrue(obj.equals(obj2,new TestEqualsHansler()), "obj.equals(obj2,new TestEqualsHansler())");
		Set<ISchemaProperty> props=SchemaUtils.getSchemaObjectProperties(obj.getClass());
		for(ISchemaProperty prop:props){
			Object value1=prop.getValue(obj);
			Object value2=prop.getValue(obj2);
			if (value1==null&&value2==null){
				
			} else{
				assertFalse(value1==value2, "prop(==)="+prop);
				if (value1.getClass().isArray()&&value2.getClass().isArray()){
					assertArrayEquals((Object[])value1, (Object[])value2, "prop(equals)="+prop);
				} else{
					assertEquals(value1, value2, "prop(equals)="+prop);
				}
			}
		}
	}
	
	protected abstract void testDiffString(T obj1, T obj2);

	protected static Timestamp toTimestamp(String text) {
		return Timestamp.valueOf(text);
	}
	
	protected void testDiffString(DbObjectDifference diff) {
		testDiffString(CommonUtils.initCap(this.getClass().getSimpleName().replace("Test", "")), diff);
	}
	
	protected void testDiffString(String resourceName, DbObjectDifference diff) {
		assertEquals(this.getResource(resourceName+".diff"), diff.toString());
	}
}
