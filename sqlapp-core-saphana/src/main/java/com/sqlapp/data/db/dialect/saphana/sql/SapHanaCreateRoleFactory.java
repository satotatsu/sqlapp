/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Role;

public class SapHanaCreateRoleFactory extends SimpleSqlFactory<Role, SapHanaSqlBuilder> {
	protected boolean create() {
		return true;
	}

	@Override
	public java.util.List<SqlOperation> createSql(Role role) {
		if (role.getName() == null || role.getName().isBlank())
			throw new IllegalArgumentException("SAP HANA role name is required");
		String mode = role.getSpecifics().get("ROLE_MODE"), identity = role.getSpecifics().get("GLOBAL_IDENTITY");
		if (create() && (mode != null && !mode.isBlank() && !"LOCAL".equalsIgnoreCase(mode)
				|| identity != null && !identity.isBlank()))
			throw new IllegalArgumentException("SAP HANA externally mapped roles require explicit DDL");
		var result = new java.util.ArrayList<SqlOperation>();
		addSql(result, createSqlBuilder()._add(create() ? "CREATE ROLE " : "DROP ROLE ").name(role.getName()),
				create() ? SqlType.CREATE : SqlType.DROP, role);
		return result;
	}
}
