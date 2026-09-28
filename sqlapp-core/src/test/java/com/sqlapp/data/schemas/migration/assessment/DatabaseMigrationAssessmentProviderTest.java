/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class DatabaseMigrationAssessmentProviderTest {
	@Test
	void targetDdlIncludesOnlyFullyMappedPrimaryAndForeignKeys() {
		final var schema = new Schema("source").setProductName("Microsoft Access");
		final var parent = new Table("Parent");
		final var parentId = new Column("ID");
		parent.getColumns().add(parentId);
		parent.setPrimaryKey("PK_PARENT", parentId);
		final var child = new Table("Child");
		final var childId = new Column("ID");
		final var parentIdInChild = new Column("ParentID");
		child.getColumns().add(childId);
		child.getColumns().add(parentIdInChild);
		child.setPrimaryKey("PK_CHILD", childId);
		schema.getTables().add(parent);
		schema.getTables().add(child);
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT", parentIdInChild, parentId);
		final var source = new MigrationAssessmentSource(List.of(schema), EMPTY, false, true);
		final var parentMapping = new ResolvedMigrationTargetMapping.TableMapping(
				new MigrationAssessment.ObjectId(null, "source", "table", "Parent"), "TARGET", "PARENT_T",
				List.of(column("source", "Parent", "ID", "PARENT_ID")));
		final var childMapping = new ResolvedMigrationTargetMapping.TableMapping(
				new MigrationAssessment.ObjectId(null, "source", "table", "Child"), "TARGET", "CHILD_T",
				List.of(column("source", "Child", "ID", "CHILD_ID"),
						column("source", "Child", "ParentID", "PARENT_ID")));
		final var mapping = new ResolvedMigrationTargetMapping("fp", "target", "1", List.of(parentMapping, childMapping));
		final String ddl = MigrationTargetDdlGenerator.generate(source, mapping, value -> "[" + value + "]", "GO\n");
		assertTrue(ddl.contains("PRIMARY KEY ([PARENT_ID])"));
		assertTrue(ddl.contains("PRIMARY KEY ([CHILD_ID])"));
		assertTrue(ddl.contains("[PARENT_ID] int NOT NULL"));
		assertTrue(ddl.contains("ALTER TABLE [TARGET].[CHILD_T] ADD FOREIGN KEY ([PARENT_ID]) REFERENCES [TARGET].[PARENT_T] ([PARENT_ID]);"));
		assertFalse(ddl.contains("FK_CHILD_PARENT"));

		final var partialChild = new ResolvedMigrationTargetMapping.TableMapping(childMapping.sourceTable(), "TARGET", "CHILD_T",
				List.of(column("source", "Child", "ID", "CHILD_ID")));
		final String partial = MigrationTargetDdlGenerator.generate(source,
				new ResolvedMigrationTargetMapping("fp", "target", "1", List.of(parentMapping, partialChild)),
				value -> "[" + value + "]", "GO\n");
		assertFalse(partial.contains("ADD FOREIGN KEY"));
	}

	private static ResolvedMigrationTargetMapping.ColumnMapping column(final String schema, final String table,
			final String source, final String target) {
		return new ResolvedMigrationTargetMapping.ColumnMapping(
				new MigrationAssessment.ObjectId(null, schema, "column", source, table), target, "int", true, null);
	}
	@Test
	void existingTargetProvidersRemainCompatibleWithResolvedMappings() throws Exception {
		final var mapping = new ResolvedMigrationTargetMapping("fp", "target", "1", null);
		assertTrue(mapping.tables().isEmpty());
		assertTrue(TARGET.assessMapping(SOURCE.load(Path.of("input.fixture")), "1", mapping).findings().isEmpty());
		final var configured = new MigrationTargetMapping(MigrationTargetMapping.FORMAT, 1, "source", "target", "1", null);
		assertTrue(configured.tables().isEmpty());
	}
	@Test
	void integrityResultsDistinguishUnperformedChecksFromZeroViolations() {
		final var id = new MigrationAssessment.ObjectId(null, "s", "key", "pk", "t");
		assertTrue(new MigrationDataProfile(List.of()).integrityChecks().isEmpty());
		assertTrue(new com.sqlapp.util.JsonConverter().fromJsonString("{\"tables\":[]}", MigrationDataProfile.class).integrityChecks().isEmpty());
		assertThrows(IllegalArgumentException.class, () -> new MigrationDataProfile.IntegrityCheck(id,
				MigrationDataProfile.IntegrityKind.PRIMARY_KEY, MigrationDataProfile.IntegrityCoverage.UNSUPPORTED,
				List.of("id"), null, List.of(), 0L, 0L, 0L, "unsupported"));
		assertThrows(IllegalArgumentException.class, () -> new MigrationDataProfile.IntegrityCheck(id,
				MigrationDataProfile.IntegrityKind.PRIMARY_KEY, MigrationDataProfile.IntegrityCoverage.CHECKED,
				List.of("id"), null, List.of(), 1L, 0L, 2L, "invalid"));
	}
	@Test
	void existingProvidersRejectScansInsteadOfIgnoringTheOption() throws Exception {
		assertFalse(SOURCE.load(Path.of("input.fixture"), false).dataScanned());
		assertThrows(IllegalArgumentException.class, () -> SOURCE.load(Path.of("input.fixture"), true));
	}

	@Test
	void unsupportedColumnStatisticsCannotBeMisrepresentedAsZero() {
		final var id = new MigrationAssessment.ObjectId(null, "s", "column", "c", "t");
		assertThrows(IllegalArgumentException.class, () -> new MigrationDataProfile.ColumnProfile(id, "OLE",
				MigrationDataProfile.Coverage.UNSUPPORTED, 0L, null, null, null));
		assertThrows(IllegalArgumentException.class, () -> new MigrationDataProfile.ColumnProfile(id, "TEXT",
				MigrationDataProfile.Coverage.SCANNED, null, null, null, null));
		assertThrows(IllegalArgumentException.class, () -> new MigrationAssessmentSource(
				List.of(new Schema("s").setProductName("source")), EMPTY, false, false, new MigrationDataProfile(List.of())));
	}
	private static final MigrationAssessment EMPTY = new MigrationAssessment(List.of(), List.of());
	private static final DatabaseMigrationAssessmentProvider TARGET = new DatabaseMigrationAssessmentProvider() {
		public boolean supports(String source, String target, String version) {
			return "source".equals(source) && "target".equals(target) && "1".equals(version);
		}
		public String targetProduct() { return "target"; }
		public MigrationAssessment assess(MigrationAssessmentSource source, String version) { return EMPTY; }
	};
	private static final MigrationAssessmentSourceProvider SOURCE = new MigrationAssessmentSourceProvider() {
		public boolean supports(Path file) { return file.toString().endsWith(".fixture"); }
		public MigrationAssessmentSource load(Path file) {
			return new MigrationAssessmentSource(List.of(new Schema("s").setProductName("source")), EMPTY, false, false);
		}
	};

	@Test
	void resolvesOnlyAnExplicitUniqueSourceTargetVersionCombination() {
		assertSame(TARGET, DatabaseMigrationAssessmentProvider.resolve("source", "target", "1", List.of(TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("other", "target", "1", List.of(TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("source", "other", "1", List.of(TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("source", "target", "2", List.of(TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("source", "target", "1", List.of(TARGET, TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("source", " ", "1", List.of(TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("missing", "missing", "1"));
	}

	@Test
	void resolvesSourceWithoutFallbackOrAmbiguousSelection() {
		assertSame(SOURCE, MigrationAssessmentSourceProvider.resolve(Path.of("input.fixture"), List.of(SOURCE)));
		assertThrows(IllegalArgumentException.class, () -> MigrationAssessmentSourceProvider.resolve(Path.of("input.other"), List.of(SOURCE)));
		assertThrows(IllegalArgumentException.class, () -> MigrationAssessmentSourceProvider.resolve(Path.of("input.fixture"), List.of(SOURCE, SOURCE)));
		assertThrows(IllegalArgumentException.class, () -> MigrationAssessmentSourceProvider.resolve(Path.of("input.missing")));
	}

	@Test
	void snapshotsRequireAnUnambiguousProductAndPreserveSchemaSpecifics() {
		final var schema = new Schema("s").setProductName("source");
		schema.getSpecifics().put("source.nativeDetail", "value");
		final var schemas = new ArrayList<>(List.of(schema));
		final var snapshot = new MigrationAssessmentSource(schemas, EMPTY, false, false);
		schemas.clear();
		assertEquals("source", snapshot.sourceProduct());
		assertEquals("value", snapshot.schemas().getFirst().getSpecifics().get("source.nativeDetail", String.class));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.schemas().clear());
		assertThrows(IllegalArgumentException.class, () -> new MigrationAssessmentSource(List.of(), EMPTY, false, false));
		assertThrows(IllegalArgumentException.class, () -> new MigrationAssessmentSource(List.of(new Schema("s")), EMPTY, false, false));
		assertThrows(IllegalArgumentException.class, () -> new MigrationAssessmentSource(List.of(schema,
				new Schema("other").setProductName("other")), EMPTY, false, false));
	}
}
