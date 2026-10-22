/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

public class HsqlRevokePrivilegeFactory extends HsqlGrantPrivilegeFactory {
	@Override
	protected boolean isGrant() {
		return false;
	}
}
