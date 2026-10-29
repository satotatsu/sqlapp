/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.datatype.DataType;
import io.github.spannm.jackcess.*;

class MdbSchemaMigrationTest {
	@Test
	void planningReportsTypeAndQueryFailuresTogetherWithoutCreatingTarget() throws Exception {
		final Path source = create();
		final Path target = directory.resolve("invalid.accdb");
		final byte[] before = Files.readAllBytes(source);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> MdbSchemaMigration.plan(source, target, schema -> {
					schema.getTables().get("ITEMS").getColumns().get("ID").setDataType(DataType.BIGINT);
					final var query = new com.sqlapp.data.schemas.View("BROKEN");
					query.setDefinition("SELECT missing FROM ITEMS");
					schema.getViews().add(query);
				}));
		assertTrue(error.getMessage().contains("ITEMS.ID"));
		assertTrue(error.getMessage().contains("BROKEN"));
		assertFalse(Files.exists(target));
		assertArrayEquals(before, Files.readAllBytes(source));
	}

	@Test
	void columnRenameRequiresUpdatingSavedQueryDefinitionDuringPlanning() throws Exception {
		final Path source = create();
		try (var writer = MdbJackcessSupport.open(source)) {
			writer.createSavedQuery("Q", "SELECT TXT FROM ITEMS");
		}
		final Path target = directory.resolve("renamed-query.accdb");
		assertThrows(IllegalArgumentException.class, () -> MdbSchemaMigration.plan(source, target,
				schema -> schema.getTables().get("ITEMS").getColumns().get("TXT").setName("RENAMED")));
		final var result = MdbSchemaMigration.rebuild(source, target, schema -> {
			schema.getTables().get("ITEMS").getColumns().get("TXT").setName("RENAMED");
			schema.getViews().get("Q").setDefinition("SELECT RENAMED FROM ITEMS");
		});
		assertNotNull(result.schema().getViews().get("Q"));
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void batchedCopyPreservesNullsAndAppliesDefaultsOnlyToNewColumns(final Database.FileFormat format)
			throws Exception {
		final Path source = directory.resolve("batch" + format.getFileExtension());
		try (var file = DatabaseBuilder.create(format, source.toFile())) {
			final var table = new TableBuilder("ROWS")
					.addColumn(new ColumnBuilder("ID", io.github.spannm.jackcess.DataType.LONG).withAutoNumber(true))
					.addColumn(new ColumnBuilder("TXT", io.github.spannm.jackcess.DataType.TEXT))
					.addIndex(new IndexBuilder("PK_ROWS").withPrimaryKey().withColumns("ID")).toTable(file);
			for (int i = 0; i < 2051; i++)
				table.addRow(null, i % 2 == 0 ? null : "row-" + i);
			table.getColumn("TXT").getProperties().put(PropertyMap.DEFAULT_VALUE_PROP, "\"existing-default\"");
			table.getColumn("TXT").getProperties().save();
		}
		final byte[] before = Files.readAllBytes(source);
		final var result = MdbSchemaMigration.rebuild(source, directory.resolve("rebuilt" + format.getFileExtension()),
				schema -> {
					final var table = schema.getTables().get("ROWS");
					table.getColumns().get("TXT").setName("RENAMED");
					table.getColumns().add(new com.sqlapp.data.schemas.Column("NEW_VALUE").setDataType(DataType.INT)
							.setNotNull(true).setDefaultValue("7"));
				});
		assertEquals(2051L, result.rowCounts().get("ROWS"));
		try (var file = new DatabaseBuilder().withPath(result.plan().target()).open()) {
			final var table = file.getTable("ROWS");
			int i = 0;
			for (final var row : table) {
				assertEquals(i + 1, row.get("ID"));
				assertEquals(i % 2 == 0 ? null : "row-" + i, row.get("RENAMED"));
				assertEquals(7, row.get("NEW_VALUE"));
				i++;
			}
			final var inserted = table.addRow(null, null, null);
			assertEquals(2052, inserted[0]);
			assertEquals("existing-default", inserted[1]);
			assertEquals(7, inserted[2]);
		}
		assertArrayEquals(before, Files.readAllBytes(source));
	}

	@Test
	void manuallySuppliedPlanMappingsUseAccessIdentifierCaseRules() throws Exception {
		final var plan = MdbSchemaMigration.plan(create(), directory.resolve("mapped.accdb"),
				schema -> schema.getTables().get("ITEMS").setName("RENAMED"));
		final var supplied = new MdbSchemaMigration.Plan(plan.source(), plan.target(), plan.sourceFingerprint(),
				plan.schemaXml(), plan.schemaFingerprint(), Map.of("renamed", "items"), Map.of());
		assertEquals(1L, MdbSchemaMigration.execute(supplied).rowCounts().get("RENAMED"));
	}

	@Test
	void uniqueConstraintsBecomeEnforcedAccessIndexes() throws Exception {
		final var result = MdbSchemaMigration.rebuild(create(), directory.resolve("unique.accdb"), schema -> {
			final var table = schema.getTables().get("ITEMS");
			final var key = new com.sqlapp.data.schemas.UniqueConstraint("UQ_TXT_OLD", false);
			key.getColumns().add("TXT");
			key.getColumns().add("OLD");
			table.getConstraints().add(key);
		});
		assertTrue(result.schema().getTables().get("ITEMS").getIndexes().get("UQ_TXT_OLD").isUnique());
		try (var file = new DatabaseBuilder().withPath(result.plan().target()).open()) {
			assertThrows(java.io.IOException.class, () -> file.getTable("ITEMS").addRow(2, "private-row-value", 7));
			file.getTable("ITEMS").addRow(2, "private-row-value", 8);
		}
	}

	@TempDir
	Path directory;

	@Test
	void explicitTypeChangesOverrideRetainedNativeHints() throws Exception {
		final var result = MdbSchemaMigration.rebuild(create(), directory.resolve("memo.accdb"),
				schema -> schema.getTables().get("ITEMS").getColumns().get("TXT").setDataType(DataType.LONGNVARCHAR));
		try (var file = new DatabaseBuilder().withPath(result.plan().target()).withReadOnly(true).open()) {
			assertEquals(io.github.spannm.jackcess.DataType.MEMO, file.getTable("ITEMS").getColumn("TXT").getType());
		}
	}

	@Test
	void addsEmptyTablesWithNativeGuidAndMoney() throws Exception {
		final var result = MdbSchemaMigration.rebuild(create(), directory.resolve("added.accdb"), schema -> {
			final var table = new com.sqlapp.data.schemas.Table("NEW");
			table.getColumns().add(new com.sqlapp.data.schemas.Column("TOKEN").setDataType(DataType.UUID));
			table.getColumns().add(new com.sqlapp.data.schemas.Column("CASH").setDataType(DataType.MONEY));
			schema.getTables().add(table);
		});
		assertEquals(0L, result.rowCounts().get("NEW"));
		try (var file = new DatabaseBuilder().withPath(result.plan().target()).withReadOnly(true).open()) {
			assertEquals(io.github.spannm.jackcess.DataType.GUID, file.getTable("NEW").getColumn("TOKEN").getType());
			assertEquals(io.github.spannm.jackcess.DataType.MONEY, file.getTable("NEW").getColumn("CASH").getType());
		}
	}

	@Test
	void renamesRelatedTablesAndColumnsWithoutBreakingForeignKeys() throws Exception {
		final Path source = create();
		try (var file = new DatabaseBuilder().withPath(source).open()) {
			final var child = new TableBuilder("CHILD")
					.addColumn(new ColumnBuilder("PID", io.github.spannm.jackcess.DataType.LONG)).toTable(file);
			new RelationshipBuilder(file.getTable("ITEMS"), child).addColumns("ID", "PID").withReferentialIntegrity()
					.withName("FK_CHILD").toRelationship(file);
			child.addRow(1);
		}
		final var result = MdbSchemaMigration.rebuild(source, directory.resolve("relations.accdb"), schema -> {
			final var parent = schema.getTables().get("ITEMS");
			parent.setName("PARENT");
			parent.getColumns().get("ID").setName("KEY");
			schema.getTables().get("CHILD").getColumns().get("PID").setName("PKEY");
		});
		final var key = result.schema().getTables().get("CHILD").getConstraints().getForeignKeyConstraints().getFirst();
		assertEquals("PARENT", key.getRelatedTableName());
		assertEquals("KEY", key.getRelatedColumns().getFirst().getName());
		assertEquals("PKEY", key.getColumns().getFirst().getName());
	}

	@Test
	void editsSchemaAndInfersRenamesWithoutReplacingSource() throws Exception {
		final Path source = create();
		final Path target = directory.resolve("result.accdb");
		final byte[] before = Files.readAllBytes(source);
		final var plan = MdbSchemaMigration.plan(source, target, schema -> {
			final var table = schema.getTables().get("ITEMS");
			table.setName("商品");
			table.getColumns().get("TXT").setName("表示名");
			table.getColumns().get("表示名").setLength(80L);
			table.getColumns().remove("OLD");
			table.getColumns().add(new com.sqlapp.data.schemas.Column("NEW").setDataType(DataType.INT));
		});
		assertFalse(Files.exists(target));
		assertFalse(plan.schemaXml().contains("private-row-value"));
		assertEquals(Map.of("商品", "ITEMS"), plan.tableMappings());
		assertEquals(Map.of("表示名", "TXT"), plan.columnMappings().get("商品"));
		final var result = MdbSchemaMigration.execute(plan);
		assertEquals(1L, result.rowCounts().get("商品"));
		assertNull(result.schema().getTables().get("商品").getColumns().get("OLD"));
		assertEquals(80L, result.schema().getTables().get("商品").getColumns().get("表示名").getLength());
		try (var file = new DatabaseBuilder().withPath(target).withReadOnly(true).open()) {
			assertEquals("private-row-value", file.getTable("商品").iterator().next().get("表示名"));
		}
		assertArrayEquals(before, Files.readAllBytes(source));
		assertThrows(IllegalArgumentException.class, () -> MdbSchemaMigration.execute(plan));
	}

	@Test
	void rejectsChangedSourceAndInvalidTargets() throws Exception {
		final Path source = create();
		final Path target = directory.resolve("result.accdb");
		final var plan = MdbSchemaMigration.plan(source, target, schema -> {
		});
		try (var file = new DatabaseBuilder().withPath(source).open()) {
			file.getTable("ITEMS").addRow(2, "changed", 0);
		}
		assertThrows(java.io.IOException.class, () -> MdbSchemaMigration.execute(plan));
		assertFalse(Files.exists(target));
		assertThrows(IllegalArgumentException.class, () -> MdbSchemaMigration.rebuild(source, source, schema -> {
		}));
	}

	@Test
	void commonEntryPointReturnsVerification() throws Exception {
		final var result = MdbSchemaMigration.rebuild(create(), directory.resolve("new.accdb"),
				schema -> schema.getTables().get("ITEMS").getColumns().get("TXT").setLength(100L));
		assertEquals(1L, result.rowCounts().get("ITEMS"));
		assertTrue(result.plan().sourceFingerprint().startsWith("sha256:"));
	}

	@Test
	void schemaEditorCannotSilentlyInjectRowData() throws Exception {
		final Path source = create();
		final Path target = directory.resolve("rejected.accdb");
		assertThrows(IllegalArgumentException.class, () -> MdbSchemaMigration.rebuild(source, target, schema -> {
			final var table = schema.getTables().get("ITEMS");
			table.getRows().add(table.newRow());
		}));
		assertFalse(Files.exists(target));
	}

	@Test
	void verificationFailureRetainsTargetAndDiagnostics() throws Exception {
		final Path source = create();
		final Path target = directory.resolve("unverified.accdb");
		final byte[] original = Files.readAllBytes(source);
		final var failure = assertThrows(MdbSchemaMigration.VerificationException.class,
				() -> MdbSchemaMigration.rebuild(source, target, schema -> {
					final var table = schema.getTables().get("ITEMS");
					final var index = new com.sqlapp.data.schemas.Index("IX_TXT");
					index.getColumns().add("TXT");
					index.setEnable(false);
					table.getIndexes().add(index);
				}));
		assertEquals(target, failure.plan().target());
		assertNotNull(failure.schema().getTables().get("ITEMS"));
		assertEquals(1L, failure.rowCounts().get("ITEMS"));
		assertTrue(failure.issues().stream().anyMatch(issue -> issue.contains("IX_TXT") && issue.contains("enabled")));
		assertTrue(Files.exists(target));
		assertArrayEquals(original, Files.readAllBytes(source));
	}

	@Test
	void verificationDetectsLostKeysIndexesAndColumnProperties() throws Exception {
		final Path source = create();
		try (var file = new DatabaseBuilder().withPath(source).open()) {
			new IndexBuilder("IX_TXT").withColumns("TXT").addToTable(file.getTable("ITEMS"));
			final var child = new TableBuilder("CHILD")
					.addColumn(new ColumnBuilder("PID", io.github.spannm.jackcess.DataType.LONG)).toTable(file);
			new RelationshipBuilder(file.getTable("ITEMS"), child).addColumns("ID", "PID").withReferentialIntegrity()
					.withName("FK_CHILD").toRelationship(file);
		}
		final var expected = MdbFileLoader.load(source);
		final var observed = MdbFileLoader.load(source);
		assertTrue(MdbSchemaVerification.verify(expected, observed).isEmpty());
		final var table = observed.getTables().get("ITEMS");
		table.getConstraints().remove("PK_ITEMS");
		table.getIndexes().remove("IX_TXT");
		table.getColumns().get("TXT").setLength(20L).setDefaultValue("'changed'");
		observed.getTables().get("CHILD").getConstraints().remove("FK_CHILD");
		final var issues = MdbSchemaVerification.verify(expected, observed);
		assertTrue(issues.stream().anyMatch(issue -> issue.contains("primary key presence")));
		assertTrue(issues.stream().anyMatch(issue -> issue.contains("Missing index")));
		assertTrue(issues.stream().anyMatch(issue -> issue.contains("Missing foreign key")));
		assertTrue(issues.stream().anyMatch(issue -> issue.contains("length/precision")));
		assertTrue(issues.stream().anyMatch(issue -> issue.contains("default")));
	}

	private Path create() throws Exception {
		final Path path = directory.resolve("source.accdb");
		try (var file = DatabaseBuilder.create(Database.FileFormat.V2010, path.toFile())) {
			new TableBuilder("ITEMS").addColumn(new ColumnBuilder("ID", io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("TXT", io.github.spannm.jackcess.DataType.TEXT).withLengthInUnits(40))
					.addColumn(new ColumnBuilder("OLD", io.github.spannm.jackcess.DataType.LONG))
					.addIndex(new IndexBuilder("PK_ITEMS").withColumns("ID").withPrimaryKey()).toTable(file)
					.addRow(1, "private-row-value", 7);
		}
		return path;
	}
}
