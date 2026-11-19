/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoleMemberReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.exceptions.SqlappException;

/** Reads explicit GoogleSQL role memberships in one query. */
public class SpannerRoleMemberReader extends RoleMemberReader {
	public SpannerRoleMemberReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoleMember> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final List<RoleMember> members = new ArrayList<>();
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT ROLE_NAME, GRANTEE FROM INFORMATION_SCHEMA.ROLE_GRANTEES "
						+ "ORDER BY ROLE_NAME, GRANTEE")) {
			while (rows.next())
				members.add(membership(rows.getString(1), rows.getString(2)));
		} catch (SQLException exception) {
			if (SpannerRoleReader.isMissingCatalogTable(exception, "ROLE_GRANTEES"))
				return List.of();
			throw new SqlappException("Unable to read Spanner role memberships", exception);
		}
		return members;
	}

	static RoleMember membership(final String role, final String grantee) {
		if (role == null || role.isBlank() || grantee == null || grantee.isBlank())
			throw new IllegalArgumentException("Spanner membership metadata requires ROLE_NAME and GRANTEE");
		return new RoleMember(new Role(grantee), new Role(role));
	}
}
