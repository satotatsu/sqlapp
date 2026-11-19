/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

public class SpannerRevokeSchemaPrivilegeFactory extends SpannerGrantSchemaPrivilegeFactory {
	@Override
	protected boolean revoke() {
		return true;
	}
}
