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

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.data.schemas.SchemaObjectProperties;

public abstract class RoutinePrivilegeReader extends AbstractCatalogObjectMetadataReader<RoutinePrivilege>{
	/**
	 * オブジェクト名
	 */
	private String objectName=null;

	public String getObjectName() {
		return objectName;
	}

	public void setObjectName(String objectName) {
		this.objectName = objectName;
	}

	protected RoutinePrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.ROUTINE_PRIVILEGES;
	}

	/**
	 * カタログ名、スキーマ名、ディレクトリ名を含むパラメタコンテキストを作成します。
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=super.defaultParametersContext(connection);
		context.put(getNameLabel(), nativeCaseString(connection,  this.getObjectName()));
		return context;
	}

	protected String getNameLabel(){
		return "objectName";
	}

	protected String getObjectName(ParametersContext context){
		return (String)context.get(getNameLabel());
	}
}
