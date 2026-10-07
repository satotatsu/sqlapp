/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.verification;

import java.time.Instant;

/** Portable linkage between verification evidence and a cutover decision. */
public record MigrationCutoverEvidence(int formatVersion, Instant createdAt, String planFingerprint,
		String verificationReportFingerprint, Instant verifiedAt, String cutoverReportFingerprint,
		Instant assessedAt, MigrationCutoverReport.Status status) {
	public static final int CURRENT_FORMAT_VERSION = 1;
}
