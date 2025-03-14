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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class InheritsCollectionTest {

	@Test
	public void test() {
		Schema schema1=new Schema("schema1");
		Table table1=new Table("table1");
		schema1.getTables().add(table1);
		Schema schema2=new Schema("schema2");
		Table table2=new Table("table2");
		schema2.getTables().add(table2);
		table1.getInherits().add(table2);
		assertEquals("schema1", table1.getSchemaName());
		assertEquals("schema2", table2.getSchemaName());
	}

}
