/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

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

public class Db2CatalogReader extends CatalogReader {

	public Db2CatalogReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Catalog> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		List<Catalog> result = CommonUtils.list();
		result.add(new Catalog());
		return result;
	}

	@Override
	public String getCurrentCatalogName(Connection connection) {
		try {
			return connection.getCatalog();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new Db2SchemaReader(this.getDialect());
	}

	@Override
	protected TableSpaceReader newTableSpaceReader() {
		return new Db2TableSpaceReader(this.getDialect());
	}

	@Override
	protected DirectoryReader newDirectoryReader() {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected UserReader newUserReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected RoleReader newRoleReader() {
		return new Db2RoleReader(this.getDialect());
	}

	@Override
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected RoutinePrivilegeReader newRoutinePrivilegeReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected UserPrivilegeReader newUserPrivilegeReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected RoleMemberReader newRoleMemberReader() {
		return new Db2RoleMemberReader(this.getDialect());
	}

	@Override
	protected RolePrivilegeReader newRolePrivilegeReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected SettingReader newSettingReader() {
		// TODO Auto-generated method stub
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
