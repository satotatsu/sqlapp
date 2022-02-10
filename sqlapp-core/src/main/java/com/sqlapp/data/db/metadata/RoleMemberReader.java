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
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.data.schemas.SchemaObjectProperties;
/**
 * RoleMember読み込み
 * @author satoh
 *
 */
public abstract class RoleMemberReader extends AbstractCatalogObjectMetadataReader<RoleMember>{
	/**
	 * オブジェクト名
	 */
	private String grantee=null;

	public String getGrantee() {
		return grantee;
	}

	public void setGrantee(String grantee) {
		this.grantee = grantee;
	}

	protected RoleMemberReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.ROLE_MEMBERS;
	}
	
	/**
	 * カタログ名、スキーマ名、ディレクトリ名を含むパラメタコンテキストを作成します。
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName());
		context.put("grantee", nativeCaseString(connection,  this.getGrantee()));
		return context;
	}
}
