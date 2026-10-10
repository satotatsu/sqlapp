/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora;

import com.sqlapp.data.db.dialect.postgres.Postgres160;

/** Aurora PostgreSQL 16 engine baseline. */
public class AuroraPostgres16 extends Postgres160 implements AuroraPostgreSQL {
	private static final long serialVersionUID = 1L;
	public AuroraPostgres16() { super(() -> null); }
	@Override public String getProductName() { return "Aurora PostgreSQL"; }
	@Override public String getSimpleName() { return "aurora-postgresql"; }
}
