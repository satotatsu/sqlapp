/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

public class Postgres92CreateViewFactory extends PostgresCreateViewFactory {
	@Override
	protected boolean supportsViewOption(String name) {
		return SECURITY_BARRIER.equals(name) || super.supportsViewOption(name);
	}
}
