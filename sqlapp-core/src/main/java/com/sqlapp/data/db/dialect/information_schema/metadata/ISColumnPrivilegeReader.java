/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.information_schema.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * INFORMATION_SCHEMAのカラム権限読み込みクラス
 * 
 * @author satoh
 * 
 */
public class ISColumnPrivilegeReader extends ColumnPrivilegeReader {

	protected ISColumnPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ColumnPrivilege> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
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

	protected ColumnPrivilege createColumnPrivilege(ExResultSet rs)
			throws SQLException {
		String catalogName = getString(rs, TABLE_CATALOG);
		String schemaName = getString(rs, TABLE_SCHEMA);
		String objectName = getString(rs, TABLE_NAME);
		ColumnPrivilege obj = new ColumnPrivilege();
		obj.setCatalogName(catalogName);
		obj.setSchemaName(schemaName);
		obj.setObjectName(objectName);
		obj.setGrantorName(getString(rs, "GRANTOR"));
		obj.setGranteeName(getString(rs, "GRANTEE"));
		obj.setColumnName(getString(rs, COLUMN_NAME));
		obj.setPrivilege(getString(rs, "PRIVILEGE_TYPE"));
		obj.setGrantable("YES".equals(getString(rs, "IS_GRANTABLE")));
		return obj;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache(ISColumnPrivilegeReader.class).getString(
				"columnPrivileges.sql");
		return node;
	}
}
