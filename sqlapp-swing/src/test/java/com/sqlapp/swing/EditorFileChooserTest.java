/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorFileChooserTest {
	@TempDir
	Path directory;

	@Test
	void explicitInputHintsDoNotReplaceRememberedOutputFolderAndInvalidHintsRemainOptional() throws Exception {
		final Path other = Files.createDirectory(directory.resolve("input"));
		SwingUtilities.invokeAndWait(() -> {
			final var context = new EditorFileChooser();
			context.remember(directory.resolve("report.json"));
			final var filter = new FileNameExtensionFilter("JSON", "json");
			assertEquals(other.toFile(),
					context.forFile(filter, other.resolve("input.json").toString()).getCurrentDirectory());
			assertEquals(directory.toFile(), context.forFile(filter, "bad\u0000path").getCurrentDirectory());
			assertEquals(directory.toFile(), context.create(filter, "output.json").getCurrentDirectory());
			assertEquals(directory.resolve("output.json").toFile(),
					context.forOutput(filter, "output.json", other.resolve("input.json").toString()).getSelectedFile());
			assertEquals(directory.getRoot().toFile(),
					context.forFile(filter, directory.getRoot().toString()).getCurrentDirectory());
		});
	}

	@Test
	void firstOutputUsesRelatedInputAndDirectoryDialogsStayDirectoryOnly() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var context = new EditorFileChooser();
			final var filter = new FileNameExtensionFilter("JSON", "json");
			assertEquals(directory.resolve("audit.json").toFile(), context
					.forOutput(filter, "audit.json", directory.resolve("job.yaml").toString()).getSelectedFile());
			final var folder = context.forDirectory(directory.toString());
			assertEquals(JFileChooser.DIRECTORIES_ONLY, folder.getFileSelectionMode());
			assertEquals(directory.toFile(), folder.getCurrentDirectory());
			final var output = context.forOutputDirectory(filter, "plan.json", directory.toString());
			assertEquals(JFileChooser.FILES_ONLY, output.getFileSelectionMode());
			assertEquals(directory.resolve("plan.json").toFile(), output.getSelectedFile());
		});
	}

	@Test
	void suggestedNameStaysInRememberedFolderAndBrowsingDoesNotChangeNextDialog() throws Exception {
		final Path other = Files.createDirectory(directory.resolve("other"));
		SwingUtilities.invokeAndWait(() -> {
			final var context = new EditorFileChooser();
			context.remember(directory.resolve("job.yaml"));
			final var filter = new FileNameExtensionFilter("Jobs", "yaml", "yml", "json");
			final var save = context.create(filter, "job.yaml");
			assertEquals(directory.toFile(), save.getCurrentDirectory());
			assertEquals(directory.resolve("job.yaml").toFile(), save.getSelectedFile());
			assertSame(filter, save.getFileFilter());
			assertFalse(save.isMultiSelectionEnabled());
			assertEquals(JFileChooser.FILES_ONLY, save.getFileSelectionMode());
			save.setCurrentDirectory(other.toFile());
			final var open = context.create(filter, null);
			assertEquals(directory.toFile(), open.getCurrentDirectory());
			assertNull(open.getSelectedFile());
			final var independent = new EditorFileChooser();
			independent.remember(other.resolve("job.yaml"));
			assertEquals(other.toFile(), independent.create(filter, null).getCurrentDirectory());
			assertEquals(directory.toFile(), context.create(filter, null).getCurrentDirectory());
		});
	}
}
