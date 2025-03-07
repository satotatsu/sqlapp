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

package com.sqlapp.data.db.metadata;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ArgumentRoutine;
import com.sqlapp.data.schemas.NamedArgument;

/**
 * Function ProcedureのPARAMETER
 * 
 * @param <T>
 */
public abstract class RoutineArgumentReader<T extends ArgumentRoutine<?>>
		extends AbstractNamedMetadataReader<NamedArgument, T> {

	protected RoutineArgumentReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * スキーマ名
	 */
	private String schemaName = null;

	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	@Override
	public void loadFull(Connection connection, T obj) {
		List<NamedArgument> list = getAllFull(connection);
		int size = list.size();
		List<NamedArgument> c = getSchemaObjectList(obj);
		for (int i = 0; i < size; i++) {
			NamedArgument val = list.get(i);
			c.add(val);
		}
	}
	
	@Override
	public void load(Connection connection, T obj) {
		List<NamedArgument> list = getAll(connection);
		int size = list.size();
		List<NamedArgument> c = getSchemaObjectList(obj);
		for (int i = 0; i < size; i++) {
			NamedArgument val = list.get(i);
			c.add(val);
		}
	}

	protected List<NamedArgument> getSchemaObjectList(T obj) {
		return obj.getArguments();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.metadata.AbstractDBMetadataFactory#defaultParametersContext(java.sql.Connection)
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName());
		context.put("schemaName", nativeCaseString(connection,  this.getSchemaName()));
		context.put(getNameLabel(), nativeCaseString(connection,  this.getObjectName()));
		return context;
	}
	
	@Override
	protected String getNameLabel() {
		return "routineName";
	}
	
	protected NamedArgument createObject(){
		NamedArgument obj=new NamedArgument();
		obj.setDialect(this.getDialect());
		return obj;
	}

	protected NamedArgument createObject(String name){
		NamedArgument obj=new NamedArgument(name);
		obj.setDialect(this.getDialect());
		return obj;
	}
}
