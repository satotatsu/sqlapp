/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

public class Db2RevokeColumnPrivilegeFactory extends Db2GrantColumnPrivilegeFactory {
	@Override
	protected boolean isGrant() {
		return false;
	}

	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(
			com.sqlapp.data.schemas.ColumnPrivilege privilege) {
		throw new IllegalArgumentException("Db2 cannot revoke an individual column privilege; "
				+ "explicitly revoke the table-level privilege and regrant the retained columns");
	}
}
