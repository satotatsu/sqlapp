/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.PostgresPredefinedRole;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL predefined-role membership SQL builder.
 */
public class PostgresPredefinedRoleBuilder {
	private final Dialect dialect;

	public PostgresPredefinedRoleBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public String grant(PostgresPredefinedRole role, String grantee,
			boolean withAdminOption) {
		checkRole(role);
		StringBuilder builder = new StringBuilder("GRANT ")
				.append(role.getRoleName()).append(" TO ")
				.append(dialect.quote(require(grantee, "grantee")));
		if (withAdminOption) {
			builder.append(" WITH ADMIN OPTION");
		}
		return builder.toString();
	}

	public String revoke(PostgresPredefinedRole role, String grantee,
			boolean adminOptionFor, boolean cascade) {
		checkRole(role);
		StringBuilder builder = new StringBuilder("REVOKE ");
		if (adminOptionFor) {
			builder.append("ADMIN OPTION FOR ");
		}
		builder.append(role.getRoleName()).append(" FROM ")
				.append(dialect.quote(require(grantee, "grantee")))
				.append(cascade ? " CASCADE" : " RESTRICT");
		return builder.toString();
	}

	private void checkRole(PostgresPredefinedRole role) {
		Objects.requireNonNull(role, "role");
		if (!role.isSupported(dialect)) {
			throw new IllegalArgumentException(
					role.getRoleName() + " is not supported by this PostgreSQL version.");
		}
	}

	private String require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
		return value;
	}
}
