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
import com.sqlapp.data.db.metadata.UserReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.User;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Oracleのユーザー読み込み
 * 
 * @author satoh
 * 
 */
public class OracleUserReader extends UserReader {

	protected OracleUserReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<User> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final boolean dba = OracleMetadataUtils.hasSelectPrivilege(connection,
				this.getDialect(), "SYS", "DBA_USERS");
		SqlNode node = getSqlSqlNode(productVersionInfo);
		OracleMetadataUtils.setDba(dba, context);
		final List<User> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				User obj = createUser(rs, dba);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("users.sql");
	}

	protected User createUser(ExResultSet rs, boolean dba) throws SQLException {
		User obj = new User(getString(rs, "USERNAME"));
		obj.setCreatedAt(rs.getTimestamp("CREATED"));
		if (!dba) {
			return obj;
		}
		obj.setPassword(getString(rs, "PASSWORD"));
		obj.setId(getString(rs, "USER_ID"));
		obj.setCreatedAt(rs.getTimestamp("CREATED"));
		obj.setLockedAt(rs.getTimestamp("LOCK_DATE"));
		obj.setExpiredAt(rs.getTimestamp("EXPIRY_DATE"));
		return obj;
	}
}
