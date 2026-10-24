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
