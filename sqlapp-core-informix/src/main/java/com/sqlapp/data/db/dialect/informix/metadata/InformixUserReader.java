/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UserReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.User;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Database authorization identities; does not read passwords or OS accounts.
 */
public class InformixUserReader extends UserReader {
	public InformixUserReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<User> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final List<User> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("users.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String kind = getString(rs, "user_type");
				if (!java.util.Set.of("C", "R", "D", "U").contains(kind == null ? "" : kind))
					throw new IllegalStateException("Unknown Informix database user type: " + kind);
				final var user = new User(InformixSecurityMetadata.required(rs, USER_NAME));
				user.getSpecifics().put("INFORMIX_USER_TYPE", kind);
				final String role = getString(rs, "default_role");
				if (role != null && !role.isBlank())
					user.getSpecifics().put("INFORMIX_DEFAULT_ROLE", role);
				result.add(user);
			}
		});
		return result;
	}
}
