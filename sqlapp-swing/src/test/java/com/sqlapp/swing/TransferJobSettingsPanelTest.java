/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;

class TransferJobSettingsPanelTest {
	@TempDir
	Path directory;
	private final TransferJobConfigurationIO io = new TransferJobConfigurationIO();

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void schemaOnlySettingsRoundTripThroughDirectRouteWithoutEnablingDataOptions(boolean alreadySchemaOnly)
			throws Exception {
		var file = Files.writeString(directory.resolve("schema.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [NO_KEY]\nschemaMode: CREATE_MISSING\ndataMode: "
						+ (alreadySchemaOnly ? "SCHEMA_ONLY" : "JDBC_DIRECT") + "\n");
		var saved = new AtomicReference<TransferJobConfigurationIO.Snapshot>();
		var panel = load(file, saved);
		var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			if (!alreadySchemaOnly)
				panel.schemaOnly.doClick(0);
			assertTrue(panel.schemaOnly.isSelected());
			assertFalse(panel.verify.isEnabled());
			assertFalse(panel.createMissing.isEnabled());
			assertFalse(panel.chunk.isEnabled());
			panel.targetSchema.setText("ARCHIVE");
			onIdle(panel, done);
			panel.save.doClick(0);
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertNotNull(saved.get());
		var actual = io.read(file).configuration();
		assertEquals(TransferJobConfiguration.DataMode.SCHEMA_ONLY, actual.dataMode());
		assertFalse(actual.verify());
		assertEquals("ARCHIVE", actual.targetSchema());
	}

	private Path job() throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"), """
				formatVersion: 1
				source: from
				target: to
				tables: [PUBLIC.ITEMS]
				schemaMode: REQUIRE_EXISTING
				sourceSchema: PUBLIC
				mappingFile: maps/mapping.yaml
				dataMode: FILE
				files: {directory: packages, nullValue: __NULL__, unsupportedTemporalPolicy: ERROR}
				""");
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void savesEditableSettingsAndPreservesSelectionsRelativePathsAndAdvancedOptions(boolean fileTransport)
			throws Exception {
		final var file = job();
		if (!fileTransport)
			Files.writeString(file, Files.readString(file).replace(
					"dataMode: FILE\nfiles: {directory: packages, nullValue: __NULL__, unsupportedTemporalPolicy: ERROR}\n",
					"dataMode: JDBC_DIRECT\n"));
		final var original = io.read(file);
		final var saved = new AtomicReference<TransferJobConfigurationIO.Snapshot>();
		final var panel = load(file, saved);
		final var finished = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.verify.isSelected());
			assertFalse(panel.strict.isSelected());
			panel.targetSchema.setText("Archive Schema");
			panel.createMissing.setSelected(true);
			panel.verify.setSelected(false);
			panel.strict.setSelected(true);
			panel.chunk.setValue(42);
			onIdle(panel, finished);
			panel.save.doClick(0);
			assertTrue(panel.isBusy());
			assertFalse(panel.cancel.isEnabled());
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		final var actual = io.read(file);
		assertNotNull(saved.get());
		assertNotEquals(original.fingerprint(), actual.fingerprint());
		final var config = actual.configuration();
		assertEquals("Archive Schema", config.targetSchema());
		assertEquals(42, config.chunkSize());
		assertFalse(config.verify());
		assertEquals(TransferJobConfiguration.SchemaMode.CREATE_MISSING, config.schemaMode());
		assertEquals(TransferJobConfiguration.CompatibilityPolicy.ERROR, config.compatibilityPolicy());
		assertEquals(original.configuration().files(), config.files());
		assertEquals(original.configuration().tables(), config.tables());
		assertEquals(original.configuration().sourceSchema(), config.sourceSchema());
		assertEquals(original.mappingFile(), actual.mappingFile());
		assertEquals(original.filesDirectory(), actual.filesDirectory());
		assertEquals("from", config.source());
		assertEquals("to", config.target());
	}

	@Test
	void externalChangeIsNotOverwrittenAndDoesNotPublishSuccessfulSave() throws Exception {
		final var file = job();
		final var saved = new AtomicReference<TransferJobConfigurationIO.Snapshot>();
		final var panel = load(file, saved);
		final String external = Files.readString(file) + "# changed externally\n";
		Files.writeString(file, external);
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			panel.targetSchema.setText("OTHER");
			onIdle(panel, done);
			panel.save.doClick(0);
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertNull(saved.get());
		assertEquals(external, Files.readString(file));
		SwingUtilities.invokeAndWait(
				() -> assertTrue(panel.status.getText().contains("changed outside"), panel.status.getText()));
	}

	@Test
	void invalidChunkInputCannotWriteOrPublishSettings() throws Exception {
		final var file = job();
		final String original = Files.readString(file);
		final var saved = new AtomicReference<TransferJobConfigurationIO.Snapshot>();
		final var panel = load(file, saved);
		SwingUtilities.invokeAndWait(() -> {
			((JSpinner.DefaultEditor) panel.chunk.getEditor()).getTextField().setText("0");
			panel.save.doClick(0);
			assertFalse(panel.isBusy());
			assertTrue(panel.status.getText().contains("Check chunk size"));
		});
		assertNull(saved.get());
		assertEquals(original, Files.readString(file));
	}

	@Test
	void completedPackageAndWrongRouteCannotBeEdited() throws Exception {
		final var file = job();
		final var done = new CountDownLatch(1);
		final var ref = new AtomicReference<TransferJobSettingsPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new TransferJobSettingsPanel(TransferJobConfiguration.DataMode.JDBC_DIRECT,
					value -> fail("Wrong route"), () -> {
					});
			ref.set(panel);
			onIdle(panel, done);
			panel.load(file);
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(ref.get().save.isEnabled());
			assertTrue(ref.get().status.getText().contains("route"));
		});
		Files.writeString(directory.resolve("manifest.json"), "{}");
		Files.writeString(directory.resolve("source-schema.xml"), "<schema/>");
		final var blocked = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new TransferJobSettingsPanel(TransferJobConfiguration.DataMode.FILE,
					value -> fail("Package"), () -> {
					});
			ref.set(panel);
			onIdle(panel, blocked);
			panel.load(file);
		});
		assertTrue(blocked.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(ref.get().save.isEnabled());
			assertTrue(ref.get().status.getText().contains("Completed packages"));
		});
	}

	@Test
	void packageCreatedAfterLoadAlsoPreventsSave() throws Exception {
		final var file = job();
		final String original = Files.readString(file);
		final var saved = new AtomicReference<TransferJobConfigurationIO.Snapshot>();
		final var panel = load(file, saved);
		Files.writeString(directory.resolve("manifest.json"), "{}");
		Files.writeString(directory.resolve("source-schema.xml"), "<schema/>");
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			onIdle(panel, done);
			panel.save.doClick(0);
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertNull(saved.get());
		assertEquals(original, Files.readString(file));
	}

	private TransferJobSettingsPanel load(Path file, AtomicReference<TransferJobConfigurationIO.Snapshot> saved)
			throws Exception {
		final var done = new CountDownLatch(1);
		final var ref = new AtomicReference<TransferJobSettingsPanel>();
		final var actualMode = io.read(file).configuration().dataMode();
		final var mode = actualMode == TransferJobConfiguration.DataMode.SCHEMA_ONLY
				? TransferJobConfiguration.DataMode.JDBC_DIRECT
				: actualMode;
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new TransferJobSettingsPanel(mode, saved::set, () -> {
			});
			ref.set(panel);
			onIdle(panel, done);
			panel.load(file);
			assertTrue(panel.isBusy());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		return ref.get();
	}

	private static void onIdle(TransferJobSettingsPanel panel, CountDownLatch done) {
		panel.cancel.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				done.countDown();
		});
	}
}
