/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

public class MySqlRevokeUserPrivilegeFactory extends MySqlGrantUserPrivilegeFactory {
	@Override
	protected boolean isGrant() {
		return false;
	}
}
