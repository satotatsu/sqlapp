/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.migration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;

class AccessOracleMigrationAssessmentTest {
	@Test
	void serviceProviderRejectsUnimplementedPairsAndVersions() {
		final var provider = com.sqlapp.data.schemas.migration.assessment.DatabaseMigrationAssessmentProvider
				.resolve("Microsoft Access", "ORACLE", "19C");
		assertInstanceOf(OracleDatabaseMigrationAssessmentProvider.class, provider);
		assertFalse(provider.supports("PostgreSQL", "oracle", "19c"));
		assertFalse(provider.supports("Microsoft Access", "postgres", "19c"));
		assertFalse(provider.supports("Microsoft Access", "oracle", "future"));
		assertFalse(provider.supports("Microsoft Access", "oracle", null));
		final var source = new com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSource(
				java.util.List.of(schema("BOOLEAN")), new MigrationAssessment(java.util.List.of(), java.util.List.of()), false, true);
		assertTrue(provider.assess(source, "19c").findings().stream().anyMatch(f -> f.ruleId().equals("access.oracle.environment")));
	}
	private Schema schema(final String type) {
		final var schema = new Schema("source").setProductName("Microsoft Access");
		final var table = new Table("顧客");
		final var column = new Column("値");
		column.getSpecifics().put("access.sourceType", type);
		table.getColumns().add(column);
		schema.getTables().add(table);
		return schema;
	}

	@Test
	void booleanAdviceRespectsTargetVersionWithoutMutatingSource() {
		final var schema = schema("BOOLEAN");
		final var before = schema.clone();
		for (final String version : new String[] { "19c", "21c", "23ai", "26ai" }) {
			final var result = AccessOracleMigrationAssessment.assess(schema, version);
			final var type = result.findings().stream().filter(f -> f.ruleId().equals("access.oracle.type")).findFirst().orElseThrow();
			assertTrue(type.action().contains(version.equals("19c") || version.equals("21c") ? "NUMBER(1)" : "BOOLEAN"));
			assertEquals(MigrationAssessment.Severity.REVIEW, type.severity());
			assertFalse(result.hasBlockers());
		}
		assertEquals(before, schema);
	}

	@Test
	void blocksComplexAndUnknownTypesInsteadOfCallingThemBlobCompatible() {
		for (final String type : new String[] { "COMPLEX_TYPE", "UNSUPPORTED" }) {
			final var result = AccessOracleMigrationAssessment.assess(schema(type), "19c");
			assertTrue(result.hasBlockers());
			assertTrue(result.findings().stream().anyMatch(f -> f.severity() == MigrationAssessment.Severity.BLOCKER
					&& "顧客".equals(f.object().table()) && "値".equals(f.object().name())));
		}
		assertTrue(AccessOracleMigrationAssessment.assess(schema(null), "19c").hasBlockers());
	}

	@Test
	void identifiesTextAndExpressionAndIdentityRisks() {
		final var schema = schema("TEXT");
		final var column = schema.getTables().getFirst().getColumns().getFirst();
		column.setDefaultValue("Nz([other], '')").setIdentity(true);
		final var rules = AccessOracleMigrationAssessment.assess(schema, "19c").findings().stream()
				.map(MigrationAssessment.Finding::ruleId).toList();
		assertTrue(rules.containsAll(java.util.List.of("access.oracle.empty-string", "access.oracle.column-expression",
				"access.oracle.autonumber", "access.oracle.primary-key", "access.oracle.identifier")));
	}

	@Test
	void rejectsUnknownTargetsAndOtherSources() {
		for (final String version : new String[] { null, "", "12c", "19", "future" }) {
			assertThrows(IllegalArgumentException.class, () -> AccessOracleMigrationAssessment.assess(schema("TEXT"), version));
		}
		assertThrows(IllegalArgumentException.class, () -> AccessOracleMigrationAssessment.assess(new Schema("x"), "19c"));
		assertEquals("26ai", AccessOracleMigrationAssessment.validateTargetVersion("26AI"));
	}
}
