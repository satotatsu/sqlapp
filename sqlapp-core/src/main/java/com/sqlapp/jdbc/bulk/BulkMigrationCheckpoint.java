/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.Serializable;

import lombok.Builder;
import lombok.Value;

/** Durable progress recorded only after a migration chunk succeeds. */
@Value
@Builder(toBuilder = true)
public class BulkMigrationCheckpoint implements Serializable {
	private static final long serialVersionUID = 1L;
	public static final int ID_MAX_LENGTH = 255;
	public static final int FINGERPRINT_MAX_LENGTH = 255;
	public static final int HASH_MAX_LENGTH = 64;
	public static final int RESUME_TOKEN_MAX_LENGTH = 4_000;
	String migrationId;
	String sourceFingerprint;
	String targetFingerprint;
	long processedRows;
	long completedChunks;
	String lastChunkHash;
	String resumeToken;
	boolean complete;

	public BulkMigrationCheckpoint validate() {
		validateMigrationId(migrationId);
		maxLength(sourceFingerprint, FINGERPRINT_MAX_LENGTH, "sourceFingerprint");
		maxLength(targetFingerprint, FINGERPRINT_MAX_LENGTH, "targetFingerprint");
		maxLength(lastChunkHash, HASH_MAX_LENGTH, "lastChunkHash");
		maxLength(resumeToken, RESUME_TOKEN_MAX_LENGTH, "resumeToken");
		if (processedRows < 0 || completedChunks < 0) {
			throw new IllegalArgumentException("checkpoint progress must not be negative");
		}
		return this;
	}

	public static void validateMigrationId(final String migrationId) {
		if (migrationId == null || migrationId.isBlank()) {
			throw new IllegalArgumentException("migrationId must not be empty");
		}
		maxLength(migrationId, ID_MAX_LENGTH, "migrationId");
	}

	private static void maxLength(final String value, final int maxLength,
			final String name) {
		if (value != null && value.length() > maxLength) {
			throw new IllegalArgumentException(name + " must not exceed " + maxLength
					+ " characters");
		}
	}
}
