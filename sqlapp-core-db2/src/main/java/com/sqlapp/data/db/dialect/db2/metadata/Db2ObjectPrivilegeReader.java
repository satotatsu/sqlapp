/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Set-based Db2 LUW privilege metadata; no per-object catalog queries. */
public class Db2ObjectPrivilegeReader extends ObjectPrivilegeReader {
	protected Db2ObjectPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ObjectPrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<ObjectPrivilege> result = new ArrayList<>();
		context.put("schemaName", getSchemaName());
		execute(connection, getSqlNodeCache().getString("objectPrivileges.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.addAll(createPrivileges(rs));
			}
		});
		return result;
	}

	protected List<ObjectPrivilege> createPrivileges(ExResultSet rs) throws SQLException {
		List<ObjectPrivilege> result = new ArrayList<>();
		for (String action : List.of("CONTROL", "ALTER", "DELETE", "INDEX", "INSERT", "REFERENCES", "SELECT",
				"UPDATE")) {
			String state = getString(rs, action.equals("REFERENCES") ? "REFAUTH" : action + "AUTH");
			if (!"Y".equals(state) && !"G".equals(state))
				continue;
			ObjectPrivilege privilege = new ObjectPrivilege();
			privilege.setSchemaName(getString(rs, "schema_name"));
			privilege.setObjectName(getString(rs, "object_name"));
			privilege.setGrantorName(getString(rs, "GRANTOR"));
			privilege.setGranteeName(getString(rs, "GRANTEE"));
			privilege.getSpecifics().put("GRANTEE_TYPE", getString(rs, "GRANTEETYPE"));
			privilege.setPrivilege(action);
			privilege.setGrantable("G".equals(state));
			result.add(privilege);
		}
		return result;
	}
}
