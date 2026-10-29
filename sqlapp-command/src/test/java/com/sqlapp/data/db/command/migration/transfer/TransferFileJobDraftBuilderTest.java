/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;

class TransferFileJobDraftBuilderTest {
	@TempDir
	Path directory;

	@Test
	void savesSharedJobWithoutSchemaPathMechanicsAndRejectsOverwrite() throws Exception {
		final var snapshot = snapshot();
		final var builder = new TransferFileJobDraftBuilder();
		final var saved = builder.save(snapshot, List.of("PUBLIC.ITEMS"), 200, true, "__NULL__",
				directory.resolve("transfer.yaml"));
		assertEquals(saved, new TransferJobConfigurationIO().read(saved.file()));
		assertEquals("PUBLIC", saved.configuration().sourceSchema());
		assertNull(saved.configuration().targetSchema());
		assertEquals(List.of("PUBLIC.ITEMS"), saved.configuration().tables());
		assertEquals(TransferJobConfiguration.DataMode.FILE, saved.configuration().dataMode());
		assertEquals(TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING, saved.configuration().schemaMode());
		assertEquals("__NULL__", saved.configuration().files().nullValue());
		assertEquals("packages", saved.configuration().files().directory());
		assertEquals(200, saved.configuration().chunkSize());
		assertTrue(saved.configuration().verify());
		assertFalse(saved.text().contains("schemaFile"));
		assertFalse(saved.text().contains("jdbc:"));
		assertThrows(Exception.class,
				() -> builder.save(snapshot, List.of("PUBLIC.ITEMS"), 200, false, "__NULL__", saved.file()));
		assertEquals(saved, new TransferJobConfigurationIO().read(saved.file()));
	}

	@Test
	void explicitTargetSchemaPreservesSourceSelectionAndPoliciesAndRejectsBlankNames() throws Exception {
		final var reviewed = snapshot();
		final var builder = new TransferFileJobDraftBuilder();
		final var saved = builder.save(reviewed, List.of("PUBLIC.ITEMS"), 50, false, "__NULL__",
				TransferJobConfiguration.SchemaMode.CREATE_MISSING, TransferJobConfiguration.TemporalPolicy.WARN,
				TransferJobConfiguration.CompatibilityPolicy.ERROR, "Archive Data", directory.resolve("target.yaml"));
		final var config = new TransferJobConfigurationIO().read(saved.file()).configuration();
		assertEquals("Archive Data", config.targetSchema());
		assertEquals("PUBLIC", config.sourceSchema());
		assertEquals(List.of("PUBLIC.ITEMS"), config.tables());
		assertEquals(TransferJobConfiguration.CompatibilityPolicy.ERROR, config.compatibilityPolicy());
		assertEquals(TransferJobConfiguration.TemporalPolicy.WARN, config.files().unsupportedTemporalPolicy());
		assertFalse(config.verify());
		assertThrows(IllegalArgumentException.class,
				() -> builder.save(reviewed, List.of("PUBLIC.ITEMS"), 50, true, "__NULL__",
						TransferJobConfiguration.SchemaMode.CREATE_MISSING,
						TransferJobConfiguration.TemporalPolicy.WARN, TransferJobConfiguration.CompatibilityPolicy.WARN,
						" ", directory.resolve("blank.yaml")));
		assertFalse(Files.exists(directory.resolve("blank.yaml")));
	}

	@Test
	void tablePickerEligibilityUsesTheSameNumericKeyAndScalarTypeRulesAsImport() throws Exception {
		final var snapshot = snapshot();
		final var builder = new TransferFileJobDraftBuilder();
		final var inspected = builder.inspect(snapshot);
		assertTrue(inspected.tables().stream().filter(table -> table.table().endsWith("ITEMS")).findFirst()
				.orElseThrow().selectable());
		assertTrue(inspected.tables().stream().filter(table -> table.table().endsWith("DATES"))
				.allMatch(table -> table.selectable()));
		assertFalse(inspected.tables().stream().filter(table -> table.table().endsWith("TEXT_KEYS")).findFirst()
				.orElseThrow().selectable());
		assertThrows(Exception.class,
				() -> builder.save(snapshot, List.of("PUBLIC.DATES"), 100, true, "__NULL__",
						TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING,
						TransferJobConfiguration.TemporalPolicy.ERROR, directory.resolve("invalid.yaml")));
		assertFalse(Files.exists(directory.resolve("invalid.yaml")));
	}

	@Test
	void changedSchemaAndInvalidNullTokenCannotCreateAJob() throws Exception {
		final var snapshot = snapshot();
		final var builder = new TransferFileJobDraftBuilder();
		assertThrows(Exception.class, () -> builder.save(snapshot, List.of("PUBLIC.ITEMS"), 100, true, "",
				directory.resolve("invalid.yaml")));
		assertFalse(Files.exists(directory.resolve("invalid.yaml")));
		Files.writeString(snapshot.file(), Files.readString(snapshot.file()) + "\n<!-- edited -->");
		assertThrows(Exception.class, () -> builder.inspect(snapshot));
		assertThrows(Exception.class, () -> builder.save(snapshot, List.of("PUBLIC.ITEMS"), 100, true, "__NULL__",
				directory.resolve("invalid.yaml")));
		assertFalse(Files.exists(directory.resolve("invalid.yaml")));
	}

	@Test
	void missingTablePreparationIsExplicitAndPersistedInTheSharedFormat() throws Exception {
		final var saved = new TransferFileJobDraftBuilder().save(snapshot(), List.of("PUBLIC.ITEMS"), 100, true,
				"__NULL__", TransferJobConfiguration.SchemaMode.CREATE_MISSING, directory.resolve("create.yaml"));
		assertEquals(TransferJobConfiguration.SchemaMode.CREATE_MISSING,
				new TransferJobConfigurationIO().read(saved.file()).configuration().schemaMode());
	}

	private BulkMigrationJobDraftBuilder.SchemaSnapshot snapshot() throws Exception {
		return snapshot("PUBLIC");
	}

	@Test
	void accessCsvJobOmitsUnnamedSourceSchema() throws Exception {
		var saved = new TransferFileJobDraftBuilder().save(snapshot(""), List.of("ITEMS"), 100, true, "__NULL__",
				directory.resolve("access.yaml"));
		assertNull(saved.configuration().sourceSchema());
		assertFalse(saved.text().contains("sourceSchema:"));
		assertEquals(saved, new TransferJobConfigurationIO().read(saved.file()));
	}

	private BulkMigrationJobDraftBuilder.SchemaSnapshot snapshot(String schemaName) throws Exception {
		final var schema = new Schema(schemaName);
		for (String name : List.of("ITEMS", "DATES", "TEXT_KEYS")) {
			final var table = new Table(name);
			final var key = new Column("ID").setDataType(name.equals("TEXT_KEYS") ? DataType.VARCHAR : DataType.INT)
					.setNotNull(true);
			table.getColumns().add(key);
			table.setPrimaryKey(key);
			if (name.equals("DATES"))
				table.getColumns().add(new Column("DAY_VALUE").setDataType(DataType.TIME).setLength(6));
			schema.getTables().add(table);
		}
		final Path xml = directory.resolve("source-schema.xml");
		schema.writeXml(xml.toFile());
		return new BulkMigrationJobDraftBuilder().inspect(xml);
	}
}
