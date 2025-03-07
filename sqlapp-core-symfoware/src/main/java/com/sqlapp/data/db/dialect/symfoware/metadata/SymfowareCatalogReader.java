/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-symfoware.
 *
 * sqlapp-core-symfoware is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-symfoware is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-symfoware.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.symfoware.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcCatalogReader;
import com.sqlapp.data.db.metadata.AssemblyReader;
import com.sqlapp.data.db.metadata.ColumnPrivilegeReader;
import com.sqlapp.data.db.metadata.DirectoryReader;
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.db.metadata.PartitionFunctionReader;
import com.sqlapp.data.db.metadata.PartitionSchemeReader;
import com.sqlapp.data.db.metadata.RoleMemberReader;
import com.sqlapp.data.db.metadata.RolePrivilegeReader;
import com.sqlapp.data.db.metadata.RoleReader;
import com.sqlapp.data.db.metadata.RoutinePrivilegeReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.SettingReader;
import com.sqlapp.data.db.metadata.TableSpaceReader;
import com.sqlapp.data.db.metadata.UserPrivilegeReader;
import com.sqlapp.data.db.metadata.UserReader;

public class SymfowareCatalogReader extends JdbcCatalogReader {

	public SymfowareCatalogReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new SymfowareSchemaReader(this.getDialect());
	}

	@Override
	protected TableSpaceReader newTableSpaceReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected DirectoryReader newDirectoryReader() {
		return null;
	}

	@Override
	protected PartitionFunctionReader newPartitionFunctionReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected PartitionSchemeReader newPartitionSchemeReader() {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected RoutinePrivilegeReader newRoutinePrivilegeReader() {
		// TODO Auto-generated method stub
		return null;
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

}
