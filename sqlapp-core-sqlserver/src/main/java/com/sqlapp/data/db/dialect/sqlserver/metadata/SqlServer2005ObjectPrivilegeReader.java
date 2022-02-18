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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class SqlServer2005ObjectPrivilegeReader extends ObjectPrivilegeReader {

	protected SqlServer2005ObjectPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ObjectPrivilege> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<ObjectPrivilege> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				ObjectPrivilege obj = createPrivilege(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("objectPrivileges2005.sql");
	}

	protected ObjectPrivilege createPrivilege(ExResultSet rs) throws SQLException {
		String catalogName = getString(rs, CATALOG_NAME);
		String schemaName = getString(rs, SCHEMA_NAME);
		String objectName = getString(rs, OBJECT_NAME);
		ObjectPrivilege obj = new ObjectPrivilege();
		obj.setCatalogName(catalogName);
		obj.setSchemaName(schemaName);
		obj.setObjectName(objectName);
		obj.setGrantorName(getString(rs, GRANTOR));
		obj.setGranteeName(getString(rs, GRANTEE));
		obj.setPrivilege(getString(rs, "permission_name"));
		obj.setState(getString(rs, "state_desc"));
		obj.setGrantable("GRANT_WITH_GRANT_OPTION".equals(getString(rs,
				"state_desc")));
		return obj;
	}
}
