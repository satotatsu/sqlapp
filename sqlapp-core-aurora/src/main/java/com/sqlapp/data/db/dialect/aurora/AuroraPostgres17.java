/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora;

import com.sqlapp.data.db.dialect.postgres.Postgres170;

/** Aurora PostgreSQL 17 engine baseline. */
public class AuroraPostgres17 extends Postgres170 implements AuroraPostgreSQL {
	private static final long serialVersionUID = 1L;

	public AuroraPostgres17() {
		super(() -> null);
	}

	@Override
	public String getProductName() {
		return "Aurora PostgreSQL";
	}

	@Override
	public String getSimpleName() {
		return "aurora-postgresql";
	}
}
