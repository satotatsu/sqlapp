/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.SchemaPrivilege;
import com.sqlapp.data.schemas.User;

/**
 * GoogleSQL schema access and grants covering all objects of a supported kind.
 */
public class SpannerGrantSchemaPrivilegeFactory extends SimpleSqlFactory<SchemaPrivilege, SpannerSqlBuilder> {
	private static final Set<String> SUPPORTED = Set.of("USAGE", "SELECT ON ALL TABLES", "INSERT ON ALL TABLES",
			"UPDATE ON ALL TABLES", "DELETE ON ALL TABLES", "SELECT ON ALL VIEWS", "SELECT ON ALL SEQUENCES",
			"UPDATE ON ALL SEQUENCES", "SELECT ON ALL CHANGE STREAMS");

	protected boolean revoke() {
		return false;
	}

	@Override
	public List<SqlOperation> createSql(final SchemaPrivilege grant) {
		final String privilege = Objects.toString(grant.getPrivilege(), "").toUpperCase(Locale.ROOT);
		if (!SUPPORTED.contains(privilege) || grant.isGrantable() || grant.getGrantorName() != null
				|| grant.getState() != null)
			throw new UnsupportedOperationException(
					"Unsupported Spanner schema privilege or grant options: " + privilege);
		if (grant.getGrantee() instanceof User)
			throw new IllegalArgumentException("Spanner schema privileges require a database role");
		final var role = new Role(grant.getGranteeName());
		SpannerCreateRoleFactory.requireName(role, false);
		final String schema = grant.getSchemaName();
		if (schema != null && !schema.isEmpty() && schema.isBlank())
			throw new IllegalArgumentException("Spanner schema privilege name cannot be blank");
		final var builder = createSqlBuilder();
		builder._add(revoke() ? "REVOKE " : "GRANT ")._add(privilege)
				._add("USAGE".equals(privilege) ? " ON SCHEMA " : " IN SCHEMA ");
		if (schema == null || schema.isEmpty())
			builder._add("DEFAULT");
		else
			builder.name(schema);
		builder._add(revoke() ? " FROM ROLE " : " TO ROLE ").name(role);
		return List.of(createOperation(builder.toString(), revoke() ? SqlType.REVOKE : SqlType.GRANT, grant));
	}
}
