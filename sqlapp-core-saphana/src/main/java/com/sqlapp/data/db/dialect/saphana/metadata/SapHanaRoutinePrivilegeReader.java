/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoutinePrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Reads direct EXECUTE grants in one catalog query, without routine lookups.
 */
public class SapHanaRoutinePrivilegeReader extends RoutinePrivilegeReader {
	protected SapHanaRoutinePrivilegeReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoutinePrivilege> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final List<RoutinePrivilege> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("routinePrivileges.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final var privilege = new RoutinePrivilege();
				privilege.setSchemaName(getString(rs, SCHEMA_NAME));
				privilege.setObjectName(getString(rs, OBJECT_NAME));
				privilege.setSpecificName(privilege.getObjectName());
				privilege.setGrantorName(getString(rs, GRANTOR));
				privilege.setGranteeName(getString(rs, GRANTEE));
				privilege.setPrivilege(getString(rs, "PRIVILEGE"));
				privilege.setGrantable("TRUE".equalsIgnoreCase(getString(rs, "IS_GRANTABLE")));
				privilege.getSpecifics().put("ROUTINE_KIND", getString(rs, "OBJECT_TYPE"));
				result.add(privilege);
			}
		});
		return result;
	}
}
