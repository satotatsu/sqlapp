/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.util.MySqlAccount;
import com.sqlapp.data.db.metadata.RoleReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Accounts known to be used as roles in MySQL 8+, without guessing from account
 * locks.
 */
public class MySqlRole800Reader extends RoleReader {
	protected MySqlRole800Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected String nativeCaseString(Connection connection, String value) {
		return value;
	}

	@Override
	protected List<Role> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		context = context.clone();
		context.putIfAbsent("accountHost", null);
		Object filter = context.get("roleName");
		if (filter instanceof String name && (name.startsWith("'") || name.startsWith("`"))) {
			context.put("roleName", MySqlAccount.user(name));
			context.put("accountHost", MySqlAccount.host(name));
		}
		List<Role> result = new ArrayList<>();
		execute(connection, getSqlNodeCache(MySqlRole800Reader.class).getString("roles800.sql"), context,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						Role role = new Role(MySqlAccount.name(getString(rs, "role_user"), getString(rs, "role_host")));
						role.setDialect(getDialect());
						result.add(role);
					}
				});
		return result;
	}
}
