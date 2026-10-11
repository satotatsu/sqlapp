/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;

import lombok.Getter;

/** Intentional pause after a migration chunk and its checkpoint are durable. */
@Getter
public class ChunkedBulkMigrationPausedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final ChunkedBulkMigrationProgress progress;

	public ChunkedBulkMigrationPausedException(final ChunkedBulkMigrationProgress progress) {
		super(message(progress));
		this.progress = progress;
	}

	private static String message(final ChunkedBulkMigrationProgress progress) {
		Objects.requireNonNull(progress, "progress");
		return "Migration paused after chunk " + progress.getChunkIndex()
				+ " for " + progress.getMigrationId();
	}
}
