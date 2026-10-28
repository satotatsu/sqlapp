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
		final var schema = new Schema("PUBLIC");
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
