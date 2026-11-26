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
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.User;

/** GoogleSQL privileges on individual modeled database objects. */
public class SpannerGrantObjectPrivilegeFactory extends SimpleSqlFactory<ObjectPrivilege, SpannerSqlBuilder> {
	public static final String OBJECT_TYPE = "OBJECT_TYPE";

	protected boolean revoke() {
		return false;
	}

	@Override
	public List<SqlOperation> createSql(final ObjectPrivilege object) {
		final String kind = Objects.toString(object.getSpecifics().get(OBJECT_TYPE), "").toUpperCase(Locale.ROOT);
		final String privilege = Objects.toString(object.getPrivilege(), "").toUpperCase(Locale.ROOT);
		final Set<String> supported = switch (kind) {
		case "TABLE" -> Set.of("SELECT", "INSERT", "UPDATE", "DELETE");
		case "VIEW", "CHANGE STREAM" -> Set.of("SELECT");
		case "SEQUENCE" -> Set.of("SELECT", "UPDATE");
		case "TABLE FUNCTION" -> Set.of("EXECUTE");
		default -> throw new IllegalArgumentException(
				"Specify Spanner OBJECT_TYPE as TABLE, VIEW, SEQUENCE, CHANGE STREAM or TABLE FUNCTION");
		};
		if (!supported.contains(privilege) || object.isGrantable() || object.isHierachy()
				|| object.getGrantorName() != null || object.getState() != null
				|| (object.getSpecificName() != null && !object.getSpecificName().equals(object.getObjectName()))) {
			throw new UnsupportedOperationException("Unsupported Spanner object privilege or grant options");
		}
		if (object.getGrantee() instanceof User || object.getObjectName() == null || object.getObjectName().isBlank())
			throw new IllegalArgumentException("Spanner object privileges require a named object and database role");
		final var role = new Role(object.getGranteeName());
		SpannerCreateRoleFactory.requireName(role, false);
		final var target = new Table(object.getObjectName()).setSchemaName(object.getSchemaName());
		final var builder = createSqlBuilder();
		builder._add(revoke() ? "REVOKE " : "GRANT ")._add(privilege)._add(" ON ")._add(kind).space().name(target, true)
				._add(revoke() ? " FROM ROLE " : " TO ROLE ").name(role);
		return List.of(createOperation(builder.toString(), revoke() ? SqlType.REVOKE : SqlType.GRANT, object));
	}
}
