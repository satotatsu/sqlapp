/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoleReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Roles are scoped to the connected Informix database. */
public class InformixRoleReader extends RoleReader {
	public InformixRoleReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Role> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final List<Role> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("roles.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				result.add(new Role(InformixSecurityMetadata.required(rs, ROLE_NAME)));
			}
		});
		return result;
	}
}
