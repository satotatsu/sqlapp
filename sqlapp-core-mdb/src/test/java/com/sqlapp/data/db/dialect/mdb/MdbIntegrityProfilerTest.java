/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.*;
import io.github.spannm.jackcess.*;

class MdbIntegrityProfilerTest {
	@TempDir
	Path directory;

	@Test
	void scansCompositeOrphansAndCountsNullRowsWithoutFollowingLinks() throws Exception {
		final Path file = directory.resolve("relationships.accdb");
		try (var db = DatabaseBuilder.create(Database.FileFormat.V2010, file.toFile())) {
			final var parent = new TableBuilder("Parent").addColumn(new ColumnBuilder("A", DataType.LONG))
					.addColumn(new ColumnBuilder("B", DataType.LONG)).toTable(db);
			final var child = new TableBuilder("Child").addColumn(new ColumnBuilder("X", DataType.LONG))
					.addColumn(new ColumnBuilder("Y", DataType.LONG)).toTable(db);
			new RelationshipBuilder(parent, child).addColumns("A", "X").addColumns("B", "Y").withName("FK_child")
					.toRelationship(db);
			parent.addRow(1, 2);
			parent.addRow(2, 1);
			child.addRow(1, 2);
			child.addRow(1, 1);
			child.addRow(1, 1);
			child.addRow(null, 3);
			child.addRow(1, null);
		}
		final byte[] before = Files.readAllBytes(file);
		final var provider = new MdbMigrationAssessmentSourceProvider();
		assertNull(provider.load(file).dataProfile());
		final var source = provider.load(file, true);
		final var check = source.dataProfile().integrityChecks().getFirst();
		assertEquals(IntegrityKind.FOREIGN_KEY, check.kind());
		assertEquals(IntegrityCoverage.CHECKED, check.coverage());
		assertEquals(List.of("X", "Y"), check.columns());
		assertEquals(List.of("A", "B"), check.relatedColumns());
		assertEquals(3L, check.checkedRows());
		assertEquals(2L, check.nullRows());
		assertEquals(2L, check.violationRows());
		assertTrue(source.assessment().findings().stream().anyMatch(f -> f.ruleId().equals("access.data.orphan-key")));
		assertArrayEquals(before, Files.readAllBytes(file));
		assertEquals(source.dataProfile(), provider.load(file, true).dataProfile());
		try (var db = new DatabaseBuilder().withPath(file).open()) {
			db.createLinkedTable("External", "missing.accdb", "T");
		}
		final var linked = provider.load(file, true);
		assertFalse(linked.relationshipsCollected());
		assertTrue(linked.dataProfile().integrityChecks().isEmpty());
		assertTrue(linked.assessment().findings().stream()
				.anyMatch(f -> f.ruleId().equals("access.relationship-coverage")));
	}

