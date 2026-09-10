/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.time.Instant;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Process-local lease store intended for tests and single-process execution. */
public final class InMemoryBulkMigrationJobLeaseStore
		implements BulkMigrationJobLeaseStore {
	private final ConcurrentMap<String, BulkMigrationJobLease> leases =
			new ConcurrentHashMap<>();

	@Override
	public Optional<BulkMigrationJobLease> load(final String jobId) {
		requireId(jobId, "jobId");
		return Optional.ofNullable(leases.get(jobId));
	}

	@Override
	public boolean tryAcquire(final BulkMigrationJobLease lease, final Instant now) {
		Objects.requireNonNull(lease, "lease");
		Objects.requireNonNull(now, "now");
		if (lease.isExpiredAt(now)) {
			throw new IllegalArgumentException("acquired lease must expire after now");
		}
		final AtomicBoolean acquired = new AtomicBoolean();
		leases.compute(lease.jobId(), (key, current) -> {
			if (current == null || current.isExpiredAt(now)) {
				acquired.set(true);
				return lease;
			}
			return current;
		});
		return acquired.get();
	}

	@Override
	public boolean renew(final BulkMigrationJobLease lease, final Instant now) {
		Objects.requireNonNull(lease, "lease");
		Objects.requireNonNull(now, "now");
		if (lease.isExpiredAt(now)) {
			throw new IllegalArgumentException("renewed lease must expire after now");
		}
		final AtomicBoolean renewed = new AtomicBoolean();
		leases.computeIfPresent(lease.jobId(), (key, current) -> {
			if (current.ownerId().equals(lease.ownerId())
					&& current.planFingerprint().equals(lease.planFingerprint())
					&& !current.isExpiredAt(now)) {
				renewed.set(true);
				return lease;
			}
			return current;
		});
		return renewed.get();
	}

	@Override
	public void release(final String jobId, final String ownerId) {
		requireId(jobId, "jobId");
		requireId(ownerId, "ownerId");
		leases.computeIfPresent(jobId, (key, current) ->
				current.ownerId().equals(ownerId) ? null : current);
	}

	private static void requireId(final String value, final String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be empty");
		}
	}
}
