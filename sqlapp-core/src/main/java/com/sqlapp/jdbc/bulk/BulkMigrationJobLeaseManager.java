/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Acquires, renews, and owner-fences one migration job lease. */
public final class BulkMigrationJobLeaseManager {
	private final BulkMigrationJobLeaseStore store;
	private final String ownerId;
	private final Duration duration;
	private final Clock clock;

	public BulkMigrationJobLeaseManager(final BulkMigrationJobLeaseStore store,
			final String ownerId, final Duration duration) {
		this(store, ownerId, duration, Clock.systemUTC());
	}

	BulkMigrationJobLeaseManager(final BulkMigrationJobLeaseStore store,
			final String ownerId, final Duration duration, final Clock clock) {
		this.store = Objects.requireNonNull(store, "store");
		if (ownerId == null || ownerId.isBlank()) {
			throw new IllegalArgumentException("ownerId must not be empty");
		}
		if (ownerId.length() > BulkMigrationJobLease.ID_MAX_LENGTH) {
			throw new IllegalArgumentException("ownerId is too long");
		}
		this.ownerId = ownerId;
		this.duration = Objects.requireNonNull(duration, "duration");
		if (duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException("duration must be positive");
		}
		if (duration.compareTo(Duration.ofNanos(2)) < 0) {
			throw new IllegalArgumentException("duration must be at least 2 nanoseconds");
		}
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public LeaseHandle acquire(final String planFingerprint) throws SQLException {
		final Instant now = Instant.now(clock);
		final BulkMigrationJobLease lease = lease(planFingerprint, now);
		if (!store.tryAcquire(lease, now)) {
			throw new BulkMigrationJobLeaseUnavailableException(planFingerprint);
		}
		return new LeaseHandle(lease);
	}

	private BulkMigrationJobLease lease(final String planFingerprint,
			final Instant now) {
		return new BulkMigrationJobLease(planFingerprint, ownerId, now.plus(duration));
	}

	public final class LeaseHandle implements AutoCloseable {
		private BulkMigrationJobLease lease;
		private boolean closed;

		private LeaseHandle(final BulkMigrationJobLease lease) {
			this.lease = lease;
		}

		public synchronized BulkMigrationJobLease getLease() {
			return lease;
		}

		public synchronized void renew() throws SQLException {
			if (closed) {
				throw new IllegalStateException("Migration job lease is already closed");
			}
			final Instant now = Instant.now(clock);
			final BulkMigrationJobLease renewed = lease(lease.planFingerprint(), now);
			if (!store.renew(renewed, now)) {
				throw new BulkMigrationJobLeaseUnavailableException(
						lease.planFingerprint());
			}
			lease = renewed;
		}

		public BulkMigrationJobLeaseHeartbeat startHeartbeat() {
			Duration interval = duration.dividedBy(3);
			if (interval.isZero()) {
				interval = Duration.ofNanos(1);
			}
			return startHeartbeat(interval);
		}

		public BulkMigrationJobLeaseHeartbeat startHeartbeat(
				final Duration interval) {
			Objects.requireNonNull(interval, "interval");
			if (interval.isZero() || interval.isNegative()
					|| interval.compareTo(duration) >= 0) {
				throw new IllegalArgumentException(
						"heartbeat interval must be positive and shorter than lease duration");
			}
			return new BulkMigrationJobLeaseHeartbeat(this, interval);
		}

		@Override
		public synchronized void close() throws SQLException {
			if (!closed) {
				store.release(lease.planFingerprint(), lease.ownerId());
				closed = true;
			}
		}
	}
}
