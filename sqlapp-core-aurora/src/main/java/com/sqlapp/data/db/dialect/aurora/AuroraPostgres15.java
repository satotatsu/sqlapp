/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora;

import com.sqlapp.data.db.dialect.postgres.Postgres150;

/** Aurora PostgreSQL 15 engine baseline. */
public class AuroraPostgres15 extends Postgres150 implements AuroraPostgreSQL {
	private static final long serialVersionUID = 1L;

	public AuroraPostgres15() {
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
