/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Role;
public class SpannerDropRoleFactory extends AbstractDropNamedObjectFactory<Role, SpannerSqlBuilder> {
	@Override
	protected void addDropObject(final Role role, final SpannerSqlBuilder builder) {
		SpannerCreateRoleFactory.requireName(role, true);
		builder.drop().space()._add("ROLE ").name(role);
	}
}
