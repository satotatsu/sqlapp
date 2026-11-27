/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Role;

public class FirebirdCreateRoleFactory extends SimpleSqlFactory<Role, FirebirdSqlBuilder> {
	protected boolean isCreate() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(Role role) {
		FirebirdPrivilege.identifier(role.getName(), "role name");
		var builder = createSqlBuilder();
		builder._add(isCreate() ? "CREATE ROLE " : "DROP ROLE ").name(role.getName());
		if (isCreate()) {
			String privileges = com.sqlapp.data.db.dialect.firebird.util.FirebirdSystemPrivileges.normalize(
					role.getSpecifics()
							.get(com.sqlapp.data.db.dialect.firebird.util.FirebirdSystemPrivileges.ROLE_PRIVILEGES),
					getDialect());
			if (!privileges.isEmpty())
				builder._add(" SET SYSTEM PRIVILEGES TO ")._add(privileges);
		}
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isCreate() ? SqlType.CREATE : SqlType.DROP, role);
		if (isCreate() && role.getRemarks() != null) {
			if (getDialect().compareTo(DialectHolder.defaultDialect20) < 0) {
				throw new IllegalArgumentException("Role comments require Firebird 2.0 or later");
			}
			builder = createSqlBuilder();
			builder._add("COMMENT ON ROLE ").name(role.getName())._add(" IS ").sqlChar(role.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, role);
		}
		return result;
	}
}
