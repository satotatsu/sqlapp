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
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Assembly;
import com.sqlapp.data.schemas.AssemblyFile;
import com.sqlapp.data.schemas.AssemblyFileCollection;
import com.sqlapp.data.schemas.DbObjects;

public abstract class AssemblyFileReader extends AbstractNamedMetadataReader<AssemblyFile, Assembly>{

	protected AssemblyFileReader(Dialect dialect) {
		super(dialect);
	}
	
	@Override
	public void loadFull(Connection connection, Assembly assembly) {
		List<AssemblyFile> list=getAllFull(connection);
		int size=list.size();
		List<AssemblyFile> c=getSchemaObjectList(assembly);
		for(int i=0;i<size;i++){
			AssemblyFile obj=list.get(i);
			c.add(obj);
		}
	}
	
	@Override
	public void load(Connection connection, Assembly assembly) {
		List<AssemblyFile> list=getAll(connection);
		int size=list.size();
		List<AssemblyFile> c=getSchemaObjectList(assembly);
		for(int i=0;i<size;i++){
			AssemblyFile obj=list.get(i);
			c.add(obj);
		}
	}
	
	protected AssemblyFileCollection getSchemaObjectList(Assembly assembly) {
		return assembly.getAssemblyFiles();
	}

	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName());
		context.put(getNameLabel(), nativeCaseString(connection,  this.getObjectName()));
		return context;
	}

	@Override
	protected String getNameLabel(){
		return DbObjects.ASSEMBLY.getCamelCaseNameLabel();
	}

	protected String getObjectName(ParametersContext context){
		return (String)context.get(getNameLabel());
	}
}
