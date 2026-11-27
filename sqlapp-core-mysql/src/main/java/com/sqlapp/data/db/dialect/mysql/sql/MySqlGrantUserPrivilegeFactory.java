/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.Set;
import com.sqlapp.data.db.dialect.mysql.MySql800;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.schemas.UserPrivilege;

/** Global static and MySQL 8+ dynamic privileges. */
public class MySqlGrantUserPrivilegeFactory extends MySqlPrivilegeFactory<UserPrivilege> {
	private static final Set<String> GLOBAL_ONLY = Set.of("ALTER ROUTINE", "CREATE ROUTINE", "CREATE TABLESPACE",
			"CREATE TEMPORARY TABLES", "CREATE USER", "EVENT", "EXECUTE", "FILE", "LOCK TABLES", "PROCESS", "RELOAD",
			"REPLICATION CLIENT", "REPLICATION SLAVE", "SHOW DATABASES", "SHUTDOWN", "SUPER", "USAGE");

	@Override
	protected boolean supportsPrivilege(String privilege) {
		if (TABLE_PRIVILEGES.contains(privilege) || GLOBAL_ONLY.contains(privilege))
			return true;
		return getDialect() instanceof MySql800 && (Set.of("CREATE ROLE", "DROP ROLE").contains(privilege)
				|| (!Set.of("ALL", "PROXY").contains(privilege) && privilege.matches("[A-Z][A-Z0-9_]*")));
	}

	@Override
	protected void validate(UserPrivilege object) {
		if (object.isAdmin())
			throw new IllegalArgumentException("MySQL global privileges use grantable (WITH GRANT OPTION), not admin");
	}

	@Override
	protected void appendTarget(UserPrivilege object, MySqlSqlBuilder builder) {
		builder._add("*.*");
	}
}
