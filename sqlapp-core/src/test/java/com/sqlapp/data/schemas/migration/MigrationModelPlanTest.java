/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.migration.MigrationModel.Materialization;

class MigrationModelPlanTest {

	private static MigrationModel model(final String name, final String sql) {
		return new MigrationModel(name, sql, Materialization.VIEW);
	}

	private static List<String> names(final List<MigrationModel> models) {
		return models.stream().map(MigrationModel::name).toList();
	}

	private static final MigrationModelSource RAW = MigrationModelSource.table("raw_customer", "main", "customer");

	@Test
	void ordersByDependencyAndKeepsDeclarationOrderOtherwise() {
		final var plan = MigrationModelPlan.of(List.of(
				model("report", "SELECT * FROM /*$ref('customer')*/customer JOIN /*$ref('region')*/region USING (id)"),
				model("region", "SELECT 1 AS id"),
				model("customer", "SELECT * FROM /*$ref('STG_CUSTOMER')*/stg_customer"),
				model("stg_customer", "SELECT * FROM /*$source('raw_customer')*/customer")), List.of(RAW));

		assertEquals(List.of("region", "stg_customer", "customer", "report"), names(plan.models()));
		assertEquals(List.of("customer", "region"), plan.dependencies("report"));
		assertEquals(List.of("stg_customer"), plan.dependencies("customer"));
		assertEquals(List.of("raw_customer"), plan.sourceDependencies("stg_customer"));
		assertEquals(List.of("stg_customer", "report"), names(plan.select(Set.of("report", "STG_CUSTOMER"))));
	}

	@Test
	void rejectsInvalidDefinitions() {
		final var cycle = assertThrows(IllegalArgumentException.class, () -> MigrationModelPlan.of(List.of(
				model("a", "SELECT * FROM /*$ref('b')*/b"), model("b", "SELECT * FROM /*$ref('c')*/c"),
				model("c", "SELECT * FROM /*$ref('a')*/a"), model("d", "SELECT 1")), List.of()));
		assertTrue(cycle.getMessage().contains("a -> b -> c -> a"), cycle.getMessage());

		assertThrows(IllegalArgumentException.class,
				() -> MigrationModelPlan.of(List.of(model("a", "SELECT * FROM /*$ref('a')*/a")), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> MigrationModelPlan.of(List.of(model("a", "SELECT 1"), model("A", "SELECT 2")), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> MigrationModelPlan.of(List.of(model("raw_customer", "SELECT 1")), List.of(RAW)));
		assertThrows(IllegalArgumentException.class, () -> model("bad name", "SELECT 1"));
		assertThrows(IllegalArgumentException.class, () -> new MigrationModelSource("s", null, "t", "f.csv", null));

		final var unknown = assertThrows(IllegalArgumentException.class, () -> MigrationModelPlan
				.of(List.of(model("a", "SELECT * FROM /*$ref('raw_customer')*/customer")), List.of(RAW)));
		assertTrue(unknown.getMessage().contains("source('raw_customer')"), unknown.getMessage());
		assertThrows(IllegalArgumentException.class, () -> MigrationModelPlan
				.of(List.of(model("a", "SELECT * FROM /*$source('missing')*/missing")), List.of(RAW)));
	}

	@Test
	void selectsModifiedModelsAndTheirDownstream() {
		final List<MigrationModel> before = List.of(model("stg", "SELECT * FROM /*$source('raw_customer')*/c"),
				model("customer", "SELECT * FROM /*$ref('stg')*/stg"), model("other", "SELECT 1 AS id"));
		final var previous = MigrationModelPlan.of(before, List.of(RAW));

		assertEquals(previous.fingerprint(), MigrationModelPlan.of(before, List.of(RAW)).fingerprint());
		assertTrue(MigrationModelPlan.of(before, List.of(RAW)).modifiedAndDownstream(previous.nodeManifest())
				.isEmpty());

		final var sqlChanged = MigrationModelPlan.of(List.of(
				model("stg", "SELECT id FROM /*$source('raw_customer')*/c"),
				model("customer", "SELECT * FROM /*$ref('stg')*/stg"), model("other", "SELECT 1 AS id"),
				model("added", "SELECT 2 AS id")), List.of(RAW));
		assertEquals(Set.of("stg", "customer", "added"),
				sqlChanged.modifiedAndDownstream(previous.nodeManifest()));
		assertNotEquals(previous.fingerprint(), sqlChanged.fingerprint());

		final var sourceChanged = MigrationModelPlan.of(before,
				List.of(MigrationModelSource.table("raw_customer", "main", "customer_v2")));
		assertEquals(Set.of("stg", "customer"), sourceChanged.modifiedAndDownstream(previous.nodeManifest()));

		final var materializationChanged = MigrationModelPlan.of(List.of(before.get(0), before.get(1),
				new MigrationModel("other", "SELECT 1 AS id", Materialization.TABLE)), List.of(RAW));
		assertEquals(Set.of("other"), materializationChanged.modifiedAndDownstream(previous.nodeManifest()));
	}
}
