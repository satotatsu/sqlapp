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

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;

public class OperatorTest extends AbstractDbObjectTest<Operator>{

	public static Operator getOperator(String dbTypeName){
		Operator operator=new Operator();
		operator.setDialect(DialectUtils.getInstance(Dialect.class));
		operator.setFunctionName("funcA");
		operator.setLeftArgument(new OperatorArgument("BOX"));
		operator.setRightArgument(new OperatorArgument("BOX2"));
		operator.setHashes(true);
		operator.setMerges(true);
		operator.getBindings().add(OperatorBindingTest.getOperatorBinding("VARCHAR"));
		return operator;
	}

	public static OperatorArgument getArgument(String dbTypeName){
		OperatorArgument argument=new OperatorArgument();
		argument.setDataTypeName(dbTypeName);
		return argument;
	}

	@Override
	protected Operator getObject() {
		return getOperator("varchar");
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Operator> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(Operator obj1, Operator obj2) {
		obj2.setFunctionName("funcB");
		obj2.setHashes(false);
		obj2.getLeftArgument().setDataTypeName("int");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
	
	@Test
	public void test1() throws XMLStreamException {
		Operator operator=new Operator("===");
		operator.setDialect(DialectUtils.getInstance(Dialect.class));
		operator.setLeftArgument(new OperatorArgument("BOX"));
		assertEquals("===(BOX,)", operator.getSpecificName());
		operator.setRightArgument(new OperatorArgument("BOX2"));
		assertEquals("===(BOX,BOX2)", operator.getSpecificName());
	}

	@Test
	public void test2() throws XMLStreamException {
		Operator operator=new Operator("contains");
		operator.setDialect(DialectUtils.getInstance(Dialect.class));
		assertEquals("contains", operator.getSpecificName());
	}
}
