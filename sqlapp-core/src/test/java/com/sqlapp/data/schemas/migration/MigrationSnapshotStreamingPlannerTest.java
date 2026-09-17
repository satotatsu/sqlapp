/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MigrationSnapshotStreamingPlannerTest {

	@Test
	void mergePlansOrderedInputsWithoutMaterializingThem() {
		final List<MigrationSnapshotChange> changes = new ArrayList<>();

		final var summary = MigrationSnapshotStreamingPlanner.plan(definition(),
				List.of(row(1, "same"), row(2, "changed"), row(4, "new")),
				List.of(row(1, "same"), row(2, "old"), row(3, "removed")), changes::add);

		assertEquals(new MigrationSnapshotSummary(1, 1, 1, 1), summary);
		assertEquals(
				List.of(MigrationSnapshotChange.Type.UPDATE_VERSION, MigrationSnapshotChange.Type.EXPIRE,
						MigrationSnapshotChange.Type.INSERT),
				changes.stream().map(MigrationSnapshotChange::type).toList());
	}

	@Test
	void rejectsUnsortedOrDuplicateInput() {
		assertThrows(IllegalArgumentException.class, () -> MigrationSnapshotStreamingPlanner.plan(definition(),
				List.of(row(2, "a"), row(1, "b")), List.of(), ignored -> {
				}));
		assertThrows(IllegalArgumentException.class, () -> MigrationSnapshotStreamingPlanner.plan(definition(),
				List.of(row(1, "a"), row(1, "b")), List.of(), ignored -> {
				}));
	}

	private static MigrationSnapshotDefinition definition() {
		return new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"), List.of("NAME"),
				"VALID_FROM", "VALID_TO", "IS_CURRENT", true);
	}

	private static Map<String, Object> row(final int id, final String name) {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("ID", id);
		row.put("NAME", name);
		return row;
	}
}
