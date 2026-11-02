/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.FileFormat;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfigurationIO;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class FileTransferDraftDialogTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@CsvSource({ "'', CSV", "Archive Data, CSV", "'', JSONL", "Archive Data, JSONL", "'', TSV", "Archive Data, TSV" })
	void formSavesSelectedTargetSchemaWithoutConnectingOrChangingSourceSelection(String targetSchema, FileFormat format)
			throws Exception {
		final var schema = new Schema("PUBLIC");
		final var table = new Table("ITEMS");
		final var key = new Column("ID").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(key);
		table.setPrimaryKey(key);
		schema.getTables().add(table);
		final var xml = directory.resolve("source-schema.xml");
		schema.writeXml(xml.toFile());
		final var captured = new BulkMigrationJobDraftBuilder().inspect(xml);
		final var done = new CountDownLatch(1);
		final var saved = new AtomicReference<Path>();
		SwingUtilities.invokeAndWait(() -> {
			final var form = FileTransferDraftDialog.form(new DataMigrationDraftService.Prepared(directory, captured),
					path -> {
						saved.set(path);
						done.countDown();
					}, () -> fail("No cancellation requested"));
			assertEquals("", form.targetSchema().getText());
			assertEquals(FileFormat.CSV, form.format().getSelectedItem());
			assertEquals("UTF-8", form.fileOptions().encoding.getSelectedItem());
			assertEquals(com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.HeaderMode.VALIDATE,
					form.fileOptions().headerMode.getSelectedItem());
			form.format().setSelectedItem(FileFormat.JSONL);
			assertFalse(form.nullValue().isEnabled());
			form.format().setSelectedItem(FileFormat.CSV);
			assertTrue(form.nullValue().isEnabled());
			assertEquals("__SQLAPP_NULL__", form.nullValue().getText());
			form.format().setSelectedItem(format);
			form.fileOptions().encoding.getEditor().setItem("windows-31j");
			form.fileOptions().headerMode.setSelectedItem(
					com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.HeaderMode.NONE);
			form.targetSchema().setText(targetSchema);
			form.picker().select.doClick(0);
			form.picker().create.doClick(0);
			assertTrue(form.picker().isBusy());
			assertFalse(form.targetSchema().isEnabled());
			assertFalse(form.format().isEnabled());
			assertFalse(form.fileOptions().encoding.isEnabled());
			assertFalse(form.fileOptions().headerMode.isEnabled());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		final var config = new TransferJobConfigurationIO().read(saved.get()).configuration();
		assertEquals(targetSchema.isEmpty() ? null : targetSchema, config.targetSchema());
		assertEquals(format, config.files().format());
		assertEquals("windows-31j", config.files().encoding());
		assertEquals(com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.HeaderMode.NONE,
				config.files().headerMode());
		assertEquals(format == FileFormat.JSONL ? null : "__SQLAPP_NULL__", config.files().nullValue());
		assertEquals("PUBLIC", config.sourceSchema());
		assertEquals(java.util.List.of("PUBLIC.ITEMS"), config.tables());
	}
}
