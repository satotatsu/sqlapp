/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.AssemblyReader;
import com.sqlapp.data.db.metadata.ColumnPrivilegeReader;
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.db.metadata.PartitionFunctionReader;
import com.sqlapp.data.db.metadata.PartitionSchemeReader;
import com.sqlapp.data.db.metadata.PublicDbLinkReader;
import com.sqlapp.data.db.metadata.RoleMemberReader;
import com.sqlapp.data.db.metadata.RoleReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.SettingReader;
import com.sqlapp.data.db.metadata.TableSpaceReader;
import com.sqlapp.data.db.metadata.UserPrivilegeReader;
import com.sqlapp.data.db.metadata.UserReader;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SQLServer2005のカタログ読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2005CatalogReader extends SqlServer2000CatalogReader {

	public SqlServer2005CatalogReader(Dialect dialect) {
		super(dialect);
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("catalogs2005.sql");
	}

	protected Catalog createCatalog(ExResultSet rs) throws SQLException {
		Catalog obj = new Catalog(getString(rs, "name"));
		// setDbSpecificInfo(rs, "text_in_row_limit", table);
		return obj;
	}

	@Override
	protected void setCommonAfter(Connection connection, Catalog catalog) {
		Setting setting = catalog.getSettings().get("Collation");
		if (setting != null) {
			catalog.setCollation(setting.getValue());
		}
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new SqlServer2005SchemaReader(this.getDialect());
	}

	@Override
	protected TableSpaceReader newTableSpaceReader() {
		return new SqlServer2005TableSpaceReader(this.getDialect());
	}

	@Override
	protected PartitionFunctionReader newPartitionFunctionReader() {
		return new SqlServer2005PartitionFunctionReader(this.getDialect());
	}

	@Override
	protected PartitionSchemeReader newPartitionSchemeReader() {
		return new SqlServer2005PartitionSchemeReader(this.getDialect());
	}

	@Override
	protected AssemblyReader newAssemblyReader() {
		return new SqlServer2005AssemblyReader(this.getDialect());
	}

	@Override
	protected UserReader newUserReader() {
		return new SqlServer2005UserReader(this.getDialect());
	}

	@Override
	protected RoleReader newRoleReader() {
		return new SqlServer2005RoleReader(this.getDialect());
	}

	@Override
	protected UserPrivilegeReader newUserPrivilegeReader() {
		return new SqlServer2005UserPrivilegeReader(this.getDialect());
	}

	@Override
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
		return new SqlServer2005ObjectPrivilegeReader(this.getDialect());
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		return new SqlServer2005ColumnPrivilegeReader(this.getDialect());
	}

	@Override
	protected RoleMemberReader newRoleMemberReader() {
		return new SqlServer2005RoleMemberReader(this.getDialect());
	}

	@Override
	protected SettingReader newSettingReader() {
		return new SqlServer2005SettingReader(this.getDialect());
	}

	@Override
	protected PublicDbLinkReader newPublicDbLinkReader() {
		return new SqlServer2005PublicDbLinkReader(this.getDialect());
	}

}
