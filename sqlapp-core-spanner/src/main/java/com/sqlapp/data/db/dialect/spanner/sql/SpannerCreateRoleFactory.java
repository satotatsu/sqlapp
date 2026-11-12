/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.Locale;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.schemas.Role;

public class SpannerCreateRoleFactory extends AbstractCreateNamedObjectFactory<Role, SpannerSqlBuilder> {
	static void requireName(final Role role, final boolean userDefined) {
		if (role == null || role.getName() == null || role.getName().isBlank())
			throw new IllegalArgumentException("Spanner database role requires a name");
		final String name = role.getName().toLowerCase(Locale.ROOT);
		if (userDefined && ("public".equals(name) || name.startsWith("spanner_")))
			throw new IllegalArgumentException("Spanner system role cannot be created or dropped: " + role.getName());
	}
	@Override
	protected void addCreateObject(final Role role, final SpannerSqlBuilder builder) {
		requireName(role, true);
		if (role.isGrantable())
			throw new UnsupportedOperationException("Spanner CREATE ROLE does not support grantable role options");
		builder.create().space()._add("ROLE ").name(role);
	}
}
