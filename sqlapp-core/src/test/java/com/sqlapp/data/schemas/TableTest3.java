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

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractTest;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.xml.ResultHandler;

public class TableTest3 extends AbstractTest {

	@Test
	public void testDiff() throws XMLStreamException, UnsupportedEncodingException {
		final Table obj1 = read("tableDataSource1.xml");
		final Table obj2 = read("tableDataSource2.xml");
		//
		final ColumnEqualsHandler equalsHandler = new ColumnEqualsHandler();
		final DbObjectDifference diff1 = obj1.diff(obj2, equalsHandler);
		final DbObjectDifference diff2 = obj1.diff(obj2);
		this.testDiffString("table3-1", diff1);
		this.testDiffString("table3-2", diff2);
	}

	protected void testDiffString(final DbObjectDifference diff) {
		testDiffString(CommonUtils.initCap(this.getClass().getSimpleName().replace("Test", "")), diff);
	}

	protected void testDiffString(final String resourceName, final DbObjectDifference diff) {
		assertEquals(FileUtils.getResource(this, resourceName + ".diff"), diff.toString());
	}

	protected Table read(final String path) throws XMLStreamException {
		final StringReader reader = new StringReader(FileUtils.getResource(this, path));
		final StaxReader staxReader = new StaxReader(reader);
		final TableXmlReaderHandler handler = new TableXmlReaderHandler();
		final ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, null);
		final List<Table> list = resultHandler.getResult();
		final Table table = CommonUtils.first(list);
		return table;
	}

	static class ColumnEqualsHandler extends DefaultSchemaEqualsHandler {

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.schemas.EqualsHandler#equals(java.lang.String,
		 * java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object)
		 */
		@Override
		public boolean valueEquals(final String propertyName, final Object object1, final Object object2,
				final Object value1, final Object value2, final BooleanSupplier p) {
			if (equalsValue(propertyName, value1, value2)) {
				return true;
			}
			return super.valueEquals(propertyName, object1, object2, value1, value2, p);
		}

		private boolean equalsValue(final String propertyName, final Object value1, final Object value2) {
			if (SchemaProperties.DATA_TYPE.getLabel().equals(propertyName)) {
				if (value1 == DataType.BOOLEAN || value1 == DataType.BIT) {
					if (value2 == DataType.BOOLEAN || value2 == DataType.BIT) {
						return true;
					}
				}
			}
			if (SchemaProperties.DATA_TYPE_NAME.getLabel().equals(propertyName)) {
				return true;
			}
			if (SchemaProperties.CATALOG_NAME.getLabel().equals(propertyName)) {
				return true;
			}
			if (SchemaProperties.SCHEMA_NAME.getLabel().equals(propertyName)) {
				return true;
			}
			if (SchemaProperties.CHARACTER_SEMANTICS.getLabel().equals(propertyName)) {
				return true;
			}
			if (SchemaProperties.TABLE_TYPE.getLabel().equals(propertyName)) {
				return true;
			}
			if (SchemaProperties.READONLY.getLabel().equals(propertyName)) {
				return true;
			}
			return false;
		}

		@Override
		public ColumnEqualsHandler clone() {
			return (ColumnEqualsHandler) super.clone();
		}
	}
}
