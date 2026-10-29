/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.util.MessageDigests;

class BulkMigrationJobDraftBuilderTest {
	@TempDir
	Path directory;
	private final BulkMigrationJobDraftBuilder builder = new BulkMigrationJobDraftBuilder();

	@Test
	void includesTransitiveParentsWithoutUnrelatedTablesOrChangingSchema() throws Exception {
		var schema = new Schema("PUBLIC");
		var grandparent = table("ROOTS");
		var parent = table("PARENTS");
		var child = table("CHILDREN");
		parent.getConstraints().addForeignKeyConstraint("FK_ROOT", parent.getColumns().get("ID"),
				grandparent.getColumns().get("ID"));
		child.getConstraints().addForeignKeyConstraint("FK_PARENT", child.getColumns().get("ID"),
				parent.getColumns().get("ID"));
		schema.getTables().add(child);
		schema.getTables().add(parent);
		schema.getTables().add(grandparent);
		schema.getTables().add(table("OTHER"));
		var file = directory.resolve("references.xml");
		schema.writeXml(file.toFile());
		var original = Files.readAllBytes(file);
		var snapshot = builder.inspect(file);
		assertEquals(List.of("PUBLIC.CHILDREN", "PUBLIC.PARENTS", "PUBLIC.ROOTS"),
				builder.includeReferencedTables(snapshot, List.of("PUBLIC.CHILDREN")));
		assertArrayEquals(original, Files.readAllBytes(file));
		var draft = new BulkMigrationJobConfigurationEditorIO().validate(builder.draft(snapshot,
				builder.includeReferencedTables(snapshot, List.of("PUBLIC.CHILDREN")), null, 100, true));
		assertEquals(List.of("PUBLIC.ROOTS", "PUBLIC.PARENTS", "PUBLIC.CHILDREN"),
				draft.getTasks().stream().map(task -> task.getTable()).toList());
		Files.writeString(file, Files.readString(file) + "\n<!-- changed -->");
		assertThrows(IllegalArgumentException.class,
				() -> builder.includeReferencedTables(snapshot, List.of("PUBLIC.CHILDREN")));
	}

	@Test
	void unresolvedParentsAreRejectedWithoutGuessingAnotherSchema() throws Exception {
		var schema = new Schema("PUBLIC");
		var child = table("CHILDREN");
		var external = new Schema("EXTERNAL");
		var externalParent = table("PARENTS");
		external.getTables().add(externalParent);
		child.getConstraints().addForeignKeyConstraint("FK_EXTERNAL", child.getColumns().get("ID"),
				externalParent.getColumns().get("ID"));
		schema.getTables().add(child);
		schema.getTables().add(table("PARENTS"));
		var file = directory.resolve("external.xml");
		schema.writeXml(file.toFile());
		assertTrue(Files.readString(file).contains("EXTERNAL"));
		var failure = assertThrows(IllegalArgumentException.class,
				() -> builder.includeReferencedTables(builder.inspect(file), List.of("PUBLIC.CHILDREN")));
		assertTrue(failure.getMessage().contains("EXTERNAL.PARENTS"));
		assertTrue(failure.getMessage().contains("missing or ambiguous"));
	}

	@Test
	void cyclesTerminateInSelectionButOrdinaryPlannerStillRejectsExecutionCycle() throws Exception {
		var schema = new Schema("PUBLIC");
		var first = table("FIRST");
		var second = table("SECOND");
		first.getConstraints().addForeignKeyConstraint("FK_SECOND", first.getColumns().get("ID"),
				second.getColumns().get("ID"));
		second.getConstraints().addForeignKeyConstraint("FK_FIRST", second.getColumns().get("ID"),
				first.getColumns().get("ID"));
		schema.getTables().add(first);
		schema.getTables().add(second);
		var file = directory.resolve("cycle.xml");
		schema.writeXml(file.toFile());
		var snapshot = builder.inspect(file);
		var selected = builder.includeReferencedTables(snapshot, List.of("PUBLIC.FIRST"));
		assertEquals(List.of("PUBLIC.FIRST", "PUBLIC.SECOND"), selected);
		assertTrue(
				assertThrows(IllegalArgumentException.class, () -> builder.draft(snapshot, selected, null, 100, false))
						.getMessage().contains("cyclic"));
		assertThrows(IllegalArgumentException.class,
				() -> builder.includeReferencedTables(snapshot, List.of("PUBLIC.FIRST", "FIRST")));
		assertThrows(IllegalArgumentException.class, () -> builder.includeReferencedTables(snapshot, List.of()));
		assertThrows(IllegalArgumentException.class, () -> builder.includeReferencedTables(snapshot, List.of(" ")));
		assertThrows(IllegalArgumentException.class,
				() -> builder.includeReferencedTables(snapshot, java.util.Collections.singletonList(null)));
	}

