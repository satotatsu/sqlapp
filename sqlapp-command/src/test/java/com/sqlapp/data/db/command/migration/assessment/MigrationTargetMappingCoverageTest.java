/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.ObjectId;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSource;
import com.sqlapp.data.schemas.migration.assessment.ResolvedMigrationTargetMapping;

class MigrationTargetMappingCoverageTest {
	@Test
	void warnsWhenObservedNegativeAutoNumbersAreMappedToSequentialIdentity() {
		final var schema = new Schema("source").setProductName("Microsoft Access");
		final var table = new Table("RandomKeys");
		final var sourceColumn = new Column("ID").setIdentity(true);
		sourceColumn.getSpecifics().put("access.sourceType", "LONG");
		table.getColumns().add(sourceColumn);
		schema.getTables().add(table);
		final var tableId = new ObjectId(null, "source", "table", "RandomKeys");
		final var columnId = new ObjectId(null, "source", "column", "ID", "RandomKeys");
		final var profile = new com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile(List.of(
				new com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.TableProfile(tableId, 2, List.of(
						new com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.ColumnProfile(columnId, "LONG",
								com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.Coverage.SCANNED, 0L, null,
								new com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.NumericStatistics(
										new java.math.BigDecimal("-2147483648"), new java.math.BigDecimal("17"), 10, 0, 0), null)))));
		final var mappedColumn = new ResolvedMigrationTargetMapping.ColumnMapping(columnId, "ID", "int", false,
				true, null, null);
		final var mapping = new ResolvedMigrationTargetMapping("fp", "sqlserver", "2022", List.of(
				new ResolvedMigrationTargetMapping.TableMapping(tableId, "dbo", "RandomKeys", List.of(mappedColumn))));
		final var assessment = new MigrationTargetMappingCoverage().assess(
				new MigrationAssessmentSource(List.of(schema), new MigrationAssessment(List.of(), List.of()), true, true,
						profile), mapping);
		final var finding = assessment.findings().stream()
				.filter(f -> f.ruleId().equals("migration.mapping.observed-negative-autonumber"))
				.findFirst().orElseThrow();
		assertEquals(MigrationAssessment.Severity.WARNING, finding.severity());
		assertEquals(MigrationAssessment.Evidence.DATABASE, finding.evidence());
		assertTrue(finding.action().contains("New Values uses Random"));
		assertEquals(1, assessment.inventory().stream().filter(i -> i.type().equals("mappingSemanticDifferences"))
				.findFirst().orElseThrow().count());
	}

	@Test
	void reportsMappedColumnSemanticDifferences() {
		final var schema = new Schema("source").setProductName("Microsoft Access");
		final var table = new Table("Sample");
		table.getColumns().add(new Column("ID").setIdentity(true));
		final var sourceValue = new Column("Value").setDefaultValue("7").setFormula("[ID] + 1");
		sourceValue.setCheck(">= 0");
		sourceValue.getSpecifics().put("access.allowZeroLength", "true");
		table.getColumns().add(sourceValue);
		table.getConstraints().addCheckConstraint("CK_SAMPLE", "[Value] >= 0");
		table.getConstraints().addForeignKeyConstraint("FK_SAMPLE_SELF", sourceValue, sourceValue)
				.setUpdateRule(CascadeRule.Cascade).setDeleteRule(CascadeRule.Cascade);
		final var ignoreNulls = new Index("IX_SAMPLE_VALUE", sourceValue);
		ignoreNulls.getSpecifics().put("IGNORE_NULLS", "true");
		table.getIndexes().add(ignoreNulls);
		table.getIndexes().add(new Index("UX_SAMPLE_VALUE", sourceValue).setUnique(true));
		schema.getTables().add(table);
		final var tableId = new ObjectId(null, "source", "table", "Sample");
		final var id = new ObjectId(null, "source", "column", "ID", "Sample");
		final var value = new ObjectId(null, "source", "column", "Value", "Sample");
		final var mapped = new ResolvedMigrationTargetMapping.TableMapping(tableId, "TARGET", "SAMPLE_T", List.of(
				new ResolvedMigrationTargetMapping.ColumnMapping(id, "ID", "int", true, null, null, null),
				new ResolvedMigrationTargetMapping.ColumnMapping(value, "VALUE", "int", false, null, "0", null)));
		final var assessment = new MigrationTargetMappingCoverage().assess(
				new MigrationAssessmentSource(List.of(schema), new MigrationAssessment(List.of(), List.of()), false, true),
				new ResolvedMigrationTargetMapping("fp", "target", "1", List.of(mapped)));

		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.identity-change")
				&& f.reason().contains("Access AutoNumber")
				&& f.action().contains("Increment or Random")
				&& f.action().contains("first new row")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.nullability-change")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.nullability-change")
				&& f.object().equals(id) && f.reason().contains("from required to nullable")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.default-change")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.allow-zero-length")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.calculated-expression")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.table-validation-expression")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.column-validation-expression")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.table-validation-expression")
				&& f.reason().startsWith("Mapped table changes")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.relationship-action")
				&& f.reason().startsWith("Mapped relationship changes")
				&& f.reason().contains("Access relationship actions to Sample")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.index-null-handling")
				&& f.reason().contains("Access IgnoreNulls")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.unique-index-design")
				&& f.reason().contains("Access standalone unique index")));
		assertEquals(11, assessment.inventory().stream().filter(i -> i.type().equals("mappingSemanticDifferences"))
				.findFirst().orElseThrow().count());
	}
}
