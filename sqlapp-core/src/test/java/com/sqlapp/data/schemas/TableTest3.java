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

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.test.AbstractTest;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.xml.ResultHandler;

public class TableTest3 extends AbstractTest{

	@Test
	public void testDiff() throws XMLStreamException,
			UnsupportedEncodingException {
		Table obj1=read("tableDataSource1.xml");
		Table obj2=read("tableDataSource2.xml");
		//
		ColumnEqualsHandler equalsHandler=new ColumnEqualsHandler();
		DbObjectDifference diff1 = obj1.diff(obj2,equalsHandler);
		DbObjectDifference diff2 = obj1.diff(obj2);
		this.testDiffString("table3-1", diff1);
		this.testDiffString("table3-2", diff2);
	}
	
	protected void testDiffString(DbObjectDifference diff) {
		testDiffString(CommonUtils.initCap(this.getClass().getSimpleName().replace("Test", "")), diff);
	}
	
	protected void testDiffString(String resourceName, DbObjectDifference diff) {
		assertEquals(this.getResource(resourceName+".diff"), diff.toString());
	}
	
	protected Table read(String path) throws XMLStreamException{
		StringReader reader = new StringReader(this.getResource(path));
		StaxReader staxReader = new StaxReader(reader);
		TableXmlReaderHandler handler=new TableXmlReaderHandler();
		ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, null);
		List<Table> list = resultHandler.getResult();
		Table table = CommonUtils.first(list);
		return table;
	}

	static class ColumnEqualsHandler extends DefaultSchemaEqualsHandler{

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.schemas.EqualsHandler#equals(java.lang.String,
		 * java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object)
		 */
		@Override
		public boolean valueEquals(String propertyName, Object object1, Object object2,
				Object value1, Object value2, BooleanSupplier p) {
			if (equalsValue(propertyName, value1,value2)){
				return true;
			}
			return super.valueEquals(propertyName, object1, object2, value1, value2, p);
		}
		
		private boolean equalsValue(String propertyName, Object value1, Object value2){
			if (SchemaProperties.DATA_TYPE.getLabel().equals(propertyName)){
				if (value1==DataType.BOOLEAN||value1==DataType.BIT){
					if (value2==DataType.BOOLEAN||value2==DataType.BIT){
						return true;
					}
				}
			}
			if (SchemaProperties.DATA_TYPE_NAME.getLabel().equals(propertyName)){
				return true;
			}
			if (SchemaProperties.CATALOG_NAME.getLabel().equals(propertyName)){
				return true;
			}
			if (SchemaProperties.SCHEMA_NAME.getLabel().equals(propertyName)){
				return true;
			}
			if (SchemaProperties.CHARACTER_SEMANTICS.getLabel().equals(propertyName)){
				return true;
			}
			if (SchemaProperties.TABLE_TYPE.getLabel().equals(propertyName)){
				return true;
			}
			if (SchemaProperties.READONLY.getLabel().equals(propertyName)){
				return true;
			}
			return false;
		}
		
		@Override
		public ColumnEqualsHandler clone(){
			return (ColumnEqualsHandler)super.clone();
		}
	}
}
