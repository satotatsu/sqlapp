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

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.UserPrivilege;
/**
 * ユーザー権限
 * @author satoh
 *
 */
public abstract class UserPrivilegeReader extends AbstractCatalogObjectMetadataReader<UserPrivilege>{
	/**
	 * オブジェクト名
	 */
	private String userName=null;

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	protected UserPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.USER_PRIVILEGES;
	}

	/**
	 * カタログ名、スキーマ名、ディレクトリ名を含むパラメタコンテキストを作成します。
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName());
		context.put(getNameLabel(), nativeCaseString(connection,  this.getUserName()));
		return context;
	}

	protected String getNameLabel(){
		return "userName";
	}

	protected String getObjectName(ParametersContext context){
		return (String)context.get(getNameLabel());
	}
}
