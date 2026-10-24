/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.metadata.RoutinePrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Firebird RoutinePrivilege metadata in one catalog query. */
public class FirebirdRoutinePrivilegeReader extends RoutinePrivilegeReader {
	protected FirebirdRoutinePrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoutinePrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<RoutinePrivilege> result = new ArrayList<>();
		execute(connection, getSqlSqlNode(version), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createObject(rs));
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString("routinePrivileges.sql");
	}

	protected RoutinePrivilege createObject(ExResultSet rs) throws SQLException {
		RoutinePrivilege obj = new RoutinePrivilege();
		obj.setHierachy(false);
		obj.setObjectName(getString(rs, "object_name"));
		obj.setPrivilege(FirebirdUtils.getPrivilege(getString(rs, "privilege_type")));
		obj.setGranteeName(
				FirebirdPrivilege.granteeName(getString(rs, "grantee"), rs.getInt("grantee_type"), getDialect()));
		obj.setGrantorName(getString(rs, "grantor"));
		obj.setGrantable(rs.getInt("is_grantable") == 1);
		obj.getSpecifics().put(FirebirdPrivilege.OBJECT_TYPE, FirebirdPrivilege.objectType(rs.getInt("object_type")));
		obj.getSpecifics().put(FirebirdPrivilege.GRANTEE_TYPE,
				FirebirdPrivilege.granteeType(rs.getInt("grantee_type")));
		return obj;
	}
}
