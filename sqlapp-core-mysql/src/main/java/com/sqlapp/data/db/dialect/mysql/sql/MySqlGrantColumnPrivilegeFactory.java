/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.Set;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.schemas.ColumnPrivilege;

public class MySqlGrantColumnPrivilegeFactory extends MySqlPrivilegeFactory<ColumnPrivilege> {
	@Override
	protected boolean supportsPrivilege(String privilege) {
		return Set.of("INSERT", "REFERENCES", "SELECT", "UPDATE").contains(privilege);
	}

	@Override
	protected void appendColumns(ColumnPrivilege object, MySqlSqlBuilder builder) {
		builder._add(" (")._add(identifier(object.getColumnName(), "column name"))._add(")");
	}

	@Override
	protected void appendTarget(ColumnPrivilege object, MySqlSqlBuilder builder) {
		builder._add(identifier(object.getSchemaName(), "schema name"))._add(".")
				._add(identifier(object.getObjectName(), "table or view name"));
	}
}
