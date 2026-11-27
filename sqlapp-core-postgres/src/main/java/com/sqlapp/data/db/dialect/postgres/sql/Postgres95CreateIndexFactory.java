/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

public class Postgres95CreateIndexFactory extends PostgresCreateIndexFactory {
	@Override
	protected boolean supportsIfNotExists() {
		return true;
	}
}
