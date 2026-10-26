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
		execute(connection,
				getSqlNodeCache().getString(
						getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110 ? "routinePrivileges1110.sql"
								: "routinePrivileges.sql"),
				context, new ResultSetNextHandler() {
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
		if (privilege.getSpecificName() == null) {
			String schema = getString(rs, "TYPESCHEMA"), type = getString(rs, "TYPENAME");
			if ("M".equals(privilege.getSpecifics().get("ROUTINE_TYPE")) && type != null) {
				if (schema == null)
					throw new SQLException("Db2 method type scope requires TYPESCHEMA");
				privilege.getSpecifics().put("DB2_ROUTINE_SCOPE", "TYPE");
				privilege.getSpecifics().put("DB2_SUBJECT_TYPE_SCHEMA", schema);
				privilege.getSpecifics().put("DB2_SUBJECT_TYPE_NAME", type);
				privilege.setObjectName("METHOD * FOR "
						+ com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.qualified(schema, type));
			} else {
				if (type != null || schema != null && !("M".equals(privilege.getSpecifics().get("ROUTINE_TYPE"))
						&& schema.equals(privilege.getSchemaName())))
					throw new SQLException("Ambiguous Db2 routine schema scope");
				privilege.getSpecifics().put("DB2_ROUTINE_SCOPE", "SCHEMA");
				// Collection clone/find identities do not include specifics. Give each
				// future-object scope its own logical name rather than collapsing '*'.
				String kind = switch (privilege.getSpecifics().get("ROUTINE_TYPE")) {
				case "F" -> "FUNCTION";
				case "M" -> "METHOD";
				case "P" -> "PROCEDURE";
				default -> throw new SQLException("Invalid Db2 routine privilege kind");
				};
				privilege.setObjectName(kind + " *");
			}
			privilege.setHierachy(true);
		}
		privilege.setPrivilege("EXECUTE");
		String state = getString(rs, "EXECUTEAUTH");
		if (!List.of("Y", "G").contains(state == null ? "" : state))
			throw new SQLException("Invalid Db2 routine EXECUTEAUTH: " + state);
		privilege.setGrantable("G".equals(state));
		return privilege;
	}
}
