/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot;

class JobConfigurationEditorPanelTest {
	@TempDir
	Path directory;
	private final String yaml = "# edit me\nschemaFile: schema.xml\ntasks: []\n";

	@Test
	void savesVerbatimInBackgroundAndPassesOnlySavedValidatedSnapshotToPlanning() throws Exception {
		final var chosen = new AtomicReference<Snapshot>();
		final var panel = new AtomicReference<JobConfigurationEditorPanel>();
		final var saved = new CountDownLatch(1);
		final Path destination = directory.resolve("job.yaml");
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(new BulkMigrationJobConfigurationEditorIO() {
				@Override
				public Snapshot write(Path file, String text, String fingerprint) throws java.io.IOException {
					assertFalse(SwingUtilities.isEventDispatchThread());
					return super.write(file, text, fingerprint);
				}
			}, chosen::set);
			panel.set(view);
			assertFalse(view.isDirty());
			assertTrue(view.canDiscardChanges());
			view.text.setText(yaml);
			assertTrue(view.isDirty());
			assertFalse(view.use.isEnabled());
			onFinished(view, saved);
			view.saveTo(destination);
			assertTrue(view.isBusy());
			assertFalse(view.text.isEditable());
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		assertEquals(yaml, Files.readString(destination));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertFalse(view.isDirty());
			assertTrue(view.use.isEnabled());
			assertNull(chosen.get());
			view.use.doClick();
			assertEquals(destination, chosen.get().file());
			assertEquals(yaml, chosen.get().text());
			view.text.append("# changed\n");
			assertFalse(view.use.isEnabled());
			assertTrue(view.save.isEnabled());
		});
		assertEquals(new BulkMigrationJobConfigurationEditorIO().read(destination).fingerprint(),
				chosen.get().fingerprint());
	}

	@Test
	void opensMalformedYamlForRepairAndDoesNotPretendSyntaxCheckIsLiveValidation() throws Exception {
		final Path source = Files.writeString(directory.resolve("broken.yaml"), "tasks: [");
		final var panel = new AtomicReference<JobConfigurationEditorPanel>();
		final var opened = new CountDownLatch(1);
		final var checked = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(snapshot -> fail("Invalid job cannot be selected"));
			panel.set(view);
			onFinished(view, opened);
			view.loadFrom(source);
		});
		assertTrue(opened.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("tasks: [", panel.get().text.getText());
			assertFalse(panel.get().isDirty());
			onFinished(panel.get(), checked);
			panel.get().checkSyntax();
		});
		assertTrue(checked.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().status.getText().contains("Operation failed"));
			assertFalse(panel.get().use.isEnabled());
			assertTrue(panel.get().text.isEditable());
		});
	}

	@Test
	void externalFileChangesAreNotOverwrittenAndBufferRemainsDirty() throws Exception {
		final Path source = Files.writeString(directory.resolve("job.yaml"), yaml);
		final var panel = new AtomicReference<JobConfigurationEditorPanel>();
		final var opened = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(snapshot -> {
			});
			panel.set(view);
			onFinished(view, opened);
			view.loadFrom(source);
		});
		assertTrue(opened.await(5, TimeUnit.SECONDS));
		Files.writeString(source, yaml + "# external\n");
		SwingUtilities.invokeAndWait(() -> {
			panel.get().text.append("# buffer\n");
			onFinished(panel.get(), saved);
			panel.get().saveTo(source);
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		assertTrue(Files.readString(source).endsWith("# external\n"));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().text.getText().endsWith("# buffer\n"));
			assertTrue(panel.get().isDirty());
			assertFalse(panel.get().use.isEnabled());
			assertTrue(panel.get().status.getText().contains("changed outside"));
		});
	}

	@Test
	void lateOpenDoesNotDiscardNewerProgrammaticEdits() throws Exception {
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<JobConfigurationEditorPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(new BulkMigrationJobConfigurationEditorIO() {
				@Override
				public Snapshot read(Path file) {
					assertFalse(SwingUtilities.isEventDispatchThread());
					await(release);
					return new Snapshot(file, yaml, "sha256:" + "a".repeat(64));
				}
			}, snapshot -> {
			});
			panel.set(view);
			onFinished(view, finished);
			assertTrue(view.requestOpen(directory.resolve("job.yaml")));
			assertFalse(view.requestOpen(directory.resolve("other.yaml")));
			view.text.setText("newer edits");
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("newer edits", panel.get().text.getText());
			assertTrue(panel.get().isDirty());
			assertTrue(panel.get().status.getText().contains("Inputs changed"));
		});
	}

	@Test
	void successfulSaveDoesNotMarkNewerBufferEditsAsSaved() throws Exception {
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<JobConfigurationEditorPanel>();
		final Path destination = directory.resolve("job.yaml");
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(new BulkMigrationJobConfigurationEditorIO() {
				@Override
				public Snapshot write(Path file, String text, String expected) throws java.io.IOException {
					await(release);
					return super.write(file, text, expected);
				}
			}, snapshot -> fail("Buffer is newer than saved file"));
			panel.set(view);
			view.text.setText(yaml);
			onFinished(view, finished);
			view.saveTo(destination);
			view.text.append("# newer\n");
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(yaml, Files.readString(destination));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().isDirty());
			assertFalse(panel.get().use.isEnabled());
			assertTrue(panel.get().status.getText().contains("newer unsaved edits"));
		});
	}

	@Test
	void checkingSyntaxDoesNotSaveOrSelectAnUnsavedBuffer() throws Exception {
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<JobConfigurationEditorPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(snapshot -> fail("Unsaved job cannot be selected"));
			panel.set(view);
			view.text.setText(yaml);
			onFinished(view, finished);
			view.checkSyntax();
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().status.getText().contains("still require plan inspection"));
			assertFalse(panel.get().use.isEnabled());
			assertTrue(panel.get().isDirty());
		});
		try (var files = Files.list(directory)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void lineNumbersAndCaretPositionFollowEditsWithoutChangingTheBuffer() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(snapshot -> {
			});
			view.text.setText("first\nsecond\nthird");
			assertEquals(3, view.text.getLineCount());
			assertTrue(view.editorScroll.getLineNumbersEnabled());
			view.text.setCaretPosition(8);
			assertEquals("Line 2, column 3", view.position.getText());
			final String original = view.text.getText();
			view.moveToDiagnostic(2, 999);
			assertEquals("Line 2, column 7", view.position.getText());
			assertEquals(original, view.text.getText());
			view.text.setText("one\r\ntwo\r\n");
			assertEquals(3, view.text.getLineCount());
			assertTrue(view.editorScroll.getLineNumbersEnabled());
			view.moveToDiagnostic(1, 100);
			assertEquals(3, view.text.getCaretPosition());
			view.moveToDiagnostic(999, 999);
			assertEquals(view.text.getDocument().getLength(), view.text.getCaretPosition());
			view.text.setText("");
			assertEquals(1, view.text.getLineCount());
			assertEquals("Line 1, column 1", view.position.getText());
		});
	}

	@Test
	void syntaxFailureMovesCaretToReportedLocationAndPreservesUnsavedText() throws Exception {
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<JobConfigurationEditorPanel>();
		final String invalid = "schemaFile: schema.xml\ntasks: [\n";
		final var diagnostic = assertThrows(BulkMigrationJobConfigurationEditorIO.ValidationException.class,
				() -> new BulkMigrationJobConfigurationEditorIO().validate(invalid));
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(snapshot -> {
			});
			panel.set(view);
			view.text.setText(invalid);
			view.text.setCaretPosition(0);
			onFinished(view, finished);
			view.checkSyntax();
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().position.getText().startsWith("Line " + diagnostic.getLine() + ","));
			assertEquals(invalid, panel.get().text.getText());
			assertTrue(panel.get().isDirty());
			assertFalse(panel.get().use.isEnabled());
		});
	}

	@Test
	void staleSyntaxFailureDoesNotMoveCaretInNewerBuffer() throws Exception {
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<JobConfigurationEditorPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(new BulkMigrationJobConfigurationEditorIO() {
				@Override
				public com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfiguration validate(String text) {
					await(release);
					return super.validate(text);
				}
			}, snapshot -> {
			});
			panel.set(view);
			view.text.setText("schemaFile: schema.xml\ntasks: [\n");
			onFinished(view, finished);
			view.checkSyntax();
			view.text.setText("newer buffer\nsecond line");
			view.text.setCaretPosition(1);
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(1, panel.get().text.getCaretPosition());
			assertEquals("newer buffer\nsecond line", panel.get().text.getText());
		});
	}

	private static void onFinished(JobConfigurationEditorPanel panel, CountDownLatch latch) {
		panel.open.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				latch.countDown();
		});
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS))
				throw new IllegalStateException("Timeout");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	@Test
	void controlSSavesYamlUsingTheExistingValidationAndSnapshotRules() throws Exception {
		final Path path = Files.writeString(directory.resolve("shortcut.yaml"), yaml);
		final var ref = new AtomicReference<JobConfigurationEditorPanel>();
		final var opened = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new JobConfigurationEditorPanel(snapshot -> {
			});
			ref.set(panel);
			panel.open.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					opened.countDown();
			});
			panel.loadFrom(path);
		});
		assertTrue(opened.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			panel.text.append("# keyboard save\n");
			panel.open.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					saved.countDown();
			});
			EditorFileShortcutsTest.invoke(panel.text, "control S");
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		assertEquals(yaml + "# keyboard save\n", Files.readString(path));
		SwingUtilities.invokeAndWait(() -> assertFalse(ref.get().isDirty()));
	}

	@Test
	void reloadUsesCurrentFileAndRequiresFreshValidationWithoutWriting() throws Exception {
		final Path path = Files.writeString(directory.resolve("reload.yaml"), yaml);
		final var ref = new AtomicReference<JobConfigurationEditorPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new JobConfigurationEditorPanel(ignored -> fail("Reload must not select a plan"));
			ref.set(panel);
			assertFalse(panel.reload.isEnabled());
			assertFalse(panel.reloadFromDisk());
		});
		final var panel = ref.get();
		finishReloadOperation(panel, () -> panel.loadFrom(path));
		finishReloadOperation(panel, panel::checkSyntax);
		SwingUtilities.invokeAndWait(() -> assertTrue(panel.use.isEnabled()));
		Files.writeString(path, yaml + "# external\n");
		finishReloadOperation(panel, () -> {
			assertTrue(panel.reloadFromDisk());
			assertFalse(panel.reload.isEnabled());
			assertFalse(panel.reloadFromDisk());
		});
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(yaml + "# external\n", panel.text.getText());
			assertFalse(panel.isDirty());
			assertFalse(panel.use.isEnabled());
			assertFalse(panel.text.canUndo());
		});
		Files.delete(path);
		finishReloadOperation(panel, () -> assertTrue(panel.reloadFromDisk()));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(yaml + "# external\n", panel.text.getText());
			assertTrue(panel.status.getText().startsWith("Operation failed:"));
			assertEquals(panel.status.getText(), panel.status.getToolTipText());
			assertTrue(panel.statusPanel.details.isEnabled());
			assertTrue(panel.reload.isEnabled());
			panel.fresh.doClick();
			assertFalse(panel.reload.isEnabled());
			assertEquals(panel.status.getText(), panel.status.getToolTipText());
			assertFalse(panel.status.getToolTipText().startsWith("Operation failed:"));
		});
		assertFalse(Files.exists(path));
	}

	private static void finishReloadOperation(JobConfigurationEditorPanel panel, Runnable action) throws Exception {
		final var signal = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			onFinished(panel, signal);
			action.run();
		});
		assertTrue(signal.await(5, TimeUnit.SECONDS));
	}

}
