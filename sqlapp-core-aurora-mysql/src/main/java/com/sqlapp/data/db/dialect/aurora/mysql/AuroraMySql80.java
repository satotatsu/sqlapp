/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora.mysql;

import com.sqlapp.data.db.dialect.mysql.MySql801;

/** Experimental Aurora MySQL 3.x / MySQL 8.0 baseline. */
public class AuroraMySql80 extends MySql801 implements AuroraMySQL {
	private static final long serialVersionUID = 1L;

	/** Creates the experimental MySQL compatibility baseline. */
	public AuroraMySql80() {
		super(() -> null);
	}

	@Override
	public String getProductName() {
		return "Aurora MySQL";
	}

	@Override
	public String getSimpleName() {
		return "aurora-mysql";
	}
}
