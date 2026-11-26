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
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.User;

/** GoogleSQL privileges on individual table columns. */
public class SpannerGrantColumnPrivilegeFactory extends SimpleSqlFactory<ColumnPrivilege, SpannerSqlBuilder> {
	private static final Set<String> SUPPORTED = Set.of("SELECT", "INSERT", "UPDATE");

	protected boolean revoke() {
		return false;
	}

	@Override
	public List<SqlOperation> createSql(final ColumnPrivilege grant) {
		final String privilege = Objects.toString(grant.getPrivilege(), "").toUpperCase(Locale.ROOT);
		if (!SUPPORTED.contains(privilege) || grant.isGrantable() || grant.getGrantorName() != null
				|| grant.getState() != null
				|| (grant.getSpecificName() != null && !grant.getSpecificName().equals(grant.getTableName())))
			throw new UnsupportedOperationException("Unsupported Spanner column privilege or grant options");
		if (grant.getGrantee() instanceof User || grant.getTableName() == null || grant.getTableName().isBlank()
				|| grant.getColumnName() == null || grant.getColumnName().isBlank())
			throw new IllegalArgumentException("Spanner column privilege requires a table, column and database role");
		final var role = new Role(grant.getGranteeName());
		SpannerCreateRoleFactory.requireName(role, false);
		final var table = new Table(grant.getTableName()).setSchemaName(grant.getSchemaName());
		final var builder = createSqlBuilder();
		builder._add(revoke() ? "REVOKE " : "GRANT ")._add(privilege)._add("(").name(grant.getColumnName())
				._add(") ON TABLE ").name(table, true)._add(revoke() ? " FROM ROLE " : " TO ROLE ").name(role);
		return List.of(createOperation(builder.toString(), revoke() ? SqlType.REVOKE : SqlType.GRANT, grant));
	}
}
