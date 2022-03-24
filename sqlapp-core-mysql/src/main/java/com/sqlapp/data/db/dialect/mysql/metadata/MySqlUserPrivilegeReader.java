/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UserPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.User;
import com.sqlapp.data.schemas.UserPrivilege;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * INFORMATION_SCHEMAのユーザー権限読み込みクラス
 * 
 * @author satoh
 * 
 */
public class MySqlUserPrivilegeReader extends UserPrivilegeReader {

	protected MySqlUserPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UserPrivilege> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<UserPrivilege> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				UserPrivilege obj = createPrivilege(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected UserPrivilege createPrivilege(ExResultSet rs) throws SQLException {
		UserPrivilege obj = new UserPrivilege();
		obj.setCatalogName(getString(rs, TABLE_CATALOG));
		obj.setGrantee(new User(getString(rs, "GRANTEE")));
		obj.setPrivilege(getString(rs, "PRIVILEGE_TYPE"));
		obj.setGrantable("YES".equals(getString(rs, "IS_GRANTABLE")));
		return obj;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("userPrivileges.sql");
		return node;
	}
}
