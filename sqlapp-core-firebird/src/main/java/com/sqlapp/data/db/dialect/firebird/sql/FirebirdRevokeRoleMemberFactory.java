/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

public class FirebirdRevokeRoleMemberFactory extends FirebirdGrantRoleMemberFactory {
	@Override
	protected boolean isGrant() {
		return false;
	}
}
