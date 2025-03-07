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


public class ExternalTableTest extends AbstractDbObjectTest<ExternalTable>{

	public static ExternalTable getExternalTable(String name){
		ExternalTable obj=new ExternalTable(name);
		obj.setTypeName("ORACLE_LOADER");
		obj.setSchemaName("SYS");
		obj.setLocation("sample.csv");
		obj.setDefaultDirectoryName("EXTERNAL_DATA");
		obj.setDirectoryName("EXTERNAL_DATA");
		obj.setRejectLimit("0");
		obj.setProperty("ALL");
		obj.setAccessParameters("RECORDS DELIMITED BY NEWLINE \n FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"' (KAZU, MOJI, HIZUKE CHAR DATE_FORMAT DATE MASK 'yyyy/mm/dd') \n");
		obj.setAccessType("CLOB");
		obj.setRemarks("コメント");
		return obj;
	}

	@Override
	protected ExternalTable getObject() {
		return getExternalTable("ExternalTableA");
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<ExternalTable> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(ExternalTable obj1, ExternalTable obj2) {
		obj2.setName("b");
		obj2.setRemarks("コメントB");
		obj2.setAccessType("BLOB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
