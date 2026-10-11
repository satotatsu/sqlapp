/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe request to stop a migration at its next durable checkpoint. */
public final class BulkMigrationCancellationToken {
	private static final String DEFAULT_REASON = "Cancellation requested";
	private final AtomicReference<String> reason = new AtomicReference<>();

	public boolean requestCancellation() {
		return requestCancellation(DEFAULT_REASON);
	}

	public boolean requestCancellation(final String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("cancellation reason must not be empty");
		}
		return this.reason.compareAndSet(null, reason);
	}

	public boolean isCancellationRequested() {
		return reason.get() != null;
	}

	public String getReason() {
		return reason.get();
	}
}
