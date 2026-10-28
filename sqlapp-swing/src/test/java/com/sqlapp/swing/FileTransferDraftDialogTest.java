/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfigurationIO;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class FileTransferDraftDialogTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@ValueSource(strings = { "", "Archive Data" })
	void formSavesSelectedTargetSchemaWithoutConnectingOrChangingSourceSelection(String targetSchema) throws Exception {
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
			form.targetSchema().setText(targetSchema);
			form.picker().select.doClick(0);
			form.picker().create.doClick(0);
			assertTrue(form.picker().isBusy());
			assertFalse(form.targetSchema().isEnabled());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		final var config = new TransferJobConfigurationIO().read(saved.get()).configuration();
		assertEquals(targetSchema.isEmpty() ? null : targetSchema, config.targetSchema());
		assertEquals("PUBLIC", config.sourceSchema());
		assertEquals(java.util.List.of("PUBLIC.ITEMS"), config.tables());
	}
}
