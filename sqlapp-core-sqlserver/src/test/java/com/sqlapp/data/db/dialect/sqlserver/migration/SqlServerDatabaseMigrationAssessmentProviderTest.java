/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.migration;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.schemas.migration.assessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.*;

class SqlServerDatabaseMigrationAssessmentProviderTest {
	@Test
	void observedDatesWarnAboutLegacyDatetimeOnlyBelowItsBoundary() {
		for (final String date : List.of("1752-12-31T23:59:59", "1753-01-01T00:00:00")) {
			final var column = new ColumnProfile(new ObjectId(null, "source", "column", "C0", "顧客"),
					"SHORT_DATE_TIME", Coverage.SCANNED, 0L, null, null, new DateTimeStatistics(date, date, 0));
			final var profile = new MigrationDataProfile(List.of(new TableProfile(
					new ObjectId(null, "source", "table", "顧客"), 1, List.of(column))));
			final var source = new MigrationAssessmentSource(List.of(schema("SHORT_DATE_TIME")),
					new MigrationAssessment(List.of(), List.of()), true, true, profile);
			final var result = provider.assess(source, "2022");
			assertEquals(date.startsWith("1752") ? 1 : 0, rule(result, "observed-legacy-datetime-range").size());
			assertFalse(result.hasBlockers());
		}
	}
	private final SqlServerDatabaseMigrationAssessmentProvider provider = new SqlServerDatabaseMigrationAssessmentProvider();

	private Schema schema(String... types) {
		final var schema = new Schema("source").setProductName("Microsoft Access");
		final var table = new Table("顧客");
		for (int i = 0; i < types.length; i++) {
			final var column = new Column("C" + i);
			if (types[i] != null) { column.getSpecifics().put("access.sourceType", types[i]); }
			table.getColumns().add(column);
		}
		schema.getTables().add(table);
		return schema;
	}
	private MigrationAssessment assess(Schema schema) {
		return provider.assess(source(schema), "2022");
	}
	private MigrationAssessmentSource source(Schema schema) {
		return new MigrationAssessmentSource(List.of(schema), new MigrationAssessment(List.of(), List.of()), false, true);
	}
	private List<Finding> rule(MigrationAssessment assessment, String rule) {
		return assessment.findings().stream().filter(f -> f.ruleId().equals("access.sqlserver." + rule)).toList();
	}

	@Test
	void serviceResolutionAcceptsOnlyExplicitSupportedPairsAndVersions() {
		for (final String version : List.of("2016", "2017", "2019", "2022")) {
			assertInstanceOf(SqlServerDatabaseMigrationAssessmentProvider.class,
					DatabaseMigrationAssessmentProvider.resolve("Microsoft Access", "SQLSERVER", version));
			assertEquals(version, provider.normalizeTargetVersion(version));
			assertFalse(provider.assess(source(schema("TEXT")), version).hasBlockers());
		}
		for (final String version : new String[] { null, "", "2014", "2025", "16", "2022.1", "Azure" }) {
			assertFalse(provider.supports("Microsoft Access", "sqlserver", version));
			assertThrows(IllegalArgumentException.class, () -> provider.normalizeTargetVersion(version));
			assertThrows(IllegalArgumentException.class, () -> provider.assess(source(schema("TEXT")), version));
		}
		assertFalse(provider.supports("Oracle", "sqlserver", "2022"));
		assertFalse(provider.supports("Microsoft Access", "oracle", "2022"));
		assertThrows(IllegalArgumentException.class, () -> provider.assess(source(new Schema("s").setProductName("Oracle")), "2022"));
	}

