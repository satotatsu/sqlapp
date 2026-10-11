/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

public class Postgres94CreateViewFactory extends Postgres92CreateViewFactory {
	@Override
	protected boolean supportsViewOption(String name) {
		return CHECK_OPTION.equals(name) || super.supportsViewOption(name);
	}
}
