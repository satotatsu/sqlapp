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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class OracleColumnPrivilegeReader extends ColumnPrivilegeReader {

	protected OracleColumnPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ColumnPrivilege> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final boolean dba = OracleMetadataUtils.hasSelectPrivilege(connection,
				this.getDialect(), "SYS", "DBA_COL_PRIVS");
		SqlNode node = getSqlSqlNode(dba);
		OracleMetadataUtils.setDba(dba, context);
		final List<ColumnPrivilege> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				ColumnPrivilege obj = createColumnPrivilege(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(boolean dba) {
		if (dba) {
			return getSqlNodeCache().getString("dbaColumnPrivileges.sql");
		} else {
			return getSqlNodeCache().getString("columnPrivileges.sql");
		}
	}

	protected ColumnPrivilege createColumnPrivilege(ExResultSet rs)
			throws SQLException {
		String schemaName = getString(rs, "TABLE_SCHEMA");
		String objectName = getString(rs, TABLE_NAME);
		ColumnPrivilege obj = new ColumnPrivilege();
		obj.setSchemaName(schemaName);
		obj.setObjectName(objectName);
		obj.setGrantorName(getString(rs, "GRANTOR"));
		obj.setGranteeName(getString(rs, "GRANTEE"));
		obj.setColumnName(getString(rs, COLUMN_NAME));
		obj.setPrivilege(getString(rs, "PRIVILEGE"));
		obj.setGrantable("YES".equals(getString(rs, "GRANTABLE")));
		return obj;
	}
}
