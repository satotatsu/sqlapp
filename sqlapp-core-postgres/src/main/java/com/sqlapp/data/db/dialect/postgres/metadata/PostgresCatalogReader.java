/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.getStringValue;

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
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class PostgresCatalogReader extends CatalogReader {

	public PostgresCatalogReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Catalog> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Catalog> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Catalog obj = createCatalog(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected Catalog createCatalog(ExResultSet rs) throws SQLException {
		Catalog obj = new Catalog(getString(rs, "datname"));
		// obj.setId(getString(rs, "oid"));
		obj.setCollation(getString(rs, "datcollate"));
		return obj;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("catalogs.sql");
		return node;
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new PostgresSchemaReader(this.getDialect());
	}

	@Override
	protected TableSpaceReader newTableSpaceReader() {
		return new PostgresTableSpaceReader(this.getDialect());
	}

	@Override
	public String getCurrentCatalogName(Connection connection) {
		return getStringValue(connection, "select current_database()");
	}

	@Override
	protected void setCommonAfter(Connection connection, Catalog catalog) {
		Setting setting = catalog.getSettings().get("lc_collate");
		if (setting != null) {
			catalog.setCollation(setting.getValue());
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
		return new PostgresUserReader(this.getDialect());
	}

	@Override
	protected RoleReader newRoleReader() {
		return new PostgresRoleReader(this.getDialect());
	}

	@Override
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
		return new PostgresObjectPrivilegeReader(this.getDialect());
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		return new PostgresColumnPrivilegeReader(this.getDialect());
	}

	@Override
	protected UserPrivilegeReader newUserPrivilegeReader() {
		return null;
	}

	@Override
	protected RoleMemberReader newRoleMemberReader() {
		return new PostgresRoleMemberReader(this.getDialect());
	}

	@Override
	protected RoutinePrivilegeReader newRoutinePrivilegeReader() {
		return new PostgresRoutinePrivilegeReader(this.getDialect());
	}

	@Override
	protected RolePrivilegeReader newRolePrivilegeReader() {
		return null;
	}

	@Override
	protected SettingReader newSettingReader() {
		return new PostgresSettingReader(this.getDialect());
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
