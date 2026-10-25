/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Path;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/** In-memory location for one editor; updated only after successful file IO. */
final class EditorFileChooser {
	private Path directory;

	void remember(final Path file) {
		directory = file.toAbsolutePath().normalize().getParent();
	}

	JFileChooser create(final FileNameExtensionFilter filter, final String suggestedName) {
		final var chooser = new JFileChooser(directory == null ? null : directory.toFile());
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileFilter(filter);
		if (suggestedName != null)
			chooser.setSelectedFile(new java.io.File(chooser.getCurrentDirectory(), suggestedName));
		return chooser;
	}
}
