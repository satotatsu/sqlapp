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

package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Table;

public abstract class AbstractRowIteratorHandlerTest {

	@Test
	public void testIterator() {
		Table table=getTable();
		initializeTable(table);
		int i=0;
		for(Row row:table.getRows()){
			assertEquals("name"+(i+1), row.get("name"));
			i++;
		}
		assertEquals(count(), i);
		i=0;
		Column column=table.getColumns().get(i++);
		assertEquals("id", column.getName());
		assertEquals(DataType.BIGINT, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("created_at", column.getName());
		assertEquals(DataType.NVARCHAR, column.getDataType());
	}

	private int count(){
		return 23;
	}
	
	protected Table getTable(){
		return new Table();
	}
	
	@Test
	public void testCombinedIterator() {
		Table table=getTable();
		table.setRowIteratorHandler(new CombinedRowIteratorHandler(getRowIteratorHandler(), getRowIteratorHandler()));
		int i=0;
		int count=0;
		for(Row row:table.getRows()){
			assertEquals("name"+(i+1), row.get("name"), "count="+count);
			System.out.println("name"+(i+1)+"="+row.get("name"));
			if (i==22){
				i=0;
			} else{
				i++;
			}
			count++;
		}
		assertEquals(count()*2, count);
	}

	@Test
	public void testHasNext() {
		Table table=getTable();
		initializeTable(table);
		Iterator<Row> itr=table.getRows().iterator();
		Row row=null;
		if(itr.hasNext()){
			row=itr.next();
			assertEquals("name1", row.get("name"), "itr.next()");
		}
		for(int i=0;i<50;i++){
			assertEquals(true, itr.hasNext(), "i="+i);
		}
	}

	
	protected void initializeTable(Table table){
		table.setRowIteratorHandler(getRowIteratorHandler());
	}

	
	protected abstract RowIteratorHandler getRowIteratorHandler();
}
