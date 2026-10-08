/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

public class Postgres150CreateViewFactory extends Postgres94CreateViewFactory {
	@Override
	protected boolean supportsViewOption(String name) {
		return SECURITY_INVOKER.equals(name) || super.supportsViewOption(name);
	}
}
