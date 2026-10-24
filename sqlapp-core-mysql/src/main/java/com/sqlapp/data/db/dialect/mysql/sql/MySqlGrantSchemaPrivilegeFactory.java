/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.Set;
import com.sqlapp.data.db.dialect.mysql.MySql800;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.schemas.SchemaPrivilege;

/** Database grants retain their source server's database-pattern interpretation. */
public class MySqlGrantSchemaPrivilegeFactory extends MySqlPrivilegeFactory<SchemaPrivilege> {
	public static final String PARTIAL_REVOKES = "mysql_partial_revokes";
	@Override
	protected boolean supportsPrivilege(String privilege) {
		return TABLE_PRIVILEGES.contains(privilege) || Set.of("ALTER ROUTINE", "CREATE ROUTINE", "CREATE TEMPORARY TABLES",
				"EVENT", "EXECUTE", "LOCK TABLES").contains(privilege);
	}
	@Override
	protected void validate(SchemaPrivilege object) {
		String schema = object.getSchemaName();
		identifier(schema, "database name or grant pattern");
		String mode = object.getSpecifics().get(PARTIAL_REVOKES);
		if (mode != null && !Set.of("true", "false").contains(mode))
			throw new IllegalArgumentException("mysql_partial_revokes must be true or false");
		if ("true".equals(mode) && !(getDialect() instanceof MySql800))
			throw new IllegalArgumentException("partial_revokes=true requires MySQL 8.0.16 or later");
		if (mode == null && (schema.contains("_") || schema.contains("%") || schema.contains("\\")))
			throw new IllegalArgumentException("Database grant with _, % or backslash requires specifics[\"mysql_partial_revokes\"] matching the destination server");
	}
	@Override
	protected void appendTarget(SchemaPrivilege object, MySqlSqlBuilder builder) {
		builder._add(identifier(object.getSchemaName(), "database name or grant pattern"))._add(".*");
	}
}