	@Test
	void resolvesCatalogAndSchemaQualifiedParentAndIgnoresSelfReference() throws Exception {
		var catalog = new com.sqlapp.data.schemas.Catalog("CAT");
		var childSchema = new Schema("APP");
		var parentSchema = new Schema("MASTER");
		var otherSchema = new Schema("OTHER");
		catalog.getSchemas().add(childSchema);
		catalog.getSchemas().add(parentSchema);
		catalog.getSchemas().add(otherSchema);
		var child = table("CHILDREN");
		var parent = table("PARENTS");
		childSchema.getTables().add(child);
		parentSchema.getTables().add(parent);
		otherSchema.getTables().add(table("PARENTS"));
		child.getConstraints().addForeignKeyConstraint("FK_PARENT", child.getColumns().get("ID"),
				parent.getColumns().get("ID"));
		parent.getConstraints().addForeignKeyConstraint("FK_SELF", parent.getColumns().get("ID"),
				parent.getColumns().get("ID"));
		var file = directory.resolve("catalog.xml");
		catalog.writeXml(file.toFile());
		var snapshot = builder.inspect(file);
		assertEquals(List.of("CAT.APP.CHILDREN", "CAT.MASTER.PARENTS"),
				builder.includeReferencedTables(snapshot, List.of("CHILDREN")));
		assertTrue(assertThrows(RuntimeException.class,
				() -> builder.includeReferencedTables(snapshot, List.of("PARENTS"))).getMessage()
				.toLowerCase(java.util.Locale.ROOT).contains("ambiguous"));
	}

