/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UserPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

public class SapHanaUserPrivilegeReader extends UserPrivilegeReader {
	protected SapHanaUserPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UserPrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		var result = new ArrayList<UserPrivilege>();
		execute(connection, getSqlNodeCache().getString("userPrivileges.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var privilege = new UserPrivilege().setGranteeName(rs.getString("GRANTEE"))
						.setGrantorName(rs.getString("GRANTOR")).setPrivilege(rs.getString("PRIVILEGE"))
						.setAdmin("TRUE".equalsIgnoreCase(rs.getString("IS_GRANTABLE")));
				setSpecifics(rs, "IS_VALID", privilege);
				result.add(privilege);
			}
		});
		return result;
	}
}
