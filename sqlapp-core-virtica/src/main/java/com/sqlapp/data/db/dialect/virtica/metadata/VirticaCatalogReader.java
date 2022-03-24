/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.virtica.metadata;

import java.sql.Connection;
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
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.util.CommonUtils;

public class VirticaCatalogReader extends CatalogReader {

	public VirticaCatalogReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setCommonBefore(Connection connection, Catalog catalog) {
		catalog.setCharacterSemantics(CharacterSemantics.Byte);
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new VirticaSchemaReader(this.getDialect());
	}

	@Override
	protected TableSpaceReader newTableSpaceReader() {
		return null;
	}

	@Override
	public String getCurrentCatalogName(Connection connection) {
		return null;
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
		return new VirticaUserReader(this.getDialect());
	}

	@Override
	protected RoleReader newRoleReader() {
		return new VirticaRoleReader(this.getDialect());
	}

	@Override
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
		return null;
	}

	@Override
	protected RoutinePrivilegeReader newRoutinePrivilegeReader() {
		return null;
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		return null;
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

	@Override
	protected List<Catalog> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		List<Catalog> list=CommonUtils.list();
		Catalog catalog=new Catalog();
		catalog.setDialect(this.getDialect());
		list.add(catalog);
		return list;
	}

}
