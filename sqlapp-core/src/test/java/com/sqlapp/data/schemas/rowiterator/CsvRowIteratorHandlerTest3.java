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

import java.io.File;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Table;

public class CsvRowIteratorHandlerTest3 extends AbstractRowIteratorHandlerTest{

	@Override
	protected RowIteratorHandler getRowIteratorHandler() {
		return new CsvRowIteratorHandler(new File("src/test/resources/testWithoutHeader.csv"), "UTF8", 0);
	}

	@Override
	protected void initializeTable(final Table table){
		initializeTableColumn(table);
		table.setRowIteratorHandler(getRowIteratorHandler());
	}

	protected void initializeTableColumn(final Table table){
		table.getColumns().add(c->{
			c.setName("id");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c->{
			c.setName("created_at");
			c.setDataType(DataType.DATETIME);
		});
		table.getColumns().add(c->{
			c.setName("updated_at");
			c.setDataType(DataType.DATETIME);
		});
		table.getColumns().add(c->{
			c.setName("version_no");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c->{
			c.setName("name");
			c.setDataType(DataType.VARCHAR);
		});
		table.getColumns().add(c->{
			c.setName("description");
			c.setDataType(DataType.VARCHAR);
		});
	}

	@Override
	protected Table getTable(){
		final Table table= new Table();
		initializeTableColumn(table);
		return table;
	}
}
