/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.mysql.util.MySqlAccount;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Role;

/** Creates a role using its complete MySQL account identity. */
public class MySqlCreateRoleFactory extends SimpleSqlFactory<Role, MySqlSqlBuilder> {
	protected boolean isCreate() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(Role role) {
		List<SqlOperation> result = new ArrayList<>();
		var builder = createSqlBuilder();
		builder._add(isCreate() ? "CREATE ROLE " : "DROP ROLE ")._add(MySqlAccount.sql(role.getName()));
		addSql(result, builder, isCreate() ? SqlType.CREATE : SqlType.DROP, role);
		return result;
	}
}
