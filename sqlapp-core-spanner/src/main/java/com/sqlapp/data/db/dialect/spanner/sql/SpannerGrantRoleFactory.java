/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;
import java.util.List;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;
public class SpannerGrantRoleFactory extends SimpleSqlFactory<RoleMember, SpannerSqlBuilder> {
	protected boolean revoke() { return false; }
	@Override
	public List<SqlOperation> createSql(final RoleMember member) {
		if (member.getGrantee() instanceof com.sqlapp.data.schemas.User)
			throw new IllegalArgumentException("Spanner role membership grants to database roles, not users");
		// XML may retain only a grantee name. Native Spanner membership always
		// targets a database role; the service verifies that it exists.
		final var grantee = member.getGrantee() instanceof Role role ? role : new Role(member.getGranteeName());
		SpannerCreateRoleFactory.requireName(grantee, false);
		SpannerCreateRoleFactory.requireName(member.getMemberRole(), false);
		if (member.isAdmin() || member.isDefault())
			throw new UnsupportedOperationException("Spanner role membership has no ADMIN or DEFAULT clause");
		if (member.getMemberRoleName().equalsIgnoreCase(grantee.getName()))
			throw new IllegalArgumentException("Spanner role cannot be a member of itself: " + grantee.getName());
		final var builder = createSqlBuilder();
		builder._add(revoke() ? "REVOKE ROLE " : "GRANT ROLE ").name(member.getMemberRole())
				._add(revoke() ? " FROM ROLE " : " TO ROLE ").name(grantee);
		return List.of(createOperation(builder.toString(), revoke() ? SqlType.REVOKE : SqlType.GRANT, member));
	}
}
