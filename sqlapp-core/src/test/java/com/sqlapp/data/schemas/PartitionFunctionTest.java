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

public class PartitionFunctionTest extends AbstractDbObjectTest<PartitionFunction> {

	public static PartitionFunction getProcedure(String name) {
		PartitionFunction obj = new PartitionFunction(name);
		obj.setDataTypeName("varchar");
		obj.getValues().add("a");
		obj.getValues().add("b");
		obj.getValues().add("c");
		obj.setRemarks("コメント");
		obj.addDefinition("DDL1行目");
		obj.addDefinition("DDL2行目");
		return obj;
	}

	@Override
	protected PartitionFunction getObject() {
		return getProcedure("PartitionFunctionA");
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<PartitionFunction> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(PartitionFunction obj1, PartitionFunction obj2) {
		obj2.setName("b");
		obj2.setRemarks("コメントB");
		obj2.addDefinition("DDL3行目");
		obj2.getValues().remove("a");
		obj2.getValues().add("d");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
