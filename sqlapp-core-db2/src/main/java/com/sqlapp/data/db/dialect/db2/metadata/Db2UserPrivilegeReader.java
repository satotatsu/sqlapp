/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2DatabaseAuthority;
import com.sqlapp.data.db.metadata.UserPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UserPrivilege;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Database authorities for users, groups and roles in one catalog query. */
public class Db2UserPrivilegeReader extends UserPrivilegeReader {
	public Db2UserPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	protected SqlNode getSqlNode(ProductVersionInfo version) {
		String resource = version.gte(10, 1, 0) ? "databasePrivileges1010.sql"
				: version.gte(9, 7, 0) ? "databasePrivileges970.sql"
						: version.gte(9, 1, 0) ? "databasePrivileges910.sql" : "databasePrivileges.sql";
		return getSqlNodeCache().getString(resource);
	}

	@Override
	protected List<UserPrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<UserPrivilege> result = new ArrayList<>();
		context.put("userName", getUserName());
		execute(connection, getSqlNode(version), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.addAll(createPrivileges(rs, version));
			}
		});
		return result;
	}

	protected List<UserPrivilege> createPrivileges(ExResultSet rs, ProductVersionInfo version) throws SQLException {
		List<UserPrivilege> result = new ArrayList<>();
		for (Db2DatabaseAuthority authority : Db2DatabaseAuthority.values()) {
			if (!authority.supports(version))
				continue;
			String state = getString(rs, authority.columnName());
			if (!"Y".equals(state) && !"N".equals(state))
				throw new SQLException("Invalid Db2 database authority state for " + authority + ": " + state);
			if ("N".equals(state))
				continue;
			var privilege = new UserPrivilege().setPrivilege(authority.name()).setGranteeName(getString(rs, "GRANTEE"))
					.setGrantorName(getString(rs, "GRANTOR"));
			String type = getString(rs, "GRANTEETYPE");
			if (!List.of("U", "G", "R").contains(type == null ? "" : type))
				throw new SQLException("Invalid Db2 database grantee type: " + type);
			privilege.getSpecifics().put("GRANTEE_TYPE", type);
			if (authority == Db2DatabaseAuthority.DBADM)
				privilege.getSpecifics().put("DBADM_MODEL", version.gte(9, 7, 0) ? "SEPARATE" : "LEGACY");
			result.add(privilege);
		}
		return result;
	}
}
