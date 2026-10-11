/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MigrationSnapshotPlannerTest {

	@Test
	void plansInsertNewVersionExpiryAndUnchangedRowsInSourceOrder() {
		final var definition = definition(true);
		final var source = List.of(row(1, "same", 10), row(2, "changed", 20), row(3, "new", 30));
		final var current = List.of(row(1, "same", 10), row(2, "old", 20), row(4, "removed", 40));

		final var plan = MigrationSnapshotPlanner.plan(definition, Instant.parse("2026-09-16T00:00:00Z"), source,
				current);

		assertEquals(1, plan.unchangedRows());
		assertEquals(
				List.of(MigrationSnapshotChange.Type.UPDATE_VERSION, MigrationSnapshotChange.Type.INSERT,
						MigrationSnapshotChange.Type.EXPIRE),
				plan.changes().stream().map(MigrationSnapshotChange::type).toList());
		assertEquals(Map.of("ID", 2), plan.changes().get(0).key());
		assertEquals(1, plan.count(MigrationSnapshotChange.Type.INSERT));
		assertEquals(1, plan.count(MigrationSnapshotChange.Type.UPDATE_VERSION));
		assertEquals(1, plan.count(MigrationSnapshotChange.Type.EXPIRE));
	}

	@Test
	void canRetainRowsMissingFromTheSource() {
		final var plan = MigrationSnapshotPlanner.plan(definition(false), Instant.EPOCH, List.of(),
				List.of(row(1, "retained", 10)));

		assertEquals(List.of(), plan.changes());
	}

	@Test
	void rejectsDuplicateKeysAndMissingTrackedColumns() {
		final var definition = definition(true);
		assertThrows(IllegalArgumentException.class, () -> MigrationSnapshotPlanner.plan(definition, Instant.EPOCH,
				List.of(row(1, "a", 1), row(1, "b", 2)), List.of()));
		assertThrows(IllegalArgumentException.class, () -> MigrationSnapshotPlanner.plan(definition, Instant.EPOCH,
				List.of(Map.of("ID", 1, "NAME", "a")), List.of()));
		final Map<String, Object> nullKey = row(1, "a", 1);
		nullKey.put("ID", null);
		assertThrows(IllegalArgumentException.class,
				() -> MigrationSnapshotPlanner.plan(definition, Instant.EPOCH, List.of(nullKey), List.of()));
	}

	@Test
	void comparesArrayValuesByContent() {
		final var definition = definition(true);
		final Map<String, Object> source = row(1, "same", 10);
		final Map<String, Object> current = row(1, "same", 10);
		source.put("PAYLOAD", new byte[] { 1, 2 });
		current.put("PAYLOAD", new byte[] { 1, 2 });

		final var plan = MigrationSnapshotPlanner.plan(definition, Instant.EPOCH, List.of(source), List.of(current));

		assertEquals(1, plan.unchangedRows());
	}

	private static MigrationSnapshotDefinition definition(final boolean expireMissing) {
		return new MigrationSnapshotDefinition("customer-history", "CUSTOMER_HISTORY", List.of("ID"),
				List.of("NAME", "AMOUNT", "PAYLOAD"), "VALID_FROM", "VALID_TO", "IS_CURRENT", expireMissing);
	}

	private static Map<String, Object> row(final int id, final String name, final int amount) {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("ID", id);
		row.put("NAME", name);
		row.put("AMOUNT", amount);
		row.put("PAYLOAD", null);
		return row;
	}
}
