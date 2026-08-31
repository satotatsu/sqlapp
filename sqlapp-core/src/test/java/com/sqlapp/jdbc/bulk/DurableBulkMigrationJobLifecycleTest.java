/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DurableBulkMigrationJobLifecycleTest {
	private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

	@Test
	void recordsSuccessfulLifecycleTransitions() throws Exception {
		final var store = new RecordingStore();
		final var lifecycle = lifecycle(BulkMigrationJobLifecycle.NO_OP, store);
		final var plan = BulkMigrationJobPlanner.plan(List.of(), lifecycle);

		BulkMigrationJobExecutor.executePlan(connection(), plan);

		assertEquals(List.of(BulkMigrationMaintenanceStatus.PREPARING,
				BulkMigrationMaintenanceStatus.PREPARED,
				BulkMigrationMaintenanceStatus.POST_PROCESSING,
				BulkMigrationMaintenanceStatus.COMPLETE), store.statuses());
		assertEquals(NOW, store.states.get(0).updatedAt());
	}

	@Test
	void recordsRestoreFailureWithoutMaskingIt() {
		final var store = new RecordingStore();
		final BulkMigrationJobLifecycle delegate = new BulkMigrationJobLifecycle() {
			@Override
			public void before(Connection connection, BulkMigrationJobPlan plan)
					throws SQLException {
				throw new SQLException("prepare failed");
			}

			@Override
			public void restore(Connection connection, BulkMigrationJobPlan plan,
					Throwable failure) throws SQLException {
				throw new SQLException("restore failed");
			}
		};
		final var plan = BulkMigrationJobPlanner.plan(List.of(), lifecycle(delegate, store));

		final SQLException failure = assertThrows(SQLException.class,
				() -> BulkMigrationJobExecutor.executePlan(connection(), plan));

		assertEquals("prepare failed", failure.getMessage());
		assertEquals("restore failed", failure.getSuppressed()[0].getMessage());
		assertEquals(List.of(BulkMigrationMaintenanceStatus.PREPARING,
				BulkMigrationMaintenanceStatus.RESTORING,
				BulkMigrationMaintenanceStatus.RESTORE_FAILED), store.statuses());
		assertEquals("restore failed", store.states.get(2).failureMessage());
	}

	@Test
	void truncatesOnlyThePersistedRestoreFailureMessage() {
		final var store = new RecordingStore();
		final String message = "x".repeat(1_100);
		final BulkMigrationJobLifecycle delegate = new BulkMigrationJobLifecycle() {
			@Override
			public void before(Connection connection, BulkMigrationJobPlan plan)
					throws SQLException {
				throw new SQLException("prepare failed");
			}

			@Override
			public void restore(Connection connection, BulkMigrationJobPlan plan,
					Throwable failure) throws SQLException {
				throw new SQLException(message);
			}
		};
		final var plan = BulkMigrationJobPlanner.plan(List.of(), lifecycle(delegate, store));

		final SQLException failure = assertThrows(SQLException.class,
				() -> BulkMigrationJobExecutor.executePlan(connection(), plan));

		assertEquals(message, failure.getSuppressed()[0].getMessage());
		assertEquals(1_000, store.states.get(2).failureMessage().length());
	}

	@Test
	void explicitlyRecoversOnlyAnApprovedNonterminalPlan() throws Exception {
		final var events = new ArrayList<String>();
		final var store = new RecordingStore();
		final BulkMigrationJobLifecycle delegate = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "recover-v1";
			}

			@Override
			public void restore(Connection connection, BulkMigrationJobPlan plan,
					Throwable failure) {
				events.add(failure.getMessage());
			}
		};
		final var lifecycle = lifecycle(delegate, store);
		final var plan = BulkMigrationJobPlanner.plan(List.of(), lifecycle);
		store.save(new BulkMigrationMaintenanceState(plan.getFingerprint(),
				BulkMigrationMaintenanceStatus.PREPARED, NOW, null));

		assertEquals(BulkMigrationMaintenanceStatus.PREPARED,
				lifecycle.inspect(plan).orElseThrow().status());
		assertThrows(IllegalArgumentException.class, () -> lifecycle.recoverInterrupted(
				connection(), plan, "wrong-fingerprint"));
		assertTrue(events.isEmpty());

		final var recovered = lifecycle.recoverInterrupted(connection(), plan,
				plan.getFingerprint());
		assertTrue(recovered.recovered());
		assertEquals(BulkMigrationMaintenanceStatus.PREPARED,
				recovered.previousState().status());
		assertEquals(BulkMigrationMaintenanceStatus.RESTORED,
				recovered.currentState().status());
		assertEquals(List.of("Recovering interrupted migration maintenance from PREPARED"),
				events);

		final var noOp = lifecycle.recoverInterrupted(connection(), plan,
				plan.getFingerprint());
		assertFalse(noOp.recovered());
		assertEquals(BulkMigrationMaintenanceStatus.RESTORED,
				noOp.currentState().status());
	}

	private static DurableBulkMigrationJobLifecycle lifecycle(
			final BulkMigrationJobLifecycle delegate, final RecordingStore store) {
		return new DurableBulkMigrationJobLifecycle(delegate, store,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static Connection connection() {
		return (Connection) java.lang.reflect.Proxy.newProxyInstance(
				DurableBulkMigrationJobLifecycleTest.class.getClassLoader(),
				new Class<?>[] { Connection.class },
				(proxy, method, args) -> { throw new UnsupportedOperationException(); });
	}

	private static final class RecordingStore
			implements BulkMigrationMaintenanceStateStore {
		private final List<BulkMigrationMaintenanceState> states = new ArrayList<>();

		@Override
		public java.util.Optional<BulkMigrationMaintenanceState> load(String fingerprint) {
			return states.stream().filter(state -> state.planFingerprint().equals(fingerprint))
					.reduce((first, second) -> second);
		}

		@Override
		public void save(BulkMigrationMaintenanceState state) {
			states.add(state);
		}

		@Override
		public void delete(String fingerprint) {
		}

		private List<BulkMigrationMaintenanceStatus> statuses() {
			return states.stream().map(BulkMigrationMaintenanceState::status).toList();
		}
	}
}
