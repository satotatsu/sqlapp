/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Path;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * In-memory locations and optional navigation hints; never validates execution
 * inputs.
 */
final class EditorFileChooser {
	private Path directory;

	void remember(final Path file) {
		directory = file.toAbsolutePath().normalize().getParent();
	}

	void rememberDirectory(final Path folder) {
		if (folder != null)
			directory = folder.toAbsolutePath().normalize();
	}

	JFileChooser create(final FileNameExtensionFilter filter, final String suggestedName) {
		final var chooser = new JFileChooser(directory == null ? null : directory.toFile());
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setMultiSelectionEnabled(false);
		if (filter != null)
			chooser.setFileFilter(filter);
		if (suggestedName != null)
			chooser.setSelectedFile(new java.io.File(chooser.getCurrentDirectory(), suggestedName));
		return chooser;
	}

	JFileChooser forFile(final FileNameExtensionFilter filter, final String currentPath) {
		final var chooser = create(filter, null);
		final Path hint = hint(currentPath);
		if (hint != null)
			chooser.setCurrentDirectory((hint.getParent() == null ? hint : hint.getParent()).toFile());
		return chooser;
	}

	JFileChooser forDirectory(final String currentPath) {
		final var chooser = create(null, null);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		final Path hint = hint(currentPath);
		if (hint != null)
			chooser.setCurrentDirectory(hint.toFile());
		return chooser;
	}

	JFileChooser forOutput(final FileNameExtensionFilter filter, final String name, final String relatedFile) {
		if (directory != null)
			return create(filter, name);
		final var chooser = forFile(filter, relatedFile);
		chooser.setSelectedFile(new java.io.File(chooser.getCurrentDirectory(), name));
		return chooser;
	}

	private static Path hint(final String text) {
		if (text == null || text.isBlank())
			return null;
		try {
			return Path.of(text.trim()).toAbsolutePath().normalize();
		} catch (final java.nio.file.InvalidPathException invalidHint) {
			// A browse hint is optional; actual reads/plans still validate the unchanged
			// input.
			return null;
		}
	}

	JFileChooser forOutputDirectory(final FileNameExtensionFilter filter, final String name,
			final String relatedDirectory) {
		if (directory != null)
			return create(filter, name);
		final var chooser = forDirectory(relatedDirectory);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (filter != null)
			chooser.setFileFilter(filter);
		chooser.setSelectedFile(new java.io.File(chooser.getCurrentDirectory(), name));
		return chooser;
	}
}