	@Test
	void generatesPinnedConciseYamlAndDelegatesForeignKeyOrderingToSharedPlanner() throws Exception {
		final var schema = new Schema("PUBLIC");
		final var parent = table("PARENTS");
		final var child = table("CHILDREN");
		child.getColumns().add(new Column("PARENT_ID").setDataType(DataType.INT));
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT", child.getColumns().get("PARENT_ID"),
				parent.getColumns().get("ID"));
		schema.getTables().add(child);
		schema.getTables().add(parent);
		final Path file = directory.resolve("schema.xml");
		schema.writeXml(file.toFile());
		final var snapshot = builder.inspect(file);
		final String yaml = builder.draft(snapshot, List.of("PUBLIC.CHILDREN", "PUBLIC.PARENTS"), "copy", 200, true);
		final var config = new BulkMigrationJobConfigurationEditorIO().validate(yaml);
		assertEquals(List.of("PUBLIC.PARENTS", "PUBLIC.CHILDREN"),
				config.getTasks().stream().map(BulkMigrationJobConfiguration.Task::getTable).toList());
		assertEquals(file.toAbsolutePath().toString(), config.getSchemaFile());
		assertEquals("sha256:" + MessageDigests.SHA256.checksumAsString(file.toFile()), config.getSchemaFingerprint());
		assertTrue(config.getTasks().stream().allMatch(
				task -> task.getMode() == BulkMigrationMode.INSERT && !task.isResume() && task.isRequireEmptyTarget()));
		assertEquals(200, config.getTasks().getFirst().getChunkSize());
		assertNotNull(config.getVerification());
		assertFalse(yaml.contains("sourceFingerprint"));
		assertFalse(yaml.contains("keysetColumns"));
		// Only this isolated in-memory HSQL connection is used to resolve the generated
		// config.
		final Path job = directory.resolve("job.yaml");
		Files.writeString(job, yaml);
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:draft_" + UUID.randomUUID(), "SA", "")) {
			final var resolved = new BulkMigrationJobConfigurationResolver().resolveJob(job.toFile(), connection);
			assertEquals(2, resolved.plan().getTasks().size());
			assertEquals("PARENTS", resolved.plan().getTasks().getFirst().getEffectiveSourceTable().getName());
		}
	}

	@Test
	void stableTaskIdsDoNotDependOnSelectionOrderAndDefaultsRemainConcise() throws Exception {
		final var schema = new Schema("PUBLIC");
		schema.getTables().add(table("A"));
		schema.getTables().add(table("B"));
		final Path file = directory.resolve("schema.xml");
		schema.writeXml(file.toFile());
		final var snapshot = builder.inspect(file);
		final var io = new BulkMigrationJobConfigurationEditorIO();
		final var onlyA = io.validate(builder.draft(snapshot, List.of("PUBLIC.A"), null, 10_000, false));
		final String bothYaml = builder.draft(snapshot, List.of("PUBLIC.B", "PUBLIC.A"), null, 10_000, false);
		final var both = io.validate(bothYaml);
		assertEquals(onlyA.getTasks().getFirst().getId(), both.getTasks().stream()
				.filter(task -> task.getTable().equals("PUBLIC.A")).findFirst().orElseThrow().getId());
		assertNull(onlyA.getVerification());
		assertNull(onlyA.getJobId());
		assertFalse(bothYaml.contains("chunkSize"));
	}

	@Test
	void rejectsChangedSchemaUnknownDuplicatesAndEmptySelections() throws Exception {
		final var schema = new Schema("PUBLIC");
		schema.getTables().add(table("A"));
		final Path file = directory.resolve("schema.xml");
		schema.writeXml(file.toFile());
		final var snapshot = builder.inspect(file);
		assertThrows(IllegalArgumentException.class, () -> builder.draft(snapshot, List.of(), null, 10, false));
		assertThrows(IllegalArgumentException.class,
				() -> builder.draft(snapshot, List.of("PUBLIC.A", "PUBLIC.A"), null, 10, false));
		assertThrows(IllegalArgumentException.class,
				() -> builder.draft(snapshot, List.of("MISSING"), null, 10, false));
		assertThrows(IllegalArgumentException.class,
				() -> builder.draft(snapshot, List.of("PUBLIC.A"), null, 0, false));
		Files.writeString(file, Files.readString(file) + "\n");
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> builder.draft(snapshot, List.of("PUBLIC.A"), null, 10, false)).getMessage().contains("changed"));
	}

	@Test
	void reportsUnsupportedKeysAndIdentifiersWithoutGuessing() throws Exception {
		final var schema = new Schema("PUBLIC");
		schema.getTables().add(new Table("NO_KEY"));
		schema.getTables().add(table("NAME.WITH.DOTS"));
		final Path file = directory.resolve("schema.xml");
		schema.writeXml(file.toFile());
		final var snapshot = builder.inspect(file);
		assertTrue(snapshot.tables().stream().noneMatch(BulkMigrationJobDraftBuilder.TableChoice::selectable));
		assertTrue(snapshot.tables().getFirst().restriction().contains("primary key"));
		assertThrows(IllegalArgumentException.class,
				() -> builder.draft(snapshot, List.of("PUBLIC.NO_KEY"), null, 10, false));
	}

	@Test
	void acceptsSingleTableRootAndRejectsDtdMalformedXmlAndRowData() throws Exception {
		final Path file = directory.resolve("schema.xml");
		table("ITEMS").writeXml(file.toFile());
		assertEquals("ITEMS", builder.inspect(file).tables().getFirst().table());
		Files.writeString(file, "<!DOCTYPE schema [<!ENTITY x SYSTEM 'file:///missing'>]><schema name='PUBLIC'/>");
		assertThrows(IllegalArgumentException.class, () -> builder.inspect(file));
		Files.writeString(file, "<schema>");
		assertThrows(java.io.IOException.class, () -> builder.inspect(file));
		final var schema = new Schema("PUBLIC");
		final var items = table("ITEMS");
		items.getRows().add(row -> row.put("ID", 1));
		schema.getTables().add(items);
		schema.writeXml(file.toFile());
		assertThrows(IllegalArgumentException.class, () -> builder.inspect(file));
	}

	private static Table table(String name) {
		final var table = new Table(name);
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_" + name, table.getColumns().get("ID"));
		return table;
	}
}
