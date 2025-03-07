/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.AssemblyReader;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.ColumnPrivilegeReader;
import com.sqlapp.data.db.metadata.DirectoryReader;
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.db.metadata.PartitionFunctionReader;
import com.sqlapp.data.db.metadata.PartitionSchemeReader;
import com.sqlapp.data.db.metadata.PublicDbLinkReader;
import com.sqlapp.data.db.metadata.PublicSynonymReader;
import com.sqlapp.data.db.metadata.RoleMemberReader;
import com.sqlapp.data.db.metadata.RolePrivilegeReader;
import com.sqlapp.data.db.metadata.RoleReader;
import com.sqlapp.data.db.metadata.RoutinePrivilegeReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.SettingReader;
import com.sqlapp.data.db.metadata.TableSpaceReader;
import com.sqlapp.data.db.metadata.UserPrivilegeReader;
import com.sqlapp.data.db.metadata.UserReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.util.CommonUtils;

/**
 * Firebirdのカタログ読み込み(Firebirdはカタログがないので常に１つのCatalogオブジェクトを返す)
 * 
 * @author satoh
 * 
 */
public class FirebirdCatalogReader extends CatalogReader {

	public FirebirdCatalogReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Catalog> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		List<Catalog> result = CommonUtils.list();
		result.add(new Catalog());
		return result;
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new FirebirdSchemaReader(this.getDialect());
	}

	@Override
	protected TableSpaceReader newTableSpaceReader() {
		return new FirebirdTableSpaceReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.CatalogReader#getCurrentCatalogName
	 * (java.sql.Connection)
	 */
	@Override
	public String getCurrentCatalogName(Connection connection) {
		try {
			return connection.getCatalog();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected DirectoryReader newDirectoryReader() {
		return null;
	}

	@Override
	protected PartitionFunctionReader newPartitionFunctionReader() {
		return null;
	}

	@Override
	protected PartitionSchemeReader newPartitionSchemeReader() {
		return null;
	}

	@Override
	protected AssemblyReader newAssemblyReader() {
		return null;
	}

	@Override
	protected UserReader newUserReader() {
		return null;
	}

	@Override
	protected RoleReader newRoleReader() {
		return null;
	}

	@Override
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
		return new FirebirdObjectPrivilegeReader(this.getDialect());
	}

	@Override
	protected RoutinePrivilegeReader newRoutinePrivilegeReader() {
		return null;
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		return new FirebirdColumnPrivilegeReader(this.getDialect());
	}

	@Override
	protected UserPrivilegeReader newUserPrivilegeReader() {
		return null;
	}

	@Override
	protected RoleMemberReader newRoleMemberReader() {
		return null;
	}

	@Override
	protected RolePrivilegeReader newRolePrivilegeReader() {
		return null;
	}

	@Override
	protected SettingReader newSettingReader() {
		return null;
	}

	@Override
	protected PublicDbLinkReader newPublicDbLinkReader() {
		return null;
	}

	@Override
	protected PublicSynonymReader newPublicSynonymReader() {
		return null;
	}

}
