/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoleReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Role;

/** Reads user-defined GoogleSQL database roles in one query. */
public class SpannerRoleReader extends RoleReader {
	public SpannerRoleReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Role> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final List<Role> roles = new ArrayList<>();
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT ROLE_NAME FROM INFORMATION_SCHEMA.ROLES "
						+ "WHERE IS_SYSTEM = FALSE ORDER BY ROLE_NAME")) {
			while (rows.next()) {
				final String name = rows.getString(1);
				if (name == null || name.isBlank())
					throw new IllegalArgumentException("Spanner role metadata requires ROLE_NAME");
				roles.add(new Role(name));
			}
		} catch (SQLException exception) {
			if (isMissingRoleCatalog(exception))
				return List.of();
			throw new com.sqlapp.exceptions.SqlappException("Unable to read Spanner database roles", exception);
		}
		return roles;
	}

	static boolean isMissingRoleCatalog(final SQLException exception) {
		return isMissingCatalogTable(exception, "ROLES");
	}

	static boolean isMissingCatalogTable(final SQLException exception, final String table) {
		final String message = exception.getMessage();
		return message != null && message.toUpperCase(Locale.ROOT)
				.matches("(?s).*TABLE NOT FOUND: INFORMATION_SCHEMA\\." + table + "(?![A-Z0-9_]).*");
	}
}
