/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Write operation used for each resumable migration chunk. */
public enum BulkMigrationMode {
	INSERT,
	UPSERT
}
