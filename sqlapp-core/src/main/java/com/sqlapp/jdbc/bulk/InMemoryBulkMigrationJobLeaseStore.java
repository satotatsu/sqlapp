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
	public Optional<BulkMigrationJobLease> load(final String planFingerprint) {
		requireId(planFingerprint, "planFingerprint");
		return Optional.ofNullable(leases.get(planFingerprint));
	}

	@Override
	public boolean tryAcquire(final BulkMigrationJobLease lease, final Instant now) {
		Objects.requireNonNull(lease, "lease");
		Objects.requireNonNull(now, "now");
		if (lease.isExpiredAt(now)) {
			throw new IllegalArgumentException("acquired lease must expire after now");
		}
		final AtomicBoolean acquired = new AtomicBoolean();
		leases.compute(lease.planFingerprint(), (key, current) -> {
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
		leases.computeIfPresent(lease.planFingerprint(), (key, current) -> {
			if (current.ownerId().equals(lease.ownerId()) && !current.isExpiredAt(now)) {
				renewed.set(true);
				return lease;
			}
			return current;
		});
		return renewed.get();
	}

	@Override
	public void release(final String planFingerprint, final String ownerId) {
		requireId(planFingerprint, "planFingerprint");
		requireId(ownerId, "ownerId");
		leases.computeIfPresent(planFingerprint, (key, current) ->
				current.ownerId().equals(ownerId) ? null : current);
	}

	private static void requireId(final String value, final String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be empty");
		}
	}
}
