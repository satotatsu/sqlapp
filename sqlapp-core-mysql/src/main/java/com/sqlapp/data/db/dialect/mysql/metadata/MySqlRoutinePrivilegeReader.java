/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.util.MySqlAccount;
import com.sqlapp.data.db.metadata.RoutinePrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Explicit routine grants fetched in one query without authentication fields.
 */
public class MySqlRoutinePrivilegeReader extends RoutinePrivilegeReader {
	protected MySqlRoutinePrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoutinePrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		if (version.lt(5, 0, 3))
			return List.of();
		List<RoutinePrivilege> result = new ArrayList<>();
		execute(connection, getSqlNodeCache(MySqlRoutinePrivilegeReader.class).getString("routinePrivileges.sql"),
				context, new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						result.addAll(createPrivileges(rs));
					}
				});
		return result;
	}

	protected List<RoutinePrivilege> createPrivileges(ExResultSet rs) throws SQLException {
		String kind = getString(rs, "Routine_type");
		if (!"FUNCTION".equalsIgnoreCase(kind) && !"PROCEDURE".equalsIgnoreCase(kind))
			throw new IllegalArgumentException("Unsupported MySQL routine kind: " + kind);
		Set<String> privileges = new LinkedHashSet<>();
		String raw = getString(rs, "Proc_priv");
		if (raw == null || raw.isBlank())
			return List.of();
		for (String privilege : raw.split(",")) {
			String value = privilege.trim().toUpperCase(Locale.ROOT);
			if (!Set.of("EXECUTE", "ALTER ROUTINE", "GRANT").contains(value))
				throw new IllegalArgumentException("Unsupported MySQL routine privilege: " + value);
			privileges.add(value);
		}
		List<RoutinePrivilege> result = new ArrayList<>();
		for (String privilege : privileges) {
			if ("GRANT".equals(privilege) && privileges.size() > 1)
				continue;
			RoutinePrivilege obj = new RoutinePrivilege();
			obj.setHierachy(false);
			obj.setCatalogName("def");
			obj.setSchemaName(getString(rs, "Db"));
			obj.setObjectName(getString(rs, "Routine_name"));
			// Kind participates in identity: MySQL permits a function and procedure with
			// the same name.
			obj.setSpecificName(kind.toUpperCase(Locale.ROOT) + ":" + obj.getObjectName());
			obj.getSpecifics().put("ROUTINE_KIND", kind.toUpperCase(Locale.ROOT));
			obj.setGranteeName(MySqlAccount.name(getString(rs, "User"), getString(rs, "Host")));
			obj.setPrivilege("GRANT".equals(privilege) ? "GRANT OPTION" : privilege);
			obj.setGrantable(!"GRANT".equals(privilege) && privileges.contains("GRANT"));
			result.add(obj);
		}
		return result;
	}
}
