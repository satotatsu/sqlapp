/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;

/** Raised when another owner holds a non-expired migration job lease. */
public final class BulkMigrationJobLeaseUnavailableException extends SQLException {
	private static final long serialVersionUID = 1L;

	public BulkMigrationJobLeaseUnavailableException(final String planFingerprint) {
		super("A migration job lease is already active for plan: " + planFingerprint);
	}
}
