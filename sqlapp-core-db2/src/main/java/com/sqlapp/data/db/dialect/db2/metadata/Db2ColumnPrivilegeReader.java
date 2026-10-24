/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Set-based Db2 LUW privilege metadata; no per-object catalog queries. */
public class Db2ColumnPrivilegeReader extends ColumnPrivilegeReader {
	protected Db2ColumnPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ColumnPrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<ColumnPrivilege> result = new ArrayList<>();
		context.put("schemaName", getSchemaName());
		execute(connection, getSqlNodeCache().getString("columnPrivileges.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createPrivilege(rs));
			}
		});
		return result;
	}

	protected ColumnPrivilege createPrivilege(ExResultSet rs) throws SQLException {
		ColumnPrivilege privilege = new ColumnPrivilege();
		privilege.setSchemaName(getString(rs, "schema_name"));
		privilege.setObjectName(getString(rs, "object_name"));
		privilege.setGrantorName(getString(rs, "GRANTOR"));
		privilege.setGranteeName(getString(rs, "GRANTEE"));
		privilege.getSpecifics().put("GRANTEE_TYPE", getString(rs, "GRANTEETYPE"));
		privilege.setColumnName(getString(rs, "COLNAME"));
		String type = getString(rs, "PRIVTYPE");
		privilege.setPrivilege(switch (type) {
		case "U" -> "UPDATE";
		case "R" -> "REFERENCES";
		default -> throw new IllegalArgumentException("Unsupported Db2 column privilege: " + type);
		});
		privilege.setGrantable("G".equals(getString(rs, "GRANTABLE")));
		return privilege;
	}
}
