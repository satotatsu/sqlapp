/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

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

/** Reads requested columns across all tables without JDBC per-table queries. */
public class InformixColumnPrivilegeReader extends ColumnPrivilegeReader {
	public InformixColumnPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ColumnPrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<ColumnPrivilege> result = new ArrayList<>();
		ParametersContext parameters = new ParametersContext();
		parameters.putAll(context);
		if (parameters.get("schemaName") == null)
			parameters.put("schemaName", getSchemaName());
		execute(connection, getSqlNodeCache().getString("columnPrivileges.sql"), parameters,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						String catalog = InformixSecurityMetadata.required(rs, "catalog_name");
						String schema = InformixSecurityMetadata.required(rs, "schema_name");
						String object = InformixSecurityMetadata.required(rs, "table_name"),
								column = InformixSecurityMetadata.required(rs, "column_name");
						String grantor = InformixSecurityMetadata.required(rs, "grantor"),
								grantee = InformixSecurityMetadata.required(rs, "grantee");
						for (var privilege : InformixSecurityMetadata.privileges(rs.getString("column_auth"), true)) {
							result.add(new ColumnPrivilege().setCatalogName(catalog).setSchemaName(schema)
									.setTableName(object).setColumnName(column).setGrantorName(grantor)
									.setGranteeName(grantee).setPrivilege(privilege.name())
									.setGrantable(privilege.grantable()));
						}
					}
				});
		return result;
	}
}
