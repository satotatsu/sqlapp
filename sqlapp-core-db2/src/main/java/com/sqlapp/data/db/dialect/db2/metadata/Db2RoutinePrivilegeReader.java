/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoutinePrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Set-based Db2 LUW privilege metadata; no per-object catalog queries. */
public class Db2RoutinePrivilegeReader extends RoutinePrivilegeReader {
	protected Db2RoutinePrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoutinePrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<RoutinePrivilege> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("routinePrivileges.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createPrivilege(rs));
			}
		});
		return result;
	}

	protected RoutinePrivilege createPrivilege(ExResultSet rs) throws SQLException {
		RoutinePrivilege privilege = new RoutinePrivilege();
		privilege.setSchemaName(getString(rs, "schema_name"));
		privilege.setObjectName(getString(rs, "object_name"));
		privilege.setGrantorName(getString(rs, "GRANTOR"));
		privilege.setGranteeName(getString(rs, "GRANTEE"));
		privilege.getSpecifics().put("GRANTEE_TYPE", getString(rs, "GRANTEETYPE"));
		privilege.setSpecificName(getString(rs, "SPECIFICNAME"));
		privilege.getSpecifics().put("ROUTINE_TYPE", getString(rs, "ROUTINETYPE"));
		privilege.setPrivilege("EXECUTE");
		privilege.setGrantable("G".equals(getString(rs, "EXECUTEAUTH")));
		return privilege;
	}
}
