/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoleMemberReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Direct visible role grants, never the administrator's applicable-role
 * expansion.
 */
public class HsqlRoleMemberReader extends RoleMemberReader {
	protected HsqlRoleMemberReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoleMember> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		if (version.getMajorVersion() < 2)
			return List.of();
		var result = new ArrayList<RoleMember>();
		execute(connection, getSqlNodeCache().getString("roleMembers2.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var member = new RoleMember().setMemberRoleName(getString(rs, "ROLE_NAME"))
						.setGranteeName(getString(rs, "GRANTEE"));
				// IS_GRANTABLE describes the grantee's administrator status, not a
				// per-grant ADMIN OPTION (unsupported by this engine).
				setSpecifics(rs, "GRANTOR", member);
				setSpecifics(rs, "IS_GRANTABLE", member);
				result.add(member);
			}
		});
		return result;
	}
}
