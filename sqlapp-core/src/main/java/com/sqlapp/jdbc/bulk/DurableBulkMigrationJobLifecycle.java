/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

	/** Reads lifecycle state without changing the database or state store. */
	public Optional<BulkMigrationMaintenanceState> inspect(
			final BulkMigrationJobPlan plan) throws SQLException {
		validatePlan(plan);
		return store.load(plan.getFingerprint());
	}

	/**
	 * Explicitly restores a nonterminal lifecycle after verifying the approved
	 * plan fingerprint.
	 */
	public BulkMigrationMaintenanceRecoveryResult recoverInterrupted(
			final Connection connection, final BulkMigrationJobPlan plan,
			final String expectedFingerprint) throws SQLException {
		validatePlan(plan);
		if (!plan.getFingerprint().equals(expectedFingerprint)) {
			throw new IllegalArgumentException(
					"Expected fingerprint does not match the migration plan");
		}
		final Optional<BulkMigrationMaintenanceState> optional =
				store.load(plan.getFingerprint());
		if (optional.isEmpty()) {
			return new BulkMigrationMaintenanceRecoveryResult(null, null, false);
		}
		final BulkMigrationMaintenanceState previous = optional.get();
		if (previous.status() == BulkMigrationMaintenanceStatus.COMPLETE
				|| previous.status() == BulkMigrationMaintenanceStatus.RESTORED) {
			return new BulkMigrationMaintenanceRecoveryResult(previous, previous, false);
		}
		final IllegalStateException interrupted = new IllegalStateException(
				"Recovering interrupted migration maintenance from " + previous.status());
		restore(connection, plan, interrupted);
		final BulkMigrationMaintenanceState current = store.load(plan.getFingerprint())
				.orElseThrow(() -> new SQLException(
						"Recovered maintenance state was not persisted"));
		return new BulkMigrationMaintenanceRecoveryResult(previous, current, true);
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

	private void validatePlan(final BulkMigrationJobPlan plan) {
		Objects.requireNonNull(plan, "plan");
		plan.validateUnchanged();
		if (plan.getLifecycle() != this) {
			throw new IllegalArgumentException(
					"The plan was not created with this durable lifecycle");
		}
	}

	private static String message(final Throwable failure) {
		final String value = failure.getMessage();
		final String message = value == null || value.isBlank()
				? failure.getClass().getName() : value;
		return message.length() <= BulkMigrationMaintenanceState.FAILURE_MESSAGE_MAX_LENGTH
				? message : message.substring(0,
						BulkMigrationMaintenanceState.FAILURE_MESSAGE_MAX_LENGTH);
	}
}
