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
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;

class DatabaseMigrationAssessmentProviderTest {
	@Test
	void targetDdlIncludesOnlySafeFullyMappedKeys() {
		final var schema = new Schema("source").setProductName("Microsoft Access");
		final var parent = new Table("Parent");
		final var parentId = new Column("ID").setIdentity(true);
		final var parentAlt = new Column("Alt");
		parent.getColumns().add(parentId);
		parent.getColumns().add(parentAlt);
		parent.setPrimaryKey("PK_PARENT", parentId);
		parent.getConstraints().addUniqueConstraint("UK_PARENT_ALT", parentAlt);
		final var child = new Table("Child");
		final var childId = new Column("ID");
		final var parentIdInChild = new Column("ParentID");
		final var parentAltInChild = new Column("ParentAlt");
		final var code = new Column("Code").setNotNull(true).setDefaultValue("7");
		code.getSpecifics().put("access.sourceType", "LONG");
		final var optionalCode = new Column("OptionalCode");
		child.getColumns().add(childId);
		child.getColumns().add(parentIdInChild);
		child.getColumns().add(parentAltInChild);
		child.getColumns().add(code);
		child.getColumns().add(optionalCode);
		child.setPrimaryKey("PK_CHILD", childId);
		child.getConstraints().addUniqueConstraint("UK_CHILD_CODE", code);
		child.getConstraints().addUniqueConstraint("UK_CHILD_OPTIONAL", optionalCode);
		final var searchIndex = new Index("X".repeat(129), parentIdInChild, code);
		searchIndex.getColumns().getLast().setOrder(Order.Desc);
		child.getIndexes().add(searchIndex);
		schema.getTables().add(parent);
		schema.getTables().add(child);
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT", parentIdInChild, parentId);
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_ALT", parentAltInChild, parentAlt);
		final var source = new MigrationAssessmentSource(List.of(schema), EMPTY, false, true);
		final var parentMapping = new ResolvedMigrationTargetMapping.TableMapping(
				new MigrationAssessment.ObjectId(null, "source", "table", "Parent"), "TARGET", "PARENT_T",
				List.of(column("source", "Parent", "ID", "PARENT_ID"),
						column("source", "Parent", "Alt", "ALT")));
		final var childMapping = new ResolvedMigrationTargetMapping.TableMapping(
				new MigrationAssessment.ObjectId(null, "source", "table", "Child"), "TARGET", "CHILD_T",
				List.of("[CODE] >= 0"),
				List.of(column("source", "Child", "ID", "CHILD_ID"),
						column("source", "Child", "ParentID", "PARENT_ID"),
						column("source", "Child", "ParentAlt", "PARENT_ALT"),
						new ResolvedMigrationTargetMapping.ColumnMapping(
								new MigrationAssessment.ObjectId(null, "source", "column", "Code", "Child"),
								"CODE", "int", false, null, "0", "normalize_code"),
						column("source", "Child", "OptionalCode", "OPTIONAL_CODE")));
		final var mapping = new ResolvedMigrationTargetMapping("fp", "target", "1", List.of(parentMapping, childMapping));
		final String ddl = MigrationTargetDdlGenerator.generate(source, mapping, value -> "[" + value + "]", "GO\n",
				column -> "", name -> name.length() <= 128);
		assertTrue(ddl.contains("-- Mapping summary:\n"
				+ "-- source tables: 2; mapped tables: 2; omitted tables: 0\n"
				+ "-- source columns: 7; mapped columns: 7; omitted columns in mapped tables: 0\n"
				+ "-- data profile scanned: false; relationships collected: true"));
		assertTrue(ddl.contains("-- Target DDL object summary:\n"
				+ "-- tables: 2; primary keys: 2; unique constraints: 1; checks: 1\n"
				+ "-- indexes: 1; foreign keys: 1; omitted keys/indexes: 2; omitted foreign keys: 1"));
		assertTrue(ddl.contains("CONSTRAINT [PK_PARENT] PRIMARY KEY ([PARENT_ID])"));
		assertTrue(ddl.contains("CONSTRAINT [PK_CHILD] PRIMARY KEY ([CHILD_ID])"));
		assertTrue(ddl.contains("CONSTRAINT [UK_CHILD_CODE] UNIQUE ([CODE])"));
		assertTrue(ddl.contains("[CODE] int DEFAULT 0 NOT NULL"));
		assertTrue(ddl.contains("CHECK ([CODE] >= 0)"));
		assertFalse(ddl.contains("UNIQUE ([OPTIONAL_CODE])"));
		assertTrue(ddl.matches("(?s).*CREATE INDEX \\[IX_[0-9a-f]{12}] ON \\[TARGET]\\.\\[CHILD_T] \\(\\[PARENT_ID], \\[CODE] DESC\\);.*"));
		assertTrue(ddl.contains("-- Source object names replaced for target compatibility:"));
		assertTrue(ddl.matches("(?s).*-- IX source name \"X{129}\" -> IX_[0-9a-f]{12}.*"));
		assertTrue(ddl.contains("-- Source object name mapping:\n"
				+ "-- PK \"PK_PARENT\" -> \"PK_PARENT\" (retained)\n"
				+ "-- PK \"PK_CHILD\" -> \"PK_CHILD\" (retained)\n"
				+ "-- UK \"UK_CHILD_CODE\" -> \"UK_CHILD_CODE\" (retained)"));
		assertTrue(ddl.matches("(?s).*-- IX \"X{129}\" -> \"IX_[0-9a-f]{12}\" \\(generated\\).*"));
		assertTrue(ddl.contains("-- FK \"FK_CHILD_PARENT\" -> \"FK_CHILD_PARENT\" (retained)"));
		assertTrue(ddl.contains("-- Source table and column name mapping:\n"
				+ "-- TABLE \"source.Parent\" -> \"TARGET.PARENT_T\"\n"
				+ "-- COLUMN \"source.Parent.ID\" -> \"TARGET.PARENT_T.PARENT_ID\"\n"
				+ "-- COLUMN \"source.Parent.Alt\" -> \"TARGET.PARENT_T.ALT\"\n"
				+ "-- TABLE \"source.Child\" -> \"TARGET.CHILD_T\""));
		assertTrue(ddl.contains("-- COLUMN \"source.Child.Code\" -> \"TARGET.CHILD_T.CODE\""));
		assertTrue(ddl.contains("-- Source-to-target column type mapping:"));
		assertTrue(ddl.contains("-- \"source.Child.Code\" (\"LONG\") -> \"TARGET.CHILD_T.CODE\" (\"int\"); conversion \"normalize_code\"; nullability required -> required; identity false -> unspecified; default \"7\" -> \"0\""));
		assertTrue(ddl.contains("-- \"source.Parent.ID\" (\"<unknown>\") -> \"TARGET.PARENT_T.PARENT_ID\" (\"int\"); nullability required -> required; identity true -> unspecified; default <none> -> <none>"));
		assertTrue(ddl.contains("-- Column semantic differences requiring review:"));
		assertTrue(ddl.contains("-- \"source.Parent.ID\": identity true -> unspecified"));
		assertTrue(ddl.contains("-- \"source.Parent.Alt\": nullability required -> nullable"));
		assertTrue(ddl.contains("-- \"source.Child.Code\": default \"7\" -> \"0\""));
		assertTrue(ddl.contains("[PARENT_ID] int NOT NULL"));
		assertTrue(ddl.contains("ALTER TABLE [TARGET].[CHILD_T] ADD CONSTRAINT [FK_CHILD_PARENT] FOREIGN KEY ([PARENT_ID]) REFERENCES [TARGET].[PARENT_T] ([PARENT_ID]);"));
		assertTrue(ddl.contains("-- Phase 1: Create target tables."));
		assertTrue(ddl.contains("-- Phase 2: Load data in the suggested order and run verification queries."));
		assertTrue(ddl.contains("-- Phase 3: After loading and verifying data, create secondary indexes."));
		assertTrue(ddl.contains("-- Phase 4: After loading and verifying data, apply foreign keys."));
		assertTrue(ddl.indexOf("CREATE TABLE") < ddl.indexOf("-- Suggested data load order"));
		assertTrue(ddl.indexOf("-- Post-load key integrity verification") < ddl.indexOf("CREATE INDEX"));
		assertTrue(ddl.indexOf("CREATE INDEX") < ddl.indexOf("ALTER TABLE"));
		assertFalse(ddl.contains("ADD CONSTRAINT [FK_CHILD_ALT]"));
		assertFalse(ddl.contains("c.[PARENT_ALT]"));
		assertTrue(ddl.contains("-- Source foreign keys omitted from the target DDL:\n"
				+ "-- \"Child.FK_CHILD_ALT\": referenced primary or unique key is not emitted"));
		assertTrue(ddl.contains("-- Verify target duplicates: SELECT [CHILD_ID], COUNT(*) FROM [TARGET].[CHILD_T] GROUP BY [CHILD_ID] HAVING COUNT(*) > 1;"));
		assertTrue(ddl.contains("-- Verify target duplicates: SELECT [CODE], COUNT(*) FROM [TARGET].[CHILD_T] GROUP BY [CODE] HAVING COUNT(*) > 1;"));
		assertTrue(ddl.contains("-- Verify target orphans: SELECT COUNT(*) FROM [TARGET].[CHILD_T] c LEFT JOIN [TARGET].[PARENT_T] p ON c.[PARENT_ID] = p.[PARENT_ID] WHERE c.[PARENT_ID] IS NOT NULL AND p.[PARENT_ID] IS NULL;"));
		assertTrue(ddl.contains("-- Suggested data load order from emitted foreign keys:\n"
				+ "-- 1. \"TARGET.PARENT_T\"\n-- 2. \"TARGET.CHILD_T\""));
		assertTrue(ddl.contains("-- Post-load row-count baseline from the Access source:\n"
				+ "-- \"TARGET.PARENT_T\": not scanned\n"
				+ "-- Verify target: SELECT COUNT(*) FROM [TARGET].[PARENT_T];\n"
				+ "-- \"TARGET.CHILD_T\": not scanned\n"
				+ "-- Verify target: SELECT COUNT(*) FROM [TARGET].[CHILD_T];"));
		assertTrue(ddl.contains("-- \"TARGET.CHILD_T\".\"CODE\": source NULLs not scanned\n"
				+ "-- Verify target: SELECT COUNT(*) FROM [TARGET].[CHILD_T] WHERE [CODE] IS NULL;"));

		final var partialChild = new ResolvedMigrationTargetMapping.TableMapping(childMapping.sourceTable(), "TARGET", "CHILD_T",
				List.of(column("source", "Child", "ID", "CHILD_ID")));
		final String partial = MigrationTargetDdlGenerator.generate(source,
				new ResolvedMigrationTargetMapping("fp", "target", "1", List.of(parentMapping, partialChild)),
				value -> "[" + value + "]", "GO\n", column -> "", name -> name.length() <= 128);
		assertFalse(partial.contains("ADD FOREIGN KEY"));
		assertFalse(partial.contains("CREATE INDEX"));
		assertFalse(partial.contains("Verify target orphans"));
		assertFalse(partial.contains("Source object names replaced"));
		assertTrue(partial.contains("-- Source object name mapping:\n"
				+ "-- PK \"PK_PARENT\" -> \"PK_PARENT\" (retained)\n"
				+ "-- PK \"PK_CHILD\" -> \"PK_CHILD\" (retained)"));
		assertTrue(partial.contains("-- COLUMN \"source.Child.ID\" -> \"TARGET.CHILD_T.CHILD_ID\""));
	}

