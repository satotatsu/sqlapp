/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.datatype.DataType;

class TransferJobDraftBuilderTest {
	@TempDir
	Path directory;

	@Test
	void authorsCustomGeneratedKeyNamesForBothRoutes() throws Exception {
		var snapshot = schema();
		var builder = new TransferJobDraftBuilder();
		var direct = builder.saveDirect(snapshot, List.of("PUBLIC.ITEMS"), 100, true, null, null,
				TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY, directory.resolve("direct-key.yaml"),
				"ROW_ID");
		var schemaOnly = builder.saveSchemaOnly(snapshot, List.of("PUBLIC.ITEMS"), null, null,
				TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY, false,
				directory.resolve("schema-key.yaml"), "生成ID");
		assertEquals("ROW_ID",
				new TransferJobConfigurationIO().read(direct.file()).configuration().generatedKeyColumn());
		assertEquals("生成ID",
				new TransferJobConfigurationIO().read(schemaOnly.file()).configuration().generatedKeyColumn());
	}

	@Test
	void directDraftAcceptsKeylessInsertWhileGenericDraftRetainsKeyRequirements() throws Exception {
		var model = new Schema("");
		model.setCatalogName("CATALOG");
		var table = new Table("NO_KEY");
		table.getColumns().add(new Column("VALUE").setDataType(DataType.INT));
		model.getTables().add(table);
		Path xml = directory.resolve("keyless.xml");
		model.writeXml(xml.toFile());
		var generic = new BulkMigrationJobDraftBuilder();
		var snapshot = generic.inspect(xml);
		assertThrows(IllegalArgumentException.class, () -> generic.draft(snapshot, List.of("NO_KEY"), "", 100, true));
		var saved = new TransferJobDraftBuilder().saveDirect(snapshot, List.of("NO_KEY"), 100, true, null, "PUBLIC",
				TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY, directory.resolve("keyless.yaml"));
		assertEquals(List.of("NO_KEY"), saved.configuration().tables());
		assertEquals(TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY,
				saved.configuration().schemaMode());
	}

	@Test
	void authorsSharedDirectIntentWithOptionalPolicyAndTargetSchemaAndNeverOverwrites() throws Exception {
		final var snapshot = schema();
		final var builder = new TransferJobDraftBuilder();
		final var saved = builder.saveDirect(snapshot, List.of("PUBLIC.ITEMS"), 250, true,
				TransferJobConfiguration.CompatibilityPolicy.ERROR, "ARCHIVE", directory.resolve("transfer.yaml"));
		assertEquals(saved, new TransferJobConfigurationIO().read(saved.file()));
		assertEquals(TransferJobConfiguration.DataMode.JDBC_DIRECT, saved.configuration().dataMode());
		assertNull(saved.configuration().files());
		assertEquals("PUBLIC", saved.configuration().sourceSchema());
		assertEquals("ARCHIVE", saved.configuration().targetSchema());
		assertEquals(TransferJobConfiguration.CompatibilityPolicy.ERROR, saved.configuration().compatibilityPolicy());
		assertFalse(saved.text().contains("schemaFile"));
		assertFalse(saved.text().contains("jdbc:"));
		assertThrows(Exception.class,
				() -> builder.saveDirect(snapshot, List.of("PUBLIC.ITEMS"), 250, true, null, null, saved.file()));
		assertEquals(saved, new TransferJobConfigurationIO().read(saved.file()));
	}

	@Test
	void changedSchemaOrInvalidSelectionCannotPublishAJob() throws Exception {
		final var snapshot = schema();
		final var file = directory.resolve("transfer.yaml");
		final var builder = new TransferJobDraftBuilder();
		assertThrows(Exception.class,
				() -> builder.saveDirect(snapshot, List.of("MISSING"), 100, true, null, null, file));
		assertFalse(Files.exists(file));
		Files.writeString(snapshot.file(), Files.readString(snapshot.file()) + "\n<!-- modified -->");
		assertThrows(Exception.class,
				() -> builder.saveDirect(snapshot, List.of("ITEMS"), 100, true, null, null, file));
		assertFalse(Files.exists(file));
	}

	@Test
	void missingTablePreparationIsSavedOnlyWhenExplicitlySelected() throws Exception {
		final var saved = new TransferJobDraftBuilder().saveDirect(schema(), List.of("PUBLIC.ITEMS"), 100, true, null,
				null, TransferJobConfiguration.SchemaMode.CREATE_MISSING, directory.resolve("transfer.yaml"));
		assertEquals(TransferJobConfiguration.SchemaMode.CREATE_MISSING,
				new TransferJobConfigurationIO().read(saved.file()).configuration().schemaMode());
	}

	private BulkMigrationJobDraftBuilder.SchemaSnapshot schema() throws Exception {
		return schema("PUBLIC");
	}

	@Test
	void unnamedAccessSchemaIsOmittedForDirectAndSchemaOnlyJobs() throws Exception {
		var snapshot = schema("");
		var builder = new TransferJobDraftBuilder();
		var direct = builder.saveDirect(snapshot, List.of("ITEMS"), 100, true, null, "PUBLIC",
				directory.resolve("direct.yaml"));
		var schemaOnly = builder.saveSchemaOnly(snapshot, List.of("ITEMS"), null, "PUBLIC",
				directory.resolve("schema.yaml"));
		assertNull(direct.configuration().sourceSchema());
		assertNull(schemaOnly.configuration().sourceSchema());
		assertFalse(direct.text().contains("sourceSchema:"));
		assertFalse(schemaOnly.text().contains("sourceSchema:"));
	}

	private BulkMigrationJobDraftBuilder.SchemaSnapshot schema(String name) throws Exception {
		final var schema = new Schema(name);
		final var table = new Table("ITEMS");
		final var id = new Column("ID").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(id);
		table.setPrimaryKey(id);
		schema.getTables().add(table);
		final var file = directory.resolve("source-schema.xml");
		schema.writeXml(file.toFile());
		return new BulkMigrationJobDraftBuilder().inspect(file);
	}
}
