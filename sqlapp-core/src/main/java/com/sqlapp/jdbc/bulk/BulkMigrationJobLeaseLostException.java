/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Stops a job when its execution lease cannot be renewed. */
public final class BulkMigrationJobLeaseLostException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public BulkMigrationJobLeaseLostException(final Throwable cause) {
		super("The migration job execution lease was lost", cause);
	}
}
