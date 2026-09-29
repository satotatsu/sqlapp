/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.ObjectId;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSource;
import com.sqlapp.data.schemas.migration.assessment.ResolvedMigrationTargetMapping;

class MigrationTargetMappingCoverageTest {
	@Test
	void reportsMappedColumnSemanticDifferences() {
		final var schema = new Schema("source").setProductName("Microsoft Access");
		final var table = new Table("Sample");
		table.getColumns().add(new Column("ID").setIdentity(true));
		final var sourceValue = new Column("Value").setDefaultValue("7");
		sourceValue.getSpecifics().put("access.allowZeroLength", "true");
		table.getColumns().add(sourceValue);
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

		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.identity-change")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.nullability-change")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.default-change")));
		assertTrue(assessment.findings().stream().anyMatch(f -> f.ruleId().equals("migration.mapping.allow-zero-length")));
		assertEquals(4, assessment.inventory().stream().filter(i -> i.type().equals("mappingSemanticDifferences"))
				.findFirst().orElseThrow().count());
	}
}
