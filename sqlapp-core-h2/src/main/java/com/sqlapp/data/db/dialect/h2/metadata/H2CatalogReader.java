/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.getStringValue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.information_schema.metadata.AbstractISCatalogReader;
import com.sqlapp.data.db.metadata.AssemblyReader;
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
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * H2のカタログ読み込み
 * 
 * @author satoh
 * 
 */
public class H2CatalogReader extends AbstractISCatalogReader {

	public H2CatalogReader(Dialect dialect) {
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
		Catalog obj = new Catalog(getString(rs, CATALOG_NAME));
		return obj;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("catalogs.sql");
		return node;
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new H2SchemaReader(this.getDialect());
	}

	@Override
	protected TableSpaceReader newTableSpaceReader() {
		return null;
	}

	@Override
	public String getCurrentCatalogName(Connection connection) {
		return getStringValue(connection, "call DATABASE()");
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
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
		return new H2ObjectPrivilegeReader(this.getDialect());
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		return new H2ColumnPrivilegeReader(this.getDialect());
	}

	@Override
	protected UserReader newUserReader() {
		return new H2UserReader(this.getDialect());
	}

	@Override
	protected RoleReader newRoleReader() {
		return new H2RoleReader(this.getDialect());
	}

	@Override
	protected RoutinePrivilegeReader newRoutinePrivilegeReader() {
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
		return new H2SettingReader(this.getDialect());
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