	@Test
	void targetDdlExplainsOmittedPrimaryUniqueAndIndexObjects() {
		final var schema = new Schema("source").setProductName("Microsoft Access");
		final var table = new Table("Sample");
		final var id = new Column("ID");
		final var optional = new Column("OptionalCode");
		final var detail = new Column("Detail");
		final var ignored = new Table("Ignored");
		ignored.getColumns().add(new Column("Value"));
		table.getColumns().add(id);
		table.getColumns().add(optional);
		table.getColumns().add(detail);
		table.setPrimaryKey("PK_SAMPLE", id);
		table.getConstraints().addUniqueConstraint("UK_SAMPLE_OPTIONAL", optional);
		table.getIndexes().add(new Index("IX_SAMPLE_DETAIL", optional, detail));
		schema.getTables().add(table);
		schema.getTables().add(ignored);
		final var mapped = new ResolvedMigrationTargetMapping.TableMapping(
				new MigrationAssessment.ObjectId(null, "source", "table", "Sample"), null, "SAMPLE_T",
				List.of(column("source", "Sample", "OptionalCode", "OPTIONAL_CODE")));

		final String ddl = MigrationTargetDdlGenerator.generate(
				new MigrationAssessmentSource(List.of(schema), EMPTY, false, true),
				new ResolvedMigrationTargetMapping("fp", "target", "1", List.of(mapped)),
				value -> "[" + value + "]", "GO\n", value -> "", name -> true);

		assertTrue(ddl.contains("-- Source keys and indexes omitted from the target DDL:\n"
				+ "-- PRIMARY KEY \"Sample.PK_SAMPLE\": one or more participating columns are not mapped\n"
				+ "-- UNIQUE \"Sample.UK_SAMPLE_OPTIONAL\": source or target columns allow NULL\n"
				+ "-- INDEX \"Sample.IX_SAMPLE_DETAIL\": one or more participating columns are not mapped"));
		assertFalse(ddl.contains("PRIMARY KEY ("));
		assertFalse(ddl.contains(" UNIQUE ("));
		assertFalse(ddl.contains("CREATE INDEX"));
		assertTrue(ddl.contains("-- Source tables and columns omitted from the target DDL:\n"
				+ "-- COLUMN \"source.Sample.ID\": not mapped\n"
				+ "-- COLUMN \"source.Sample.Detail\": not mapped\n"
				+ "-- TABLE \"source.Ignored\": not mapped"));
		assertTrue(ddl.contains("-- source tables: 2; mapped tables: 1; omitted tables: 1\n"
				+ "-- source columns: 4; mapped columns: 1; omitted columns in mapped tables: 2"));
		assertTrue(ddl.contains("-- tables: 1; primary keys: 0; unique constraints: 0; checks: 0\n"
				+ "-- indexes: 0; foreign keys: 0; omitted keys/indexes: 3; omitted foreign keys: 0"));
	}

