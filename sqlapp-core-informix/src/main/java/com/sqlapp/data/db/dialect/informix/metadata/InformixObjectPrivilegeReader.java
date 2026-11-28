/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

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

/** One set-based query, retaining grantor and per-privilege grant options. */
public class InformixObjectPrivilegeReader extends ObjectPrivilegeReader {
	public InformixObjectPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ObjectPrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<ObjectPrivilege> result = new ArrayList<>();
		ParametersContext parameters = new ParametersContext();
		parameters.putAll(context);
		if (parameters.get("schemaName") == null)
			parameters.put("schemaName", getSchemaName());
		execute(connection, getSqlNodeCache().getString("objectPrivileges.sql"), parameters,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						String catalog = InformixSecurityMetadata.required(rs, "catalog_name");
						String schema = InformixSecurityMetadata.required(rs, "schema_name");
						String object = InformixSecurityMetadata.required(rs, "table_name");
						String grantor = InformixSecurityMetadata.required(rs, "grantor"),
								grantee = InformixSecurityMetadata.required(rs, "grantee");
						for (var privilege : InformixSecurityMetadata.privileges(rs.getString("table_auth"), false)) {
							var value = new ObjectPrivilege().setCatalogName(catalog).setSchemaName(schema)
									.setObjectName(object).setGrantorName(grantor).setGranteeName(grantee)
									.setPrivilege(privilege.name()).setGrantable(privilege.grantable())
									.setHierachy(false);
							result.add(value);
						}
					}
				});
		return result;
	}
}
