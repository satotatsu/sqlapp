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

import static com.sqlapp.util.CommonUtils.newTimestamp;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class IndexTest2{

	@Test
	public void testReference() {
		Catalog catalog=new Catalog();
		Schema schema=new Schema("schema1");
		catalog.getSchemas().add(schema);
		//
		Table table=new Table("table1");
		Column column1=new Column("cola");
		table.getColumns().add(column1);
		Column column2=new Column("colb");
		table.getColumns().add(column2);
		table.setTableSpaceName("tableSpaceA");
		schema.getTables().add(table);
		//
		assertEquals("tableSpaceA", table.getTableSpaceName());
		assertEquals("tableSpaceA", table.getTableSpace().getName());
		//
		Index index=new Index("indexA");
		index.getColumns().add("colA", Order.Asc);
		index.getColumns().add("colB", Order.Desc);
		index.setRemarks("インデックスコメント");
		index.setUnique(true);
		index.setCompression(true);
		index.setTableSpaceName("tableSpaceA");
		index.setCreatedAt(newTimestamp());
		index.setTableSpaceName("tableSpaceA");
		//
		table.getIndexes().add(index);
		assertNotNull(index.getTableSpace());
		//
		assertEquals("tableSpaceA", index.getTableSpaceName());
		assertEquals("tableSpaceA", index.getTableSpace().getName());
		//
		TableSpace tableSpace2=new TableSpace("tableSpaceB");
		catalog.getTableSpaces().add(tableSpace2);
		//
		assertEquals("tableSpaceA", index.getTableSpaceName());
		assertEquals("tableSpaceA", index.getTableSpace().getName());
		//
		TableSpace tableSpace1=new TableSpace("tableSpaceA");
		assertTrue(tableSpace1!=table.getTableSpace());
		assertTrue(tableSpace1!=index.getTableSpace());
		//
		catalog.getTableSpaces().add(tableSpace1);
		assertTrue(tableSpace1==table.getTableSpace());
		assertTrue(tableSpace1==index.getTableSpace());
	}

}
