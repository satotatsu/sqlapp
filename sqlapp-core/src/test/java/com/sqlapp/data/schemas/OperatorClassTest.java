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

public class OperatorClassTest extends
		AbstractDbObjectTest<OperatorClass> {

	public static OperatorClass getOperatorClass(String name) {
		OperatorClass obj = new OperatorClass(name);
		obj.setDataTypeName("varchar");
		obj.getFunctionFamilies().add(
				FunctionFamilyTest.getFunctionFamily("functionA").setSupportNumber(1));
		obj.getFunctionFamilies().add(
				FunctionFamilyTest.getFunctionFamily("functionB").setSupportNumber(2));
		//
		obj.getOperatorFamilies().add(OperatorFamilyTest.getOperatorFamily("operatorA").setStrategyNumber(10));
		obj.getOperatorFamilies().add(OperatorFamilyTest.getOperatorFamily("operatorB").setStrategyNumber(20));
		return obj;
	}

	@Override
	protected OperatorClass getObject() {
		return getOperatorClass("operatorClassA");
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<OperatorClass> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(OperatorClass obj1, OperatorClass obj2) {
		obj2.setDataTypeName("int");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
