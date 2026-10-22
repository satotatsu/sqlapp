/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Role;

/**
 * Creates user-defined roles; built-in definitions are supplied by the engine.
 */
public class HsqlCreateRoleFactory extends SimpleSqlFactory<Role, HsqlSqlBuilder> {
	static final Set<String> SYSTEM_ROLES = Set.of("PUBLIC", "DBA", "CREATE_SCHEMA", "CHANGE_AUTHORIZATION",
			"SCRIPT_OPS");

	static String identifier(String value) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("A role or grantee name is required");
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	@Override
	public List<SqlOperation> createSql(Role role) {
		String name = identifier(role.getName());
		if (SYSTEM_ROLES.contains(role.getName()))
			return List.of();
		if (role.isGrantable())
			throw new IllegalArgumentException("HSQLDB does not support role WITH ADMIN OPTION");
		var result = new ArrayList<SqlOperation>();
		addSql(result, createSqlBuilder()._add("CREATE ROLE ")._add(name), SqlType.CREATE, role);
		return result;
	}
}
