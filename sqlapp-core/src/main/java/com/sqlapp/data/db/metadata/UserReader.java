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

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.User;

public abstract class UserReader extends AbstractCatalogNamedObjectMetadataReader<User>{
	/**
	 * ユーザー名
	 */
	protected static final String USER_NAME="user_name";
	/**
	 * パスワード
	 */
	public static final String PASSWORD="password";
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

	protected UserReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.USERS;
	}

	@Override
	protected String getNameLabel(){
		return DbObjects.USER.getCamelCaseNameLabel();
	}
}
