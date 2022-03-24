/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

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
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.util.CommonUtils;

/**
 * Oracleのカタログ読み込みクラス
 * 
 * @author satoh
 * 
 */
public class OracleCatalogReader extends CatalogReader {

	public OracleCatalogReader(Dialect dialect) {
		super(dialect);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.DbMetadataReader#doGetAll(java.sql
	 * .Connection, com.sqlapp.data.parameter.ParametersContext)
	 */
	@Override
	protected List<Catalog> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		List<Catalog> result = CommonUtils.list(1);
		result.add(new Catalog());
		return result;
	}

	@Override
	protected void setCommonAfter(Connection connection, Catalog catalog) {
		Setting setting = catalog.getSettings().get("nls_length_semantics");
		if (setting != null) {
			catalog.setCharacterSemantics(setting.getValue());
		}
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new OracleSchemaReader(this.getDialect());
	}

	@Override
	protected TableSpaceReader newTableSpaceReader() {
		return new OracleTableSpaceReader(this.getDialect());
	}

	@Override
	public String getCurrentCatalogName(Connection connection) {
		return null;
	}

	@Override
	protected DirectoryReader newDirectoryReader() {
		return new OracleDirectoryReader(this.getDialect());
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
		return new OracleUserReader(this.getDialect());
	}

	@Override
	protected RoleReader newRoleReader() {
		return new OracleRoleReader(this.getDialect());
	}

	@Override
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
		return new OracleObjectPrivilegeReader(this.getDialect());
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		return new OracleColumnPrivilegeReader(this.getDialect());
	}

	@Override
	protected UserPrivilegeReader newUserPrivilegeReader() {
		return new OracleUserPrivilegeReader(this.getDialect());
	}

	@Override
	protected RoleMemberReader newRoleMemberReader() {
		return new OracleRoleMemberReader(this.getDialect());
	}

	@Override
	protected RoutinePrivilegeReader newRoutinePrivilegeReader() {
		return null;
	}

	@Override
	protected SettingReader newSettingReader() {
		return new OracleSettingReader(this.getDialect());
	}

	@Override
	protected RolePrivilegeReader newRolePrivilegeReader() {
		return new OracleRolePrivilegeReader(this.getDialect());
	}

	@Override
	protected PublicDbLinkReader newPublicDbLinkReader() {
		return new OraclePublicDbLinkReader(this.getDialect());
	}

	@Override
	protected PublicSynonymReader newPublicSynonymReader() {
		return new OraclePublicSynonymReader(this.getDialect());
	}

}
