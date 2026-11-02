/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateProcedureFactory;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.util.CommonUtils;

/** Requires enough metadata to recreate a procedure. */
public class MariadbCreateProcedureFactory extends MySqlCreateProcedureFactory {
	@Override
	protected void addCreateObject(Procedure procedure, MySqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(procedure.getDefinition())
				&& procedure.getDefinition().stream().allMatch(s -> s == null || s.isBlank())) {
			throw new IllegalArgumentException(
					"MariaDB procedure CREATE definition must not be blank: " + procedure.getName());
		}
		if (CommonUtils.isEmpty(procedure.getDefinition()) && (CommonUtils.isEmpty(procedure.getStatement())
				|| procedure.getStatement().stream().allMatch(s -> s == null || s.isBlank()))) {
			throw new IllegalArgumentException(
					"MariaDB procedure requires body or complete CREATE definition; check metadata privileges: "
							+ procedure.getName());
		}
		super.addCreateObject(procedure, builder);
	}
}
