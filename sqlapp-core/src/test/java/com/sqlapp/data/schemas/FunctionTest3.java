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
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
public class FunctionTest3  {

	public static Function getFunction(String name) {
		Function obj = new Function(name);
		NamedArgument arg = new NamedArgument("arg1");
		obj.setOnNullCall(OnNullCall.CalledOnNullInput);
		obj.setSqlSecurity(SqlSecurity.Definer);
		obj.getArguments().add(arg);
		obj.getReturning().toTable().getTable().getColumns().add(c->{
			c.setName("colA");
		});
		obj.setRemarks("コメント");
		return obj;
	}

	@Test
	public void testSpecificName() {
		Schema schema=new Schema();
		Function func=getFunction("func1");
		func.setSpecificName("func1_specific1");
		schema.getFunctions().add(func);
		func=getFunction("func1");
		func.setSpecificName("func1_specific2");
		schema.getFunctions().add(func);
		assertEquals(2, schema.getFunctions().size());
	}

}
