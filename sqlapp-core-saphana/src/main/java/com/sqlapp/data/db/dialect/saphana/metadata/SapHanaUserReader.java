/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.saphana.metadata;

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
 * SAP HANA User Reader
 * 
 * @author satoh
 * 
 */
public class SapHanaUserReader extends UserReader {

	protected SapHanaUserReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<User> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<User> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				User obj = createUser(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("users.sql");
	}

	protected User createUser(ExResultSet rs) throws SQLException {
		User obj = new User(getString(rs, USER_NAME));
		obj.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		obj.setPassword(getString(rs, PASSWORD));
		setDbSpecificInfo(rs, obj);
		setDbDynamicInfo(rs, obj);
		return obj;
	}

	protected void setDbSpecificInfo(ExResultSet rs, User obj)
			throws SQLException {
	}

	protected void setDbDynamicInfo(ExResultSet rs, User obj) throws SQLException {
		setSpecifics(rs, "CREATOR", obj);
		setSpecifics(rs, "LAST_SUCCESSFUL_CONNECT", obj);
		setSpecifics(rs, "LAST_INVALID_CONNECT_ATTEMPT", obj);
		setSpecifics(rs, "INVALID_CONNECT_ATTEMPTS", obj);
		setSpecifics(rs, "ADMIN_GIVEN_PASSWORD", obj);
		setSpecifics(rs, "PASSWORD_CHANGE_TIME", obj);
		setSpecifics(rs, "PASSWORD_CHANGE_NEEDED", obj);
		setSpecifics(rs, "USER_DEACTIVATED", obj);
		setSpecifics(rs, "DEACTIVATION_TIME", obj);
	}
}