	@Test
	void targetDdlReportsCyclicLoadDependencies() {
		final var schema = new Schema("source").setProductName("Microsoft Access");
		final var first = new Table("First");
		final var firstId = new Column("ID");
		final var firstOther = new Column("OtherID");
		first.getColumns().add(firstId);
		first.getColumns().add(firstOther);
		final var second = new Table("Second");
		final var secondId = new Column("ID");
		final var secondOther = new Column("OtherID");
		second.getColumns().add(secondId);
		second.getColumns().add(secondOther);
		schema.getTables().add(first);
		schema.getTables().add(second);
		first.setPrimaryKey("PK_FIRST", firstId);
		second.setPrimaryKey("PK_SECOND", secondId);
		first.getConstraints().addForeignKeyConstraint("FK_FIRST_SECOND", firstOther, secondId);
		second.getConstraints().addForeignKeyConstraint("FK_SECOND_FIRST", secondOther, firstId);
		final var firstMapping = new ResolvedMigrationTargetMapping.TableMapping(
				new MigrationAssessment.ObjectId(null, "source", "table", "First"), null, "FIRST_T",
				List.of(column("source", "First", "ID", "ID"), column("source", "First", "OtherID", "OTHER_ID")));
		final var secondMapping = new ResolvedMigrationTargetMapping.TableMapping(
				new MigrationAssessment.ObjectId(null, "source", "table", "Second"), null, "SECOND_T",
				List.of(column("source", "Second", "ID", "ID"), column("source", "Second", "OtherID", "OTHER_ID")));
		final String ddl = MigrationTargetDdlGenerator.generate(
				new MigrationAssessmentSource(List.of(schema), EMPTY, false, true),
				new ResolvedMigrationTargetMapping("fp", "target", "1", List.of(firstMapping, secondMapping)),
				value -> "[" + value + "]", "GO\n", value -> "", name -> true);
		assertTrue(ddl.contains("-- Cyclic or cycle-dependent tables require staged loading or deferred constraints:\n"
				+ "-- - \"FIRST_T\"\n-- - \"SECOND_T\""));
		assertFalse(ddl.contains("-- 1."));
	}

