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
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Assembly;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.SchemaObjectProperties;

/**
 *  CLRアセンブリ読み込み(SQLServer2005以降専用)
 * 
 * @author satoh
 * 
 */
public abstract class AssemblyReader extends
		AbstractCatalogNamedObjectMetadataReader<Assembly> {

	protected AssemblyReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setMetadataDetail(Connection connection, Assembly obj)
			throws SQLException {
		obj.setDialect(this.getDialect());
		this.setObjectName(obj.getName());
		AssemblyFileReader assemblyFileReader = this
				.getAssemblyFileReader();
		if (assemblyFileReader != null) {
			assemblyFileReader.loadFull(connection, obj);
		}
	}
	
	protected AssemblyFileReader getAssemblyFileReader() {
		AssemblyFileReader reader = newAssemblyFileReader();
		if (reader != null) {
			reader.setCatalogName(this.getCatalogName());
			reader.setObjectName(this.getObjectName());
			initializeChild(reader);
		}
		return reader;
	}

	protected abstract AssemblyFileReader newAssemblyFileReader();

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.ASSEMBLIES;
	}

	@Override
	protected String getNameLabel() {
		return DbObjects.ASSEMBLY.getCamelCaseNameLabel();
	}

	protected String getObjectName(ParametersContext context) {
		return (String) context.get(getNameLabel());
	}
}
