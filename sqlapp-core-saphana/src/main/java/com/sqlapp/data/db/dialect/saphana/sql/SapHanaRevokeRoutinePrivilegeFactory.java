/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

public class SapHanaRevokeRoutinePrivilegeFactory extends SapHanaGrantRoutinePrivilegeFactory {
	@Override
	protected boolean isGrant() {
		return false;
	}
}
