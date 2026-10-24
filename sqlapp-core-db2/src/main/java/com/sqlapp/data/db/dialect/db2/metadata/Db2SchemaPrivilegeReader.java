/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.AbstractCatalogObjectMetadataReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaPrivilege;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Explicit schema grants, fetched in one query regardless of schema count. */
public class Db2SchemaPrivilegeReader extends AbstractCatalogObjectMetadataReader<SchemaPrivilege> {

	private String schemaName;

	public Db2SchemaPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties() {
		return SchemaObjectProperties.SCHEMA_PRIVILEGES;
	}

	@Override
	protected ParametersContext defaultParametersContext(Connection connection) {
		return newParametersContext(connection, getCatalogName(), getSchemaName());
	}

	protected SqlNode getSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString(version.gte(11, 5, 5) ? "schemaPrivileges1155.sql" : "schemaPrivileges.sql");
	}

	@Override
	protected List<SchemaPrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<SchemaPrivilege> result = new ArrayList<>();
		context.put("schemaName", getSchemaName());
		execute(connection, getSqlNode(version), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.addAll(createPrivileges(rs, version));
			}
		});
		return result;
	}

	protected List<SchemaPrivilege> createPrivileges(ExResultSet rs, ProductVersionInfo version) throws SQLException {
		List<String> actions = new ArrayList<>(List.of("ALTERIN", "CREATEIN", "DROPIN"));
		if (version.gte(11, 5, 5)) {
			actions.addAll(List.of("SELECTIN", "INSERTIN", "UPDATEIN", "DELETEIN", "EXECUTEIN", "SCHEMAADM",
					"ACCESSCTRL", "DATAACCESS", "LOAD"));
		}
		List<SchemaPrivilege> result = new ArrayList<>();
		for (String action : actions) {
			String state = getString(rs, action + "AUTH");
			if (!List.of("Y", "N", "G").contains(state == null ? "" : state))
				throw new SQLException("Invalid Db2 schema privilege state for " + action + ": " + state);
			if ("N".equals(state))
				continue;
			boolean authority = List.of("SCHEMAADM", "ACCESSCTRL", "DATAACCESS", "LOAD").contains(action);
			if (authority && "G".equals(state))
				throw new SQLException("Db2 schema authority cannot have GRANT OPTION: " + action);
			SchemaPrivilege privilege = new SchemaPrivilege().setSchemaName(getString(rs, "SCHEMANAME"))
					.setPrivilege(action).setGrantorName(getString(rs, "GRANTOR"))
					.setGranteeName(getString(rs, "GRANTEE")).setGrantable("G".equals(state));
			privilege.getSpecifics().put("GRANTEE_TYPE", getString(rs, "GRANTEETYPE"));
			result.add(privilege);
		}
		return result;
	}
}
