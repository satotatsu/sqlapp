/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.schemas.ObjectPrivilege;

/** Table/view privileges with mandatory schema qualification. */
public class MySqlGrantObjectPrivilegeFactory extends MySqlPrivilegeFactory<ObjectPrivilege> {
	@Override
	protected boolean supportsPrivilege(String privilege) {
		return TABLE_PRIVILEGES.contains(privilege);
	}

	@Override
	protected void appendTarget(ObjectPrivilege object, MySqlSqlBuilder builder) {
		builder._add(identifier(object.getSchemaName(), "schema name"))._add(".")
				._add(identifier(object.getObjectName(), "table or view name"));
	}
}