	@Test
	void targetDdlRetainsScannedSourceRowCountsForReconciliation() {
		final var schema = new Schema("source").setProductName("Microsoft Access");
		final var table = new Table("Orders");
		table.getColumns().add(new Column("ID"));
		table.getColumns().add(new Column("CreatedAt"));
		schema.getTables().add(table);
		final var tableId = new MigrationAssessment.ObjectId(null, "source", "table", "Orders");
		final var columnId = new MigrationAssessment.ObjectId(null, "source", "column", "ID", "Orders");
		final var dateId = new MigrationAssessment.ObjectId(null, "source", "column", "CreatedAt", "Orders");
		final var profile = new MigrationDataProfile(List.of(
				new MigrationDataProfile.TableProfile(tableId, 42, List.of(
						new MigrationDataProfile.ColumnProfile(columnId, "LONG", MigrationDataProfile.Coverage.SCANNED,
								3L, null, new MigrationDataProfile.NumericStatistics(
										new java.math.BigDecimal("-12"), new java.math.BigDecimal("987"), 3, 0, 0), null),
						new MigrationDataProfile.ColumnProfile(dateId, "SHORT_DATE_TIME", MigrationDataProfile.Coverage.SCANNED,
								0L, null, null, new MigrationDataProfile.DateTimeStatistics(
										"2020-01-02T03:04:05", "2025-06-07T08:09:10", 0))))), List.of());
		final var source = new MigrationAssessmentSource(List.of(schema), EMPTY, true, true, profile);
		final var mapped = new ResolvedMigrationTargetMapping.TableMapping(tableId, "APP", "ORDERS",
				List.of(column("source", "Orders", "ID", "ORDER_ID"),
						column("source", "Orders", "CreatedAt", "CREATED_AT")));
		final String ddl = MigrationTargetDdlGenerator.generate(source,
				new ResolvedMigrationTargetMapping("fp", "target", "1", List.of(mapped)),
				value -> "\"" + value + "\"", "\n", value -> "", name -> true);
		assertTrue(ddl.contains("-- \"APP.ORDERS\": 42 rows"));
		assertTrue(ddl.contains("-- Verify target: SELECT COUNT(*) FROM \"APP\".\"ORDERS\";"));
		assertTrue(ddl.contains("-- \"APP.ORDERS\".\"ORDER_ID\": source NULLs 3\n"
				+ "-- Verify target: SELECT COUNT(*) FROM \"APP\".\"ORDERS\" WHERE \"ORDER_ID\" IS NULL;"));
		assertTrue(ddl.contains("-- \"APP.ORDERS\".\"ORDER_ID\": source min=-12, max=987\n"
				+ "-- Verify target: SELECT MIN(\"ORDER_ID\"), MAX(\"ORDER_ID\") FROM \"APP\".\"ORDERS\";"));
		assertTrue(ddl.contains("-- \"APP.ORDERS\".\"CREATED_AT\": source min=2020-01-02T03:04:05, max=2025-06-07T08:09:10\n"
				+ "-- Verify target: SELECT MIN(\"CREATED_AT\"), MAX(\"CREATED_AT\") FROM \"APP\".\"ORDERS\";"));
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
