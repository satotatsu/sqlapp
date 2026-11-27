/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RolePrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

public class SapHanaRolePrivilegeReader extends RolePrivilegeReader {
	protected SapHanaRolePrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RolePrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		var result = new ArrayList<RolePrivilege>();
		execute(connection, getSqlNodeCache().getString("rolePrivileges.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var privilege = new RolePrivilege().setGranteeName(rs.getString("GRANTEE"))
						.setGrantorName(rs.getString("GRANTOR")).setPrivilege(rs.getString("PRIVILEGE"))
						.setAdmin("TRUE".equalsIgnoreCase(rs.getString("IS_GRANTABLE")));
				setSpecifics(rs, "IS_VALID", privilege);
				result.add(privilege);
			}
		});
		return result;
	}
}
