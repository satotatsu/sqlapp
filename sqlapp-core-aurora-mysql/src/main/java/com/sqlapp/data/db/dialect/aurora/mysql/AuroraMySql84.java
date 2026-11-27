/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora.mysql;

import com.sqlapp.data.db.dialect.mysql.MySql840;

/** Experimental Aurora MySQL 8.4 baseline. */
public class AuroraMySql84 extends MySql840 implements AuroraMySQL {
	private static final long serialVersionUID = 1L;

	/** Creates the experimental MySQL compatibility baseline. */
	public AuroraMySql84() {
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
