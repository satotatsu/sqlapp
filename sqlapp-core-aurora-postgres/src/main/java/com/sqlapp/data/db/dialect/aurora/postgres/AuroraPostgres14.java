/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora.postgres;

import com.sqlapp.data.db.dialect.postgres.Postgres140;

/** Aurora PostgreSQL 14 engine baseline. */
public class AuroraPostgres14 extends Postgres140 implements AuroraPostgreSQL {
	private static final long serialVersionUID = 1L;

	public AuroraPostgres14() {
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
