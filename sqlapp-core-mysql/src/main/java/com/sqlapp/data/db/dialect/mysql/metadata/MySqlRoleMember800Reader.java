/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.util.MySqlAccount;
import com.sqlapp.data.db.metadata.RoleMemberReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Direct role edges, admin option and default-role flags in a single batch. */
public class MySqlRoleMember800Reader extends RoleMemberReader {
	protected MySqlRoleMember800Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected String nativeCaseString(Connection connection, String value) {
		return value;
	}

	@Override
	protected List<RoleMember> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		context = context.clone();
		if (context.get("grantee") instanceof String account) {
			context.put("granteeUser", MySqlAccount.user(account));
			context.put("granteeHost", MySqlAccount.host(account));
		}
		List<RoleMember> result = new ArrayList<>();
		execute(connection, getSqlNodeCache(MySqlRoleMember800Reader.class).getString("roleMembers800.sql"), context,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						RoleMember member = new RoleMember();
						member.setMemberRoleName(
								MySqlAccount.name(getString(rs, "FROM_USER"), getString(rs, "FROM_HOST")));
						member.setGranteeName(MySqlAccount.name(getString(rs, "TO_USER"), getString(rs, "TO_HOST")));
						member.setAdmin("Y".equalsIgnoreCase(getString(rs, "WITH_ADMIN_OPTION")));
						member.setDefault("Y".equalsIgnoreCase(getString(rs, "IS_DEFAULT")));
						result.add(member);
					}
				});
		return result;
	}
}
