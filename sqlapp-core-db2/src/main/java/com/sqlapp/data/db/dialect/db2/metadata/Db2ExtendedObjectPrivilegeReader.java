/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges;
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * One bulk query for sequence/index/server grants. Results are not table
 * privileges.
 */
public class Db2ExtendedObjectPrivilegeReader extends ObjectPrivilegeReader {
	public Db2ExtendedObjectPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	public void loadFull(Connection connection, Catalog catalog) {
		Db2ObjectPrivileges.set(catalog, getAllFull(connection).stream().map(Db2ObjectPrivileges::fromModel).toList());
	}

	@Override
	protected List<ObjectPrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<ObjectPrivilege> result = new ArrayList<>();
		context.put("schemaName", getSchemaName());
		context.put("objectName", getObjectName());
		execute(connection, getSqlNodeCache().getString("extendedObjectPrivileges.sql"), context,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						result.add(createPrivilege(rs));
					}
				});
		return result;
	}

	protected ObjectPrivilege createPrivilege(ExResultSet rs) throws SQLException {
		String state = getString(rs, "AUTH_STATE");
		if (!List.of("Y", "G").contains(state == null ? "" : state))
			throw new SQLException("Invalid Db2 object privilege state: " + state);
		var privilege = new ObjectPrivilege().setSchemaName(getString(rs, "schema_name"))
				.setObjectName(getString(rs, "object_name")).setPrivilege(getString(rs, "PRIVILEGE"))
				.setGrantorName(getString(rs, "GRANTOR")).setGranteeName(getString(rs, "GRANTEE"))
				.setGrantable("G".equals(state)).setHierachy(false);
		privilege.getSpecifics().put("OBJECT_TYPE", getString(rs, "OBJECT_TYPE"));
		privilege.getSpecifics().put("GRANTEE_TYPE", getString(rs, "GRANTEETYPE"));
		Db2ObjectPrivileges.fromModel(privilege); // Validate identity, actions and grantability.
		return privilege;
	}
}
