/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.html;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Mask;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableSpace;

public class GenerateAllHtmlCommandTest extends AbstractGenerateHtmlCommandTest{
	
	@Override
	protected Catalog createCatalog(){
		final Catalog catalog=super.createCatalog();
		final TableSpace tableSpace=createTableSpace("tableSpaceA");
		catalog.getTableSpaces().add(tableSpace);
		return catalog;
	}

	protected TableSpace createTableSpace(final String name){
		final TableSpace obj=new TableSpace(name);
		setValues(obj);
		return obj;
	}

	@Override
	protected Schema createSchema(){
		final Schema schema=super.createSchema();
		final Table table=createTable("tableA");
		schema.getTables().add(table);
		final Domain domain=createDomain("DomainA");
		schema.getDomains().add(domain);
		final Mask mask=createMask("MaskA");
		schema.getMasks().add(mask);
		return schema;
	}

	protected Table createTable(final String name){
		final Table table=new Table(name);
		final Column column=createColumn("cola");
		table.getColumns().add(column);
		setValues(table);
		table.toPartitioning();
		return table;
	}

	protected Domain createDomain(final String name){
		final Domain obj=new Domain(name);
		setValues(obj);
		return obj;
	}

	protected Mask createMask(final String name){
		final Mask obj=new Mask(name);
		setValues(obj);
		return obj;
	}

	
	protected Column createColumn(final String name){
		final Column obj=new Column(name);
		setValues(obj);
		return obj;
	}

}
