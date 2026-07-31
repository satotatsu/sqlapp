/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL 18 default privileges for subsequently created large objects.
 */
public class PostgresLargeObjectDefaultPrivilegesBuilder {
	public enum Privilege {
		SELECT, UPDATE
	}

	private final Dialect dialect;
	private final List<String> targetRoles = new ArrayList<>();
	private final List<String> grantees = new ArrayList<>();
	private final List<Privilege> privileges = new ArrayList<>();
	private boolean publicGrantee;
	private boolean allPrivileges;
	private boolean grant;
	private boolean actionSet;
	private boolean grantOption;
	private boolean grantOptionFor;
	private boolean cascade;

	public PostgresLargeObjectDefaultPrivilegesBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public PostgresLargeObjectDefaultPrivilegesBuilder targetRole(String role) {
		require(role, "targetRole");
		targetRoles.add(role);
		return this;
	}

	public PostgresLargeObjectDefaultPrivilegesBuilder grantee(String role) {
		require(role, "grantee");
		grantees.add(role);
		return this;
	}

	public PostgresLargeObjectDefaultPrivilegesBuilder publicGrantee() {
		this.publicGrantee = true;
		return this;
	}

	public PostgresLargeObjectDefaultPrivilegesBuilder privilege(
			Privilege privilege) {
		privileges.add(Objects.requireNonNull(privilege, "privilege"));
		return this;
	}

	public PostgresLargeObjectDefaultPrivilegesBuilder allPrivileges() {
		this.allPrivileges = true;
		return this;
	}

	public PostgresLargeObjectDefaultPrivilegesBuilder grant(
			boolean withGrantOption) {
		this.grant = true;
		this.actionSet = true;
		this.grantOption = withGrantOption;
		this.grantOptionFor = false;
		return this;
	}

	public PostgresLargeObjectDefaultPrivilegesBuilder revoke(
			boolean grantOptionFor, boolean cascade) {
		this.grant = false;
		this.actionSet = true;
		this.grantOptionFor = grantOptionFor;
		this.cascade = cascade;
		this.grantOption = false;
		return this;
	}

	public String build() {
		checkVersion();
		if (!actionSet) {
			throw new IllegalArgumentException(
					"grant or revoke action must be specified.");
		}
		if (!allPrivileges && privileges.isEmpty()) {
			throw new IllegalArgumentException(
					"At least one privilege must be specified.");
		}
		if (!publicGrantee && grantees.isEmpty()) {
			throw new IllegalArgumentException(
					"At least one grantee must be specified.");
		}
		StringBuilder builder = new StringBuilder(
				"ALTER DEFAULT PRIVILEGES");
		if (!targetRoles.isEmpty()) {
			builder.append(" FOR ROLE ");
			appendNames(builder, targetRoles);
		}
		builder.append(grant ? " GRANT " : " REVOKE ");
		if (!grant && grantOptionFor) {
			builder.append("GRANT OPTION FOR ");
		}
		if (allPrivileges) {
			builder.append("ALL PRIVILEGES");
		} else {
			for (int i = 0; i < privileges.size(); i++) {
				if (i > 0) {
					builder.append(", ");
				}
				builder.append(privileges.get(i).name());
			}
		}
		builder.append(" ON LARGE OBJECTS ");
		builder.append(grant ? "TO " : "FROM ");
		List<String> recipients = new ArrayList<>();
		for (String grantee : grantees) {
			recipients.add(dialect.quote(grantee));
		}
		if (publicGrantee) {
			recipients.add("PUBLIC");
		}
		builder.append(String.join(", ", recipients));
		if (grant && grantOption) {
			builder.append(" WITH GRANT OPTION");
		} else if (!grant) {
			builder.append(cascade ? " CASCADE" : " RESTRICT");
		}
		return builder.toString();
	}

	private void appendNames(StringBuilder builder, List<String> names) {
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(dialect.quote(names.get(i)));
		}
	}

	private void checkVersion() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"Large-object default privileges require PostgreSQL 18 or later.");
		}
	}

	private void require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
	}
}
