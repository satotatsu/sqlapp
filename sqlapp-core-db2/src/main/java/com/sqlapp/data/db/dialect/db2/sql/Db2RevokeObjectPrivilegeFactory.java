/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

public class Db2RevokeObjectPrivilegeFactory extends Db2GrantObjectPrivilegeFactory {
	@Override
	protected boolean isGrant() {
		return false;
	}
}
