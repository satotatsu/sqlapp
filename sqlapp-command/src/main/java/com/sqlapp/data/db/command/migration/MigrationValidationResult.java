/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.List;

/** Checksum results for applied migrations; unverified entries are not failures. */
public record MigrationValidationResult(List<Entry> entries) {
	public MigrationValidationResult {
		entries = List.copyOf(entries);
	}

	public enum State {
		VERIFIED, UNVERIFIED, MISSING, CHANGED
	}

	public record Entry(long version, State state, String expectedChecksum, String actualChecksum) {
	}

	public boolean hasFailures() {
		return entries.stream().anyMatch(entry -> entry.state() == State.MISSING || entry.state() == State.CHANGED);
	}
}
