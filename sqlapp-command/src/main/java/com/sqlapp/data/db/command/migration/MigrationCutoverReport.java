/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Machine-readable migration cutover decision. */
public record MigrationCutoverReport(Instant assessedAt, Status status, Duration verificationAge,
		List<Freshness> freshness) {
	public MigrationCutoverReport {
		freshness = List.copyOf(freshness);
	}

	public enum Status {
		READY, NOT_READY_SOURCE_EMPTY, NOT_READY_TARGET_EMPTY, NOT_READY_LAG_EXCEEDED,
		NOT_READY_TARGET_AHEAD, NOT_READY_VERIFICATION_STALE
	}

	public record Freshness(String id, Instant sourceWatermark, Instant targetWatermark, Duration lag,
			Status status) {
	}
}