	@Test
	void coversScalarTypesWithoutMutatingTheSchema() {
		final var mappings = Map.ofEntries(Map.entry("BOOLEAN", "bit"), Map.entry("BYTE", "tinyint"),
				Map.entry("INT", "smallint"), Map.entry("LONG", "int"), Map.entry("BIG_INT", "bigint"),
				Map.entry("MONEY", "decimal(19,4)"), Map.entry("NUMERIC", "decimal(p,s)"),
				Map.entry("FLOAT", "real"), Map.entry("DOUBLE", "float(53)"),
				Map.entry("SHORT_DATE_TIME", "datetime2"), Map.entry("EXT_DATE_TIME", "datetime2"),
				Map.entry("TEXT", "nvarchar(n)"), Map.entry("MEMO", "nvarchar(max)"),
				Map.entry("GUID", "uniqueidentifier"), Map.entry("BINARY", "varbinary"), Map.entry("OLE", "varbinary(max)"));
		for (final var mapping : mappings.entrySet()) {
			final var schema = schema(mapping.getKey());
			final var before = schema.clone();
			final var assessment = assess(schema);
			assertFalse(assessment.hasBlockers(), mapping.getKey());
			assertTrue(rule(assessment, "type").getFirst().action().contains(mapping.getValue()), mapping.getKey());
			assertEquals(before, schema);
			assertEquals(1, assessment.inventory().stream().filter(i -> i.type().equals("columns")).findFirst().orElseThrow().count());
		}
	}

	@Test
	void blocksComplexUnknownAndMissingNativeTypesWithColumnIdentity() {
		for (final String type : new String[] { "COMPLEX_TYPE", "FUTURE", null }) {
			final var assessment = assess(schema(type));
			final var blocker = assessment.findings().stream().filter(f -> f.severity() == Severity.BLOCKER).findFirst().orElseThrow();
			assertEquals("顧客", blocker.object().table());
			assertEquals("C0", blocker.object().name());
		}
	}

	@Test
	void distinguishesGuidAndNumericAutonumberAndRetainsExpressionReviews() {
		final var schema = schema("GUID", "LONG");
		for (final var column : schema.getTables().getFirst().getColumns()) { column.setIdentity(true); }
		schema.getTables().getFirst().getColumns().getFirst().setDefaultValue("GenGUID()");
		final var result = assess(schema);
		assertTrue(rule(result, "autonumber").get(0).action().contains("GUID generator"));
		assertFalse(rule(result, "autonumber").get(0).action().contains("IDENTITY_INSERT"));
		assertTrue(rule(result, "autonumber").get(1).action().contains("IDENTITY_INSERT"));
		assertEquals(1, rule(result, "column-expression").size());
	}

	@Test
	void reviewsUniqueNullsCascadesDatesAndTextWithoutOracleAssumptions() {
		final var schema = schema("TEXT", "SHORT_DATE_TIME");
		final var table = schema.getTables().getFirst();
		final var index = new Index("UK", table.getColumns().getFirst()).setUnique(true);
		index.getSpecifics().put("IGNORE_NULLS", "true");
		table.getIndexes().add(index);
		table.getConstraints().add(new ForeignKeyConstraint("FK").setUpdateRule(CascadeRule.Cascade));
		final var result = assess(schema);
		assertTrue(rule(result, "key").isEmpty());
		assertEquals(1, rule(result, "index-nulls").size());
		assertTrue(rule(result, "cascade").getFirst().action().contains("supports cascades"));
		assertTrue(rule(result, "datetime").getFirst().action().contains("datetime2"));
		assertTrue(rule(result, "text-semantics").getFirst().action().contains("NULL versus empty"));
		assertFalse(result.findings().stream().anyMatch(f -> f.ruleId().startsWith("access.oracle")));
	}

	@Test
	void identifierBoundaryIs128AndFindingsAreSchemaQualified() {
		final var schema = schema("TEXT");
		final var table = schema.getTables().getFirst();
		table.setName("A".repeat(128));
		assertTrue(rule(assess(schema), "identifier-length").isEmpty());
		table.setName("A".repeat(129));
		final var finding = rule(assess(schema), "identifier-length").getFirst();
		assertEquals(Severity.BLOCKER, finding.severity());
		assertEquals("source", finding.object().schema());
	}
}
