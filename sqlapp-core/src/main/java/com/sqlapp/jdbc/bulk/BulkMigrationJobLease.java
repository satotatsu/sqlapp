/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.time.Instant;

/** Owner-fenced, expiring right to execute one migration plan. */
public record BulkMigrationJobLease(String planFingerprint, String ownerId,
		Instant expiresAt) {
	public static final int ID_MAX_LENGTH = 256;

	public BulkMigrationJobLease {
		requireId(planFingerprint, "planFingerprint");
		requireId(ownerId, "ownerId");
		if (expiresAt == null) {
			throw new IllegalArgumentException("expiresAt must not be null");
		}
	}

	public boolean isExpiredAt(final Instant instant) {
		if (instant == null) {
			throw new IllegalArgumentException("instant must not be null");
		}
		return !expiresAt.isAfter(instant);
	}

	private static void requireId(final String value, final String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be empty");
		}
		if (value.length() > ID_MAX_LENGTH) {
			throw new IllegalArgumentException(name + " must not exceed "
					+ ID_MAX_LENGTH + " characters");
		}
	}
}
