/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;
import java.util.function.Consumer;

/** Converts a cancellation request into a safe pause after a durable chunk. */
public final class BulkMigrationCancellationListener
		implements ChunkedBulkMigrationListener {
	private final BulkMigrationCancellationToken token;
	private final Consumer<String> acknowledgement;

	public BulkMigrationCancellationListener(final BulkMigrationCancellationToken token) {
		this(token, reason -> { });
	}

	public BulkMigrationCancellationListener(final BulkMigrationCancellationToken token,
			final Consumer<String> acknowledgement) {
		this.token = Objects.requireNonNull(token, "token");
		this.acknowledgement = acknowledgement == null ? reason -> { } : acknowledgement;
	}

	@Override
	public boolean pauseAfterChunk(final ChunkedBulkMigrationProgress progress) {
		Objects.requireNonNull(progress, "progress");
		if (!token.isCancellationRequested()) {
			return false;
		}
		acknowledgement.accept(token.getReason());
		return true;
	}
}
