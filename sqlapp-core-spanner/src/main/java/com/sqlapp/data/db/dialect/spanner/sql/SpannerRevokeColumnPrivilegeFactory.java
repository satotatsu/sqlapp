/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

public class SpannerRevokeColumnPrivilegeFactory extends SpannerGrantColumnPrivilegeFactory {
	@Override
	protected boolean revoke() {
		return true;
	}
}
