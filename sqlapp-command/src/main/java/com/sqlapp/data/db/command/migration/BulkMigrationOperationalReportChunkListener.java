/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.Objects;

import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress;

/** Refreshes an operational report after each checkpoint-durable chunk. */
public final class BulkMigrationOperationalReportChunkListener
		implements ChunkedBulkMigrationListener {
	private final BulkMigrationOperationalReportJobListener reportListener;

	public BulkMigrationOperationalReportChunkListener(
			final BulkMigrationOperationalReportJobListener reportListener) {
		this.reportListener = Objects.requireNonNull(reportListener, "reportListener");
	}

	@Override
	public void onChunkCompleted(final ChunkedBulkMigrationProgress progress) {
		reportListener.refresh();
	}
}
