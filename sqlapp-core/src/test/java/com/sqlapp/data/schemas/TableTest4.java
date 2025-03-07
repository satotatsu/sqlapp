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

public class TableTest4 extends AbstractDbObjectTest<Table> {

	public static Table getTable(String tableName) {
		Table tableParent = TableTest.getTable(tableName+"Parent");
		tableParent.setSchemaName("schema1");
		tableParent.toPartitioning();
		tableParent.getPartitioning().setPartitioningType(PartitioningType.Range);
		Table table = TableTest.getTable(tableName+1);
		table.setPartitionParent(tableParent, "1", "2");
		return table;
	}

	@Override
	protected Table getObject() {
		Table table = getTable("TableA");
		return table;
	}

	@Override
	protected TableXmlReaderHandler getHandler() {
		return new TableXmlReaderHandler();
	}

	@Override
	protected void testDiffString(Table obj1, Table obj2) {
		obj2.getPartitionParent().setHighValue("3");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
