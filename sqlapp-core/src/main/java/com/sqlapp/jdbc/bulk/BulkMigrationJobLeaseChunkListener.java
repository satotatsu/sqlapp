/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.Objects;

/** Renews a migration job lease before and after every durable chunk. */
public final class BulkMigrationJobLeaseChunkListener
		implements ChunkedBulkMigrationListener {
	private final BulkMigrationJobLeaseManager.LeaseHandle handle;

	public BulkMigrationJobLeaseChunkListener(
			final BulkMigrationJobLeaseManager.LeaseHandle handle) {
		this.handle = Objects.requireNonNull(handle, "handle");
	}

	@Override
	public void onChunkStarted(final ChunkedBulkMigrationProgress progress) {
		renew();
	}

	@Override
	public void onChunkCompleted(final ChunkedBulkMigrationProgress progress) {
		renew();
	}

	private void renew() {
		try {
			handle.renew();
		} catch (SQLException e) {
			throw new BulkMigrationJobLeaseLostException(e);
		}
	}
}
