/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Versioned PostgreSQL predefined roles.
 */
public enum PostgresPredefinedRole {
	SIGNAL_AUTOVACUUM_WORKER("pg_signal_autovacuum_worker",
			DialectHolder.postgreSQL180);

	private final String roleName;
	private final Dialect minimumDialect;

	PostgresPredefinedRole(String roleName, Dialect minimumDialect) {
		this.roleName = roleName;
		this.minimumDialect = minimumDialect;
	}

	public String getRoleName() {
		return roleName;
	}

	public boolean isSupported(Dialect dialect) {
		return dialect != null && dialect.compareTo(minimumDialect) >= 0;
	}
}
