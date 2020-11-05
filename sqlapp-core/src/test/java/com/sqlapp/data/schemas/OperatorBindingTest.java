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

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;

public class OperatorBindingTest extends AbstractDbObjectTest<OperatorBinding>{

	public static OperatorBinding getOperatorBinding(String dbTypeName){
		OperatorBinding operator=new OperatorBinding();
		operator.setDialect(DialectUtils.getInstance(Dialect.class));
		operator.setDataTypeName(dbTypeName);
		OperatorBindingArgument argument=getArgument("BIGINT");
		operator.getArguments().add(argument);
		return operator;
	}

	public static OperatorBindingArgument getArgument(String dbTypeName){
		OperatorBindingArgument argument=new OperatorBindingArgument();
		argument.setDataTypeName(dbTypeName);
		return argument;
	}

	@Override
	protected OperatorBinding getObject() {
		return getOperatorBinding("VARCHAR");
	}

	@Override
	protected AbstractBaseDbObjectXmlReaderHandler<OperatorBinding> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(OperatorBinding obj1, OperatorBinding obj2) {
		obj2.setDataTypeName("CHAR");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
