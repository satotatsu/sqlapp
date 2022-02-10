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

package com.sqlapp.data.db.metadata;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.TableSpaceFile;
import com.sqlapp.data.schemas.TableSpaceFileCollection;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.TableSpace;

public abstract class TableSpaceFileReader extends AbstractNamedMetadataReader<TableSpaceFile, TableSpace>{

	protected TableSpaceFileReader(Dialect dialect) {
		super(dialect);
	}
	
	@Override
	public void loadFull(Connection connection, TableSpace space) {
		List<TableSpaceFile> list=getAllFull(connection);
		int size=list.size();
		List<TableSpaceFile> c=getSchemaObjectList(space);
		for(int i=0;i<size;i++){
			TableSpaceFile obj=list.get(i);
			c.add(obj);
		}
	}

	@Override
	public void load(Connection connection, TableSpace space) {
		List<TableSpaceFile> list=getAll(connection);
		int size=list.size();
		List<TableSpaceFile> c=getSchemaObjectList(space);
		for(int i=0;i<size;i++){
			TableSpaceFile obj=list.get(i);
			c.add(obj);
		}
	}
	
	protected TableSpaceFileCollection getSchemaObjectList(TableSpace space) {
		return space.getTableSpaceFiles();
	}

	@Override
	protected String getNameLabel(){
		return DbObjects.TABLE_SPACE.getCamelCaseNameLabel();
	}
}
