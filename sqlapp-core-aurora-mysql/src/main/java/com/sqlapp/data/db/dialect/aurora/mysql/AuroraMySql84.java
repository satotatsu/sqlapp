/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora.mysql;

import com.sqlapp.data.db.dialect.mysql.MySql840;
import com.sqlapp.data.db.dialect.mysql.MySqlTinyInt1Mapping;

/** Experimental Aurora MySQL 8.4 baseline. */
public class AuroraMySql84 extends MySql840 implements AuroraMySQL {
	private static final long serialVersionUID = 1L;

	/** Creates the experimental MySQL compatibility baseline. */
	public AuroraMySql84() {
		this(MySqlTinyInt1Mapping.BOOLEAN);
	}

	/** Creates an isolated compatibility baseline with the requested mapping. */
	public AuroraMySql84(final MySqlTinyInt1Mapping mapping) {
		super(() -> null);
		if (java.util.Objects.requireNonNull(mapping, "mapping") == MySqlTinyInt1Mapping.NUMERIC)
			useNumericTinyInt1Mapping();
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
