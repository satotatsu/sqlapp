/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDropScreensTest {
	@Test
	void editorTextClipboardKeepsNativeUndoAfterDropSupportInstalled() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var editor = new SqlEditorPanel(path -> false);
			final var original = CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL, true);
			final var history = new EditorEditHistory(original);
			history.resetWith(() -> original.setText("select 1"));
			original.select(0, 6);
			editor.navigation.history.resetWith(() -> editor.text.setText("select 1"));
			editor.text.select(0, 6);
			final var clipboard = new java.awt.datatransfer.Clipboard("editor-test");
			editor.text.getTransferHandler().exportToClipboard(editor.text, clipboard, TransferHandler.COPY);
			try {
				assertEquals("select", clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor));
			} catch (Exception failure) {
				throw new AssertionError(failure);
			}
			assertTrue(editor.text.getTransferHandler().importData(new TransferHandler.TransferSupport(editor.text,
					new java.awt.datatransfer.StringSelection("update"))));
			assertEquals("update 1", editor.text.getText());
			assertTrue(original.getTransferHandler().importData(new TransferHandler.TransferSupport(original,
					new java.awt.datatransfer.StringSelection("update"))));
			for (int step = 0; step < 4 && history.undo.isEnabled(); step++) {
				assertTrue(editor.navigation.history.undo.isEnabled());
				history.undo();
				editor.navigation.history.undo();
				assertEquals(original.getText(), editor.text.getText());
			}
			assertFalse(history.undo.isEnabled());
			assertFalse(editor.navigation.history.undo.isEnabled());
			assertEquals("select 1", editor.text.getText());
		});
	}

	@TempDir
	Path directory;

	@Test
	void pathFieldsSelectOnlyTheirOwnInputWithoutStartingWork() throws Exception {
		final Path xml = Files.writeString(directory.resolve("schema.xml"), "<schema/>");
		final Path yaml = Files.writeString(directory.resolve("job.yml"), "{}");
		final Path json = Files.writeString(directory.resolve("report.json"), "{}");
		SwingUtilities.invokeAndWait(() -> {
			final var comparison = new SchemaComparisonPanel((a, b) -> {
				fail("No comparison on drop");
				return null;
			}, (a, b, c) -> fail("No writes"));
			comparison.target.setText("keep-target.xml");
			assertTrue(FileDropHandlerTest.drop(comparison.original, xml));
			assertEquals(xml.toString(), comparison.original.getText());
			assertEquals("keep-target.xml", comparison.target.getText());
			assertFalse(comparison.isBusy());
			final var schema = new SchemaMigrationPlanPanel((a, b, c, d, e) -> {
				fail("No connection");
				return null;
			}, (a, b) -> fail("No writes"));
			assertTrue(FileDropHandlerTest.drop(schema.upDirectory, directory));
			assertEquals(directory.toString(), schema.upDirectory.getText());
			assertFalse(schema.isBusy());
			assertFalse(schema.apply.isEnabled());
			final var data = new DataMigrationPlanPanel((a, b, c, d, e) -> {
				fail("No connection");
				return null;
			}, (a, b) -> fail("No writes"));
			assertTrue(FileDropHandlerTest.drop(data.configurationFile, yaml));
			assertEquals(yaml.toString(), data.configurationFile.getText());
			assertFalse(data.isBusy());
			final var reports = new ReportReviewPanel((a, b) -> {
				fail("No report read until Load");
				return null;
			});
			reports.type.setSelectedItem(ReportReviewService.Type.BULK_FAILURE);
			assertTrue(FileDropHandlerTest.drop(reports.file, json));
			assertEquals(json.toString(), reports.file.getText());
			assertEquals(ReportReviewService.Type.BULK_FAILURE, reports.type.getSelectedItem());
			assertFalse(reports.isBusy());
			final var draft = new JobDraftPanel(text -> fail("No draft on drop"));
			assertTrue(FileDropHandlerTest.drop(draft.schemaFile, xml));
			assertEquals(xml.toString(), draft.schemaFile.getText());
			assertFalse(draft.create.isEnabled());
		});
	}

	@Test
	void editorDropsOpenFilesThroughExistingAsyncReadersAndRejectConcurrentDrop() throws Exception {
		final Path sqlFile = Files.writeString(directory.resolve("drop.sql"), "select '日';\r\n");
		final Path yamlFile = Files.writeString(directory.resolve("drop.yaml"), "{}\n");
		final var sql = new AtomicReference<SqlEditorPanel>();
		final var yaml = new AtomicReference<JobConfigurationEditorPanel>();
		final var finished = new CountDownLatch(2);
		SwingUtilities.invokeAndWait(() -> {
			sql.set(new SqlEditorPanel(path -> false));
			yaml.set(new JobConfigurationEditorPanel(snapshot -> fail("No plan on drop")));
			onEnabled(sql.get().open, finished);
			onEnabled(yaml.get().open, finished);
			assertTrue(FileDropHandlerTest.drop(sql.get().text, sqlFile));
			assertTrue(sql.get().isBusy());
			assertFalse(FileDropHandlerTest.drop(sql.get().file, sqlFile));
			assertTrue(FileDropHandlerTest.drop(yaml.get().file, yamlFile));
			assertTrue(yaml.get().isBusy());
			assertFalse(FileDropHandlerTest.drop(yaml.get().text, yamlFile));
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("select '日';\r\n", sql.get().text.getText());
			assertFalse(sql.get().isDirty());
			assertEquals("{}\n", yaml.get().text.getText());
			assertFalse(yaml.get().isDirty());
			assertFalse(FileDropHandlerTest.drop(sql.get().text, yamlFile));
			assertEquals("select '日';\r\n", sql.get().text.getText());
		});
	}

	@Test
	void profileDropReadsWithoutConnectingAndClearsPasswordOnlyOnSuccessfulLoad() throws Exception {
		final var profile = new ConnectionProfileService.Profile(1, "jdbc:hsqldb:mem:drop_profile", "SA", "");
		final Path file = directory.resolve("profile.json");
		new ConnectionProfileService().write(profile, file);
		final var fields = new JTextField[] { new JTextField("old"), new JTextField("old-user"), new JTextField("") };
		final var password = new JPasswordField("secret");
		final var finished = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new ConnectionProfilePanel(fields[0], fields[1], fields[2], password);
			onEnabled(panel.load, finished);
			assertTrue(FileDropHandlerTest.drop(panel.load, file));
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(profile.jdbcUrl(), fields[0].getText());
			assertEquals(0, password.getPassword().length);
		});
	}

	@Test
	void jarDropLoadsDriverAndDependencyTogetherWithoutConnecting() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		try (var libraries = new JdbcDriverLibraries()) {
			final var panel = new AtomicReference<JdbcDriverPanel>();
			final var finished = new CountDownLatch(1);
			SwingUtilities.invokeAndWait(() -> {
				panel.set(new JdbcDriverPanel(libraries));
				onEnabled(panel.get().load, finished);
				assertTrue(FileDropHandlerTest.drop(panel.get().loaded, jars.driver, jars.dependency));
				assertTrue(panel.get().isBusy());
			});
			assertTrue(finished.await(5, TimeUnit.SECONDS));
			SwingUtilities.invokeAndWait(() -> {
				assertEquals(1, libraries.libraries().size());
				assertEquals("fixture.ChildDriver", panel.get().selectedDriver.getSelectedItem());
				assertTrue(panel.get().status.getText().contains("No database connection opened"));
			});
		}
	}

	private static void onEnabled(final JButton button, final CountDownLatch finished) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				finished.countDown();
		});
	}
}
