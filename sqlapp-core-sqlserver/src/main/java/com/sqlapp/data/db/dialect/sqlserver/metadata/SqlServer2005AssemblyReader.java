/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.AssemblyFileReader;
import com.sqlapp.data.db.metadata.AssemblyReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Assembly;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class SqlServer2005AssemblyReader extends AssemblyReader {

	protected SqlServer2005AssemblyReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Assembly> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Assembly> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Assembly obj = createAssembly(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("assemblies2005.sql");
	}

	protected Assembly createAssembly(ExResultSet rs) throws SQLException {
		Assembly obj = new Assembly(getString(rs, "name"));
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setPermissionSet(getString(rs, "permission_set_desc"));
		obj.setCreatedAt(rs.getTimestamp("create_date"));
		obj.setLastAlteredAt(rs.getTimestamp("modify_date"));
		obj.setId("" + rs.getInt("assembly_id"));
		// TODO clr名は従属属性だからいらない?
		setSpecifics(rs, "clr_name", obj);
		return obj;
	}

	@Override
	protected AssemblyFileReader newAssemblyFileReader() {
		return new SqlServer2005AssemblyFileReader(this.getDialect());
	}
}
