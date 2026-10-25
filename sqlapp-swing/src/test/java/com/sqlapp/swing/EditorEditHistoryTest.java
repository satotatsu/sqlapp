/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorEditHistoryTest {
	@TempDir
	Path directory;

	@Test
	void undoRedoAndNewDocumentHistoryStayIndependent() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var text = new JTextArea();
			final var history = new EditorEditHistory(text);
			assertFalse(history.undo.isEnabled());
			text.append("one");
			text.append("two");
			history.undo();
			assertEquals("one", text.getText());
			history.redo();
			assertEquals("onetwo", text.getText());
			history.setBusy(true);
			history.undo();
			assertEquals("onetwo", text.getText());
			history.setBusy(false);
			history.undo();
			text.append("new");
			assertFalse(history.redo.isEnabled());
			history.resetWith(() -> text.setText("another document"));
			assertFalse(history.undo.isEnabled());
			assertFalse(history.redo.isEnabled());
			history.undo();
			assertEquals("another document", text.getText());
		});
	}

	@Test
	void undoToSavedContentClearsDirtyFlagButRequiresSyntaxRecheckForHandoff() throws Exception {
		final String yaml = "schemaFile: schema.xml\ntasks: []\n";
		final Path file = Files.writeString(directory.resolve("job.yaml"), yaml);
		final var opened = new CountDownLatch(1);
		final var checked = new CountDownLatch(1);
		final var panel = new AtomicReference<JobConfigurationEditorPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(snapshot -> {
			});
			panel.set(view);
			onFinished(view, opened);
			view.loadFrom(file);
		});
		assertTrue(opened.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(panel.get().navigation.history.undo.isEnabled());
			onFinished(panel.get(), checked);
			panel.get().checkSyntax();
		});
		assertTrue(checked.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertTrue(view.use.isEnabled());
			view.navigation.query.setText("schema.xml");
			view.navigation.replacement.setText("other.xml");
			view.navigation.replaceAllMatches();
			assertTrue(view.isDirty());
			assertFalse(view.use.isEnabled());
			view.navigation.history.undo();
			assertEquals(yaml, view.text.getText());
			assertFalse(view.isDirty());
			assertFalse(view.use.isEnabled());
			assertFalse(view.save.isEnabled());
			view.navigation.history.redo();
			assertTrue(view.isDirty());
			view.acceptDraft("schemaFile: new.xml\ntasks: []\n");
			assertFalse(view.navigation.history.undo.isEnabled());
			assertTrue(view.isDirty());
			assertFalse(view.use.isEnabled());
		});
	}

	@Test
	void savingKeepsHistoryAndUndoNeverChangesTheSavedFile() throws Exception {
		final String original = "schemaFile: schema.xml\ntasks: []\n";
		final Path file = Files.writeString(directory.resolve("saved.yaml"), original);
		final var opened = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		final var panel = new AtomicReference<JobConfigurationEditorPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobConfigurationEditorPanel(snapshot -> {
			});
			panel.set(view);
			onFinished(view, opened);
			view.loadFrom(file);
		});
		assertTrue(opened.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			panel.get().text.append("# added\n");
			onFinished(panel.get(), saved);
			panel.get().saveTo(file);
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertFalse(view.isDirty());
			assertTrue(view.use.isEnabled());
			view.navigation.history.undo();
			assertEquals(original, view.text.getText());
			assertTrue(view.isDirty());
			assertFalse(view.use.isEnabled());
			view.navigation.history.redo();
			assertFalse(view.isDirty());
			assertFalse(view.use.isEnabled());
		});
		assertEquals(original + "# added\n", Files.readString(file));
	}

	private static void onFinished(JobConfigurationEditorPanel panel, CountDownLatch latch) {
		panel.open.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				latch.countDown();
		});
	}
}
