/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.AbstractCatalogObjectMetadataReader;
import com.sqlapp.data.db.dialect.mysql.sql.MySqlGrantSchemaPrivilegeFactory;
import com.sqlapp.data.db.dialect.mysql.util.MySqlAccount;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaPrivilege;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Explicit database-level grants in one set query; partial-revoke restrictions
 * are not inferred.
 */
public class MySqlSchemaPrivilegeReader extends AbstractCatalogObjectMetadataReader<SchemaPrivilege> {
	private String schemaName;

	public MySqlSchemaPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	@Override
	public void loadFull(Connection connection, Catalog catalog) {
		super.loadFull(connection, catalog);
		for (SchemaPrivilege privilege : catalog.getSchemaPrivileges())
			privilege.setGranteeName(privilege.getGranteeName());
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties() {
		return SchemaObjectProperties.SCHEMA_PRIVILEGES;
	}

	@Override
	protected ParametersContext defaultParametersContext(Connection connection) {
		return newParametersContext(connection, getCatalogName(), getSchemaName());
	}

	@Override
	protected List<SchemaPrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		if (version.lt(5, 0, 0))
			return List.of();
		String query = version.gte(8, 0, 16) ? "schemaPrivileges8016.sql" : "schemaPrivileges.sql";
		List<SchemaPrivilege> result = new ArrayList<>();
		execute(connection, getSqlNodeCache(MySqlSchemaPrivilegeReader.class).getString(query), context,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						result.add(createPrivilege(rs));
					}
				});
		return result;
	}

	protected SchemaPrivilege createPrivilege(ExResultSet rs) throws SQLException {
		SchemaPrivilege privilege = new SchemaPrivilege();
		privilege.setCatalogName(rs.getString("TABLE_CATALOG"));
		privilege.setSchemaName(rs.getString("TABLE_SCHEMA"));
		privilege.setGranteeName(MySqlAccount.fromGrantee(rs.getString("GRANTEE")));
		privilege.setPrivilege(rs.getString("PRIVILEGE_TYPE"));
		String grantable = rs.getString("IS_GRANTABLE");
		if (!"YES".equals(grantable) && !"NO".equals(grantable))
			throw new SQLException("Invalid MySQL schema grantability: " + grantable);
		privilege.setGrantable("YES".equals(grantable));
		if ("USAGE".equals(privilege.getPrivilege()) && privilege.isGrantable()) {
			// The server reports a database GRANT OPTION-only row as USAGE.
			privilege.setPrivilege("GRANT OPTION");
			privilege.setGrantable(false);
		}
		String mode = rs.getString("PARTIAL_REVOKES");
		if (!"0".equals(mode) && !"1".equals(mode))
			throw new SQLException("Invalid MySQL partial_revokes mode: " + mode);
		privilege.getSpecifics().put(MySqlGrantSchemaPrivilegeFactory.PARTIAL_REVOKES,
				"1".equals(mode) ? "true" : "false");
		return privilege;
	}
}