	@Test
	void detectsDuplicatesAndPrimaryNullsFromCanonicalKeysWithoutTrustingIndexes() throws Exception {
		final Path file = directory.resolve("duplicates.accdb");
		try (var db = DatabaseBuilder.create(Database.FileFormat.V2010, file.toFile())) {
			final var table = new TableBuilder("T")
					.addColumn(new ColumnBuilder("N", DataType.NUMERIC).withPrecision(10).withScale(2))
					.addColumn(new ColumnBuilder("B", DataType.BYTE)).toTable(db);
			table.addRow(new BigDecimal("1.00"), 255);
			table.addRow(new BigDecimal("1.0"), 255);
			table.addRow(new BigDecimal("1"), 255);
			table.addRow(new BigDecimal("1"), 1);
			table.addRow(null, 255);
		}
		final var schema = MdbFileLoader.loadForAssessment(file).schema();
		final var table = schema.getTables().get("T");
		// Model an intended key over existing invalid rows; the scanner must inspect
		// rows, not index metadata.
		table.setPrimaryKey("PK_T", table.getColumns().get("N"), table.getColumns().get("B"));
		final var result = scan(file, schema, 10);
		final var check = result.checks().getFirst();
		assertEquals(4L, check.checkedRows());
		assertEquals(2L, check.violationRows());
		assertEquals(1L, check.nullRows());
		assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("access.data.duplicate-key")));
		assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("access.data.primary-key-null")));
		final var limited = scan(file, schema, 1);
		assertEquals(IntegrityCoverage.LIMIT_EXCEEDED, limited.checks().getFirst().coverage());
		assertNull(limited.checks().getFirst().violationRows());
		assertTrue(limited.findings().stream()
				.anyMatch(f -> f.ruleId().equals("access.data.duplicate-key") && f.reason().startsWith("At least 2")));
	}

	@Test
	void reportsTextKeysAsUnsupportedAndDoesNotInferCaseSensitiveOrphans() throws Exception {
		final Path file = directory.resolve("text.accdb");
		try (var db = DatabaseBuilder.create(Database.FileFormat.V2010, file.toFile())) {
			final var parent = new TableBuilder("P").addColumn(new ColumnBuilder("ID", DataType.TEXT))
					.addIndex(new IndexBuilder("PK_P").withColumns("ID").withPrimaryKey()).toTable(db);
			final var child = new TableBuilder("C").addColumn(new ColumnBuilder("PID", DataType.TEXT)).toTable(db);
			parent.addRow("SensitiveValue");
			child.addRow("sensitivevalue");
			new RelationshipBuilder(parent, child).addColumns("ID", "PID").withName("FK_text").toRelationship(db);
		}
		final var result = new MdbMigrationAssessmentSourceProvider().load(file, true);
		assertEquals(2, result.dataProfile().integrityChecks().size());
		assertTrue(result.dataProfile().integrityChecks().stream()
				.allMatch(c -> c.coverage() == IntegrityCoverage.UNSUPPORTED && c.violationRows() == null));
		assertFalse(result.assessment().findings().stream().anyMatch(f -> f.ruleId().equals("access.data.orphan-key")));
		assertFalse(result.dataProfile().toString().contains("SensitiveValue"));
	}

	@Test
	void boundsDistinctKeysAndDoesNotPublishPartialCountsAsComplete() throws Exception {
		final Path file = directory.resolve("limits.accdb");
		try (var db = DatabaseBuilder.create(Database.FileFormat.V2010, file.toFile())) {
			final var parent = new TableBuilder("P").addColumn(new ColumnBuilder("ID", DataType.LONG))
					.addIndex(new IndexBuilder("PK_P").withColumns("ID").withPrimaryKey()).toTable(db);
			final var child = new TableBuilder("C").addColumn(new ColumnBuilder("PID", DataType.LONG)).toTable(db);
			parent.addRow(1);
			parent.addRow(2);
			child.addRow(3);
			new RelationshipBuilder(parent, child).addColumns("ID", "PID").withName("FK_limit").toRelationship(db);
		}
		final var schema = MdbFileLoader.loadForAssessment(file).schema();
		final var limited = scan(file, schema, 1);
		assertEquals(2, limited.checks().size());
		assertTrue(limited.checks().stream().allMatch(c -> c.coverage() == IntegrityCoverage.LIMIT_EXCEEDED
				&& c.checkedRows() == null && c.nullRows() == null && c.violationRows() == null));
		assertTrue(limited.findings().stream().allMatch(f -> f.ruleId().equals("access.data.integrity-not-checked")));
		assertTrue(scan(file, schema, 2).checks().stream().allMatch(c -> c.coverage() == IntegrityCoverage.CHECKED));
	}

	@Test
	void uniqueIndexesAndEmptyParentHaveExplicitCounts() throws Exception {
		final Path file = directory.resolve("empty.accdb");
		try (var db = DatabaseBuilder.create(Database.FileFormat.V2010, file.toFile())) {
			final var parent = new TableBuilder("P").addColumn(new ColumnBuilder("ID", DataType.LONG))
					.addIndex(new IndexBuilder("UQ_P").withColumns("ID").withUnique().withIgnoreNulls()).toTable(db);
			final var child = new TableBuilder("C").addColumn(new ColumnBuilder("PID", DataType.LONG)).toTable(db);
			child.addRow(1);
			child.addRow(new Object[] { null });
			new RelationshipBuilder(parent, child).addColumns("ID", "PID").withName("FK_empty").toRelationship(db);
		}
		final var checks = new MdbMigrationAssessmentSourceProvider().load(file, true).dataProfile().integrityChecks();
		final var unique = checks.stream().filter(c -> c.kind() == IntegrityKind.UNIQUE_KEY).findFirst().orElseThrow();
		assertEquals(0L, unique.checkedRows());
		assertEquals(0L, unique.violationRows());
		final var fk = checks.stream().filter(c -> c.kind() == IntegrityKind.FOREIGN_KEY).findFirst().orElseThrow();
		assertEquals(1L, fk.violationRows());
		assertEquals(1L, fk.nullRows());
	}

	private MdbIntegrityProfiler.Result scan(final Path file, final Schema schema, final int limit) throws Exception {
		try (var db = new DatabaseBuilder().withPath(file).withReadOnly(true).open()) {
			db.setDateTimeType(DateTimeType.LOCAL_DATE_TIME);
			db.setEvaluateExpressions(false);
			db.setLinkResolver((database, name) -> {
				throw new java.io.IOException("External access is prohibited");
			});
			return MdbIntegrityProfiler.scan(db, schema, limit);
		}
	}

	@Test
	void comparesGuidBooleanAndDateKeysAndRejectsFloatingPointKeys() throws Exception {
		final Path file = directory.resolve("types.accdb");
		try (var db = DatabaseBuilder.create(Database.FileFormat.V2019, file.toFile())) {
			final var table = new TableBuilder("T").addColumn(new ColumnBuilder("G", DataType.GUID))
					.addColumn(new ColumnBuilder("B", DataType.BOOLEAN))
					.addColumn(new ColumnBuilder("D", DataType.EXT_DATE_TIME))
					.addColumn(new ColumnBuilder("F", DataType.DOUBLE)).toTable(db);
			final var date = java.time.LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123456700);
			table.addRow("{12345678-1234-1234-1234-1234567890AB}", true, date, 1.0);
			table.addRow("{12345678-1234-1234-1234-1234567890ab}", true, date, 1.0);
			table.addRow("{12345678-1234-1234-1234-1234567890AB}", false, date, 1.0);
		}
		final var schema = MdbFileLoader.loadForAssessment(file).schema();
		final var table = schema.getTables().get("T");
		table.setPrimaryKey("PK_T", table.getColumns().get("G"), table.getColumns().get("B"),
				table.getColumns().get("D"));
		assertEquals(1L, scan(file, schema, 10).checks().getFirst().violationRows());
		table.getColumns().get("G").getSpecifics().put(MdbFileLoader.SOURCE_TYPE, "DOUBLE");
		assertEquals(IntegrityCoverage.UNSUPPORTED, scan(file, schema, 10).checks().getFirst().coverage());
	}

	@Test
	void selfReferencesAndIncompatibleParentTypesAreHandledExplicitly() throws Exception {
		final Path file = directory.resolve("self.accdb");
		try (var db = DatabaseBuilder.create(Database.FileFormat.V2010, file.toFile())) {
			final var table = new TableBuilder("Tree").addColumn(new ColumnBuilder("ID", DataType.LONG))
					.addColumn(new ColumnBuilder("ParentID", DataType.LONG)).toTable(db);
			new RelationshipBuilder(table, table).addColumns("ID", "ParentID").withName("FK_tree").toRelationship(db);
			table.addRow(1, null);
			table.addRow(2, 1);
			table.addRow(3, 99);
		}
		final var schema = MdbFileLoader.loadForAssessment(file).schema();
		assertEquals(1L, scan(file, schema, 10).checks().getFirst().violationRows());
		schema.getTables().get("Tree").getColumns().get("ParentID").getSpecifics().put(MdbFileLoader.SOURCE_TYPE,
				"BOOLEAN");
		assertEquals(IntegrityCoverage.UNSUPPORTED, scan(file, schema, 10).checks().getFirst().coverage());
	}
}
