/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.View;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentProvider;

class OracleMigrationAssessmentProviderTest {
	private final OracleMigrationAssessmentProvider provider = new OracleMigrationAssessmentProvider();

	private Schema schema(final int version) {
		return new Schema("業務").setProductName("Oracle").setProductMajorVersion(version);
	}

	@Test
	void distinguishesUnsupportedDirectUpgradeFromLogicalMigration() {
		for (final int version : List.of(10, 11, 12, 18)) {
			final var direct = provider.assess(List.of(schema(version)), "26ai", Method.DIRECT_UPGRADE);
			assertTrue(direct.hasBlockers());
			assertEquals(Evidence.DOCUMENTED_RULE, direct.findings().getFirst().evidence());
			final var logical = provider.assess(List.of(schema(version)), "26ai", Method.LOGICAL_MIGRATION);
			assertFalse(logical.hasBlockers());
			assertTrue(logical.requiresReview());
		}
	}

	@Test
	void newerReleasesStillRequireReviewAndUnknownReleasesAreRejected() {
		for (final int version : List.of(19, 21, 23, 26)) {
			final var result = provider.assess(List.of(schema(version)), "26ai", Method.DIRECT_UPGRADE);
			assertFalse(result.hasBlockers());
			assertTrue(result.requiresReview());
		}
		assertThrows(IllegalArgumentException.class,
				() -> provider.assess(List.of(schema(27)), "26ai", Method.DIRECT_UPGRADE));
		assertThrows(IllegalArgumentException.class,
				() -> provider.assess(List.of(new Schema("S").setProductName("Oracle")), "26ai", Method.DIRECT_UPGRADE));
		assertThrows(IllegalArgumentException.class,
				() -> provider.assess(List.of(schema(10)), "19c", Method.DIRECT_UPGRADE));
		assertThrows(IllegalArgumentException.class,
				() -> provider.assess(List.of(schema(10), schema(10).setProductName("PostgreSQL")), "26ai", Method.DIRECT_UPGRADE));
		assertThrows(NullPointerException.class, () -> provider.assess(List.of(schema(10)), "26ai", null));
	}

	@Test
	void inventoriesObjectsAndKeepsSchemaEvidenceSeparateWithoutMutation() {
		final var schema = schema(10);
		schema.getViews().add(new View("V.with.dot").setValid(false));
		schema.getDbLinks().add(object -> object.setName("REMOTE"));
		schema.getSynonyms().add(object -> object.setName("ALIAS"));
		schema.getSequences().add(object -> object.setName("SEQ"));
		schema.getPackages().add(object -> object.setName("PKG"));
		final var before = schema.clone();
		final var result = provider.assess(List.of(schema), "26ai", Method.LOGICAL_MIGRATION);
		assertEquals(before, schema);
		final var invalid = result.findings().stream().filter(f -> f.ruleId().equals("oracle.object.invalid")).findFirst().orElseThrow();
		assertEquals("V.with.dot", invalid.object().name());
		assertEquals("業務", invalid.object().schema());
		assertEquals(Evidence.SCHEMA, invalid.evidence());
		assertEquals(Severity.WARNING, invalid.severity());
		assertTrue(result.inventory().stream().anyMatch(i -> i.type().equals("views") && i.count() == 1));
		assertTrue(result.findings().stream().anyMatch(f -> f.object() != null && "REMOTE".equals(f.object().name())
				&& f.action().contains("authentication")));
		assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("oracle.environment.snapshot-coverage")));
	}

	@Test
	void discoversProviderAndRejectsUnimplementedTargets() {
		assertInstanceOf(OracleMigrationAssessmentProvider.class, MigrationAssessmentProvider.resolve("Oracle", "26ai"));
		assertThrows(IllegalArgumentException.class, () -> MigrationAssessmentProvider.resolve("Oracle", "19c"));
	}

	@Test
	void acceptsEitherDefinitionOrStatementWithoutPublishingSql() {
		final var schema = schema(10);
		schema.getViews().add(new View("MISSING").addStatement("  "));
		schema.getViews().add(new View("BODY").addStatement("select 1 from dual"));
		schema.getViews().add(new View("DDL").addDefinition("CREATE VIEW DDL AS SELECT 1 FROM DUAL"));
		final var result = provider.assess(List.of(schema), "26ai", Method.LOGICAL_MIGRATION);
		assertEquals(List.of("MISSING"), result.findings().stream()
				.filter(f -> f.ruleId().equals("oracle.object.missing-sql")).map(f -> f.object().name()).toList());
		assertFalse(result.toString().contains("select 1 from dual"));
	}
}
