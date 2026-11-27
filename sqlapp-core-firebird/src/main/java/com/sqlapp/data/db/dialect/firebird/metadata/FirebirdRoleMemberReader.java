/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.metadata.RoleMemberReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Firebird RoleMember metadata in one catalog query. */
public class FirebirdRoleMemberReader extends RoleMemberReader {
	protected FirebirdRoleMemberReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoleMember> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<RoleMember> result = new ArrayList<>();
		execute(connection, getSqlSqlNode(version), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createObject(rs));
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString("roleMembers.sql");
	}

	protected RoleMember createObject(ExResultSet rs) throws SQLException {
		RoleMember obj = new RoleMember();
		obj.setMemberRoleName(getString(rs, "role_name"));
		obj.setGranteeName(getString(rs, "grantee"));
		obj.setAdmin(rs.getInt("grant_option") != 0);
		obj.setDefault("D".equals(getString(rs, "default_marker")));
		obj.getSpecifics().put(FirebirdPrivilege.GRANTEE_TYPE,
				FirebirdPrivilege.granteeType(rs.getInt("grantee_type")));
		obj.getSpecifics().put(FirebirdPrivilege.GRANTOR, getString(rs, "grantor"));
		return obj;
	}
}
