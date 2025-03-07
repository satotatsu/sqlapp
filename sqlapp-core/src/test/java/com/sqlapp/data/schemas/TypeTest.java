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

import com.sqlapp.data.db.datatype.DataType;

public class TypeTest extends AbstractDbObjectTest<Type> {

	private static TypeColumn getColumn(String name, DataType types) {
		TypeColumn column = new TypeColumn(name);
		column.setDataType(types);
		return column;
	}
	
	public static Type getType(String name) {
		Type obj = new Type(name);
		TypeColumn column = getColumn("A", DataType.VARCHAR);
		column.setLength(1).setNullable(false).setRemarks("カラムA");
		column.getSpecifics().put("TABLE_SPACE", "TABLE_SPACEA");
		column.getStatistics().put("META_DATA_SIZE", "10");
		column.getValues().add("1");
		column.getValues().add("2");
		column.getValues().add("3");
		column.setRemarks("コメント");
		column.addDefinition("DDL1行目");
		column.addDefinition("DDL2行目");
		//
		obj.getColumns().add(column);
		//
		TypeColumn column1 = getColumn("B", DataType.BIGINT);
		obj.getColumns().add(column1);
		//
		TypeColumn column2 = getColumn("C", DataType.DATETIME);
		obj.getColumns().add(column2);
		//
		TypeColumn column3 = getColumn("D", DataType.BOOLEAN);
		obj.getColumns().add(column3);
		//
		TypeColumn column4 = getColumn("E", DataType.TINYINT);
		obj.getColumns().add(column4);
		return obj;
	}

	@Override
	protected Type getObject() {
		Type obj = getType("DBTYPEA");
		return obj;
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Type> getHandler() {
		return this.getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(Type obj1, Type obj2) {
		TypeColumn column2=obj2.getColumns().get(0);
		column2.setDataType(DataType.NVARCHAR);
		column2.setSequenceName("seqA");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
