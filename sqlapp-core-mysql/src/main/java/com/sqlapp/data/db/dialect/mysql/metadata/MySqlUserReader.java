/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

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

public class MySqlUserReader extends UserReader {

	protected MySqlUserReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected String nativeCaseString(Connection connection, String value) {
		// Account names must not inherit table-name case folding from JDBC metadata.
		return value;
	}

	@Override
	protected List<User> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		context = context.clone();
		context.putIfAbsent("accountHost", null);
		Object filter = context.get("userName");
		if (filter instanceof String name && (name.startsWith("'") || name.startsWith("`"))) {
			context.put("userName", com.sqlapp.data.db.dialect.mysql.util.MySqlAccount.user(name));
			context.put("accountHost", com.sqlapp.data.db.dialect.mysql.util.MySqlAccount.host(name));
		}
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
		String name = getString(rs, "User");
		User obj = new User("MySQL".equalsIgnoreCase(getDialect().getProductName())
				? com.sqlapp.data.db.dialect.mysql.util.MySqlAccount.name(name, getString(rs, "Host"))
				: name);
		obj.setPassword(rs.getString("Password"));
		setSpecifics(rs, "host", obj);
		return obj;
	}
}
