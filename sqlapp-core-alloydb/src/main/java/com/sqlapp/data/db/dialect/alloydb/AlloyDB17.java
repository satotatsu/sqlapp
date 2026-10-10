/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb;

import com.sqlapp.data.db.dialect.postgres.Postgres170;

/** AlloyDB PostgreSQL 17 engine baseline. */
public class AlloyDB17 extends Postgres170 implements AlloyDB {
	private static final long serialVersionUID = 1L;

	/** Creates the PostgreSQL 17 compatible dialect. */
	public AlloyDB17() {
		super(() -> null);
	}

	@Override
	public String getProductName() {
		return "AlloyDB";
	}

	@Override
	public String getSimpleName() {
		return "alloydb";
	}
}
