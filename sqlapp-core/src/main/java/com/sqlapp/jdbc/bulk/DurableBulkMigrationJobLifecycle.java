/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Records lifecycle transitions around another migration lifecycle. */
public final class DurableBulkMigrationJobLifecycle
		implements BulkMigrationJobLifecycle {
	private final BulkMigrationJobLifecycle delegate;
	private final BulkMigrationMaintenanceStateStore store;
	private final Clock clock;

	public DurableBulkMigrationJobLifecycle(final BulkMigrationJobLifecycle delegate,
			final BulkMigrationMaintenanceStateStore store) {
		this(delegate, store, Clock.systemUTC());
	}

	DurableBulkMigrationJobLifecycle(final BulkMigrationJobLifecycle delegate,
			final BulkMigrationMaintenanceStateStore store, final Clock clock) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.store = Objects.requireNonNull(store, "store");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public String getConfigurationFingerprint() {
		return "durable-v1:" + delegate.getConfigurationFingerprint();
	}

	@Override
	public List<BulkMigrationJobOperation> plan(
			final List<BulkMigrationJobTask> tasks) {
		return delegate.plan(tasks);
	}

	@Override
	public void before(final Connection connection, final BulkMigrationJobPlan plan)
			throws SQLException {
		save(plan, BulkMigrationMaintenanceStatus.PREPARING, null);
		delegate.before(connection, plan);
		save(plan, BulkMigrationMaintenanceStatus.PREPARED, null);
	}

	@Override
	public void after(final Connection connection, final BulkMigrationJobPlan plan,
			final BulkMigrationJobResult result) throws SQLException {
		save(plan, BulkMigrationMaintenanceStatus.POST_PROCESSING, null);
		delegate.after(connection, plan, result);
		save(plan, BulkMigrationMaintenanceStatus.COMPLETE, null);
	}

	@Override
	public void restore(final Connection connection, final BulkMigrationJobPlan plan,
			final Throwable failure) throws SQLException {
		save(plan, BulkMigrationMaintenanceStatus.RESTORING, null);
		try {
			delegate.restore(connection, plan, failure);
			save(plan, BulkMigrationMaintenanceStatus.RESTORED, null);
		} catch (SQLException | RuntimeException | Error restoreFailure) {
			try {
				save(plan, BulkMigrationMaintenanceStatus.RESTORE_FAILED,
						message(restoreFailure));
			} catch (SQLException stateFailure) {
				restoreFailure.addSuppressed(stateFailure);
			}
			throw restoreFailure;
		}
	}

	private void save(final BulkMigrationJobPlan plan,
			final BulkMigrationMaintenanceStatus status, final String failureMessage)
			throws SQLException {
		store.save(new BulkMigrationMaintenanceState(plan.getFingerprint(), status,
				Instant.now(clock), failureMessage));
	}

	private static String message(final Throwable failure) {
		final String value = failure.getMessage();
		return value == null || value.isBlank() ? failure.getClass().getName() : value;
	}
}
