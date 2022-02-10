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
import com.sqlapp.data.schemas.AbstractNamedObject;

public abstract class AbstractNamedMetadataReader<T extends AbstractNamedObject<?>, S> extends MetadataReader<T, S>{

	/**
	 * オブジェクト名
	 */
	private String objectName=null;
	
	protected AbstractNamedMetadataReader(Dialect dialect){
		super(dialect);
	}

	/**
	 * @return the objectName
	 */
	public String getObjectName() {
		return objectName;
	}

	/**
	 * @param objectName the objectName to set
	 */
	public void setObjectName(String objectName) {
		this.objectName = objectName;
	}

	public abstract void loadFull(Connection connection, S target);


	public abstract void load(Connection connection, S target);

	/**
	 * オブジェクト名をコンテキストから取得します
	 * @param context
	 */
	protected String getObjectName(ParametersContext context){
		return (String)context.get(getNameLabel());
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.metadata.AbstractDBMetadataFactory#defaultParametersContext(java.sql.Connection)
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName());
		context.put(getNameLabel(), nativeCaseString(connection,  this.getObjectName()));
		return context;
	}

	protected abstract String getNameLabel();
	
}
