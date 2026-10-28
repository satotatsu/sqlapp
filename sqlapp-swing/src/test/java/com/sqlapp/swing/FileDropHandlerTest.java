/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.datatransfer.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDropHandlerTest {
	@Test
	void refusedOpenKeepsEditedTextAndDoesNotInsertAFilePath() throws Exception {
		final Path sql = Files.writeString(directory.resolve("new.sql"), "select 2;");
		SwingUtilities.invokeAndWait(() -> {
			final var text = new JTextArea("unsaved SQL");
			final var requested = new AtomicReference<Path>();
			FileDropHandler.file(text, List.of("sql"), () -> true, path -> {
				requested.set(path);
				return false;
			}, failure -> fail(failure));
			assertFalse(drop(text, sql));
			assertEquals(sql, requested.get());
			assertEquals("unsaved SQL", text.getText());
		});
	}

	@TempDir
	Path directory;

	static Transferable files(final Path... paths) {
		return new Transferable() {
			public DataFlavor[] getTransferDataFlavors() {
				return new DataFlavor[] { DataFlavor.javaFileListFlavor };
			}

			public boolean isDataFlavorSupported(final DataFlavor flavor) {
				return DataFlavor.javaFileListFlavor.equals(flavor);
			}

			public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException {
				if (!isDataFlavorSupported(flavor))
					throw new UnsupportedFlavorException(flavor);
				return java.util.Arrays.stream(paths).map(Path::toFile).toList();
			}
		};
	}

	static boolean drop(final JComponent target, final Path... paths) {
		return target.getTransferHandler().importData(new TransferHandler.TransferSupport(target, files(paths)));
	}

	@Test
	void fileAndFolderPathsValidateBeforeChangingAndKeepTextClipboardOperations() throws Exception {
		final Path xml = Files.writeString(directory.resolve("日本語 schema.XML"), "<schema/>");
		final Path sql = Files.writeString(directory.resolve("wrong.sql"), "select 1;");
		SwingUtilities.invokeAndWait(() -> {
			final var errors = new AtomicReference<Throwable>();
			final var field = new JTextField("original");
			FileDropHandler.path(field, false, List.of("xml"), () -> true, errors::set);
			assertTrue(drop(field, xml));
			assertEquals(xml.toAbsolutePath().toString(), field.getText());
			assertFalse(drop(field, xml, xml));
			assertTrue(errors.get().getMessage().contains("exactly one"));
			assertEquals(xml.toString(), field.getText());
			assertFalse(drop(field, sql));
			assertFalse(drop(field, directory));
			assertEquals(xml.toString(), field.getText());
			field.selectAll();
			assertTrue(field.getTransferHandler()
					.importData(new TransferHandler.TransferSupport(field, new StringSelection("typed path"))));
			assertEquals("typed path", field.getText());
			field.selectAll();
			final var clipboard = new Clipboard("test");
			field.getTransferHandler().exportToClipboard(field, clipboard, TransferHandler.COPY);
			try {
				assertEquals("typed path", clipboard.getData(DataFlavor.stringFlavor));
			} catch (Exception failure) {
				throw new AssertionError(failure);
			}
			final var folder = new JTextField("old folder");
			FileDropHandler.path(folder, true, List.of(), () -> true, errors::set);
			assertTrue(drop(folder, directory));
			assertFalse(drop(folder, xml));
			assertEquals(directory.toString(), folder.getText());
		});
	}

	@Test
	void fileOrFolderSelectionAcceptsOnePathAndPreservesInputOnInvalidOrBlockedDrops() throws Exception {
		final Path manifest = Files.writeString(directory.resolve("manifest.json"), "{}");
		final Path wrong = Files.writeString(directory.resolve("table.csv"), "ID\n");
		SwingUtilities.invokeAndWait(() -> {
			final var field = new JTextField("old");
			final var ready = new AtomicBoolean(true);
			final var errors = new AtomicReference<Throwable>();
			FileDropHandler.fileOrDirectory(field, List.of("json"), ready::get, errors::set);
			assertTrue(drop(field, directory));
			assertEquals(directory.toString(), field.getText());
			assertTrue(drop(field, manifest));
			assertEquals(manifest.toString(), field.getText());
			assertFalse(drop(field, wrong));
			assertEquals(manifest.toString(), field.getText());
			assertFalse(drop(field, directory, manifest));
			assertTrue(errors.get().getMessage().contains("exactly one"));
			ready.set(false);
			assertFalse(drop(field, directory));
			assertEquals(manifest.toString(), field.getText());
			ready.set(true);
			field.setEnabled(false);
			assertFalse(drop(field, directory));
		});
	}

	@Test
	void multipleFileValidationIsAtomicAndHonorsReceiverRefusal() throws Exception {
		final Path first = Files.writeString(directory.resolve("first.jar"), "fixture");
		final Path second = Files.writeString(directory.resolve("second.JAR"), "fixture");
		final Path wrong = Files.writeString(directory.resolve("wrong.json"), "{}");
		SwingUtilities.invokeAndWait(() -> {
			final var calls = new AtomicInteger();
			final var accepted = new AtomicBoolean(true);
			final var panel = new JPanel();
			FileDropHandler.files(panel, List.of("jar"), () -> true, paths -> {
				calls.incrementAndGet();
				assertEquals(List.of(first, second), paths);
				return accepted.get();
			}, error -> {
			});
			assertFalse(drop(panel, first, wrong));
			assertEquals(0, calls.get());
			assertTrue(drop(panel, first, second));
			assertEquals(1, calls.get());
			accepted.set(false);
			assertFalse(drop(panel, first, second));
			assertEquals(2, calls.get());
		});
	}

	@Test
	void busyOrDisabledComponentsRejectFilesWithoutPassingThemToTextHandler() throws Exception {
		final Path sql = Files.writeString(directory.resolve("file.sql"), "select 1;");
		SwingUtilities.invokeAndWait(() -> {
			final var ready = new AtomicBoolean(false);
			final var text = new JTextArea("Keep edited text");
			FileDropHandler.file(text, List.of("sql"), ready::get, path -> {
				fail("Blocked");
				return true;
			}, failure -> fail(failure));
			assertFalse(drop(text, sql));
			assertFalse(text.getTransferHandler().canImport(text, files(sql).getTransferDataFlavors()));
			ready.set(true);
			text.setEnabled(false);
			assertFalse(drop(text, sql));
			assertEquals("Keep edited text", text.getText());
		});
	}
}
