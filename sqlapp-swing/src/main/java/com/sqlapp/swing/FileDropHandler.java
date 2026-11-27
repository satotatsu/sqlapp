/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.datatransfer.*;
import java.awt.event.InputEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.*;
import javax.swing.*;

/**
 * File-list drops use the ordinary selection/open path; text transfers keep
 * their existing handler.
 */
final class FileDropHandler extends TransferHandler {
	private static final long serialVersionUID = 1L;
	private final TransferHandler previous;
	private final boolean multiple;
	private final boolean directory;
	private final boolean allowDirectories;
	private final List<String> extensions;
	private final BooleanSupplier ready;
	private final Predicate<List<Path>> receive;
	private final Consumer<Throwable> failed;

	static void path(final JTextField field, final boolean directory, final List<String> extensions,
			final BooleanSupplier ready, final Consumer<Throwable> failed) {
		install(field, false, directory, extensions, ready, paths -> {
			field.setText(paths.getFirst().toString());
			return true;
		}, failed);
	}

	static void file(final JComponent component, final List<String> extensions, final BooleanSupplier ready,
			final Predicate<Path> receive, final Consumer<Throwable> failed) {
		install(component, false, false, extensions, ready, paths -> receive.test(paths.getFirst()), failed);
	}

	static void fileOrDirectory(final JTextField field, final List<String> extensions, final BooleanSupplier ready,
			final Consumer<Throwable> failed) {
		field.setTransferHandler(
				new FileDropHandler(field.getTransferHandler(), false, false, true, extensions, ready, paths -> {
					field.setText(paths.getFirst().toString());
					return true;
				}, failed));
		field.setToolTipText("Drop one " + String.join(" / ", extensions) + " file or its containing folder here.");
	}

	static void files(final JComponent component, final List<String> extensions, final BooleanSupplier ready,
			final Predicate<List<Path>> receive, final Consumer<Throwable> failed) {
		install(component, true, false, extensions, ready, receive, failed);
	}

	static Consumer<Throwable> errors(final JLabel status, final EditorStatusPanel details) {
		return failure -> {
			status.setText("File selection failed: "
					+ (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
			details.setDetails(EditorStatusPanel.failureDetails(failure));
		};
	}

	private static void install(final JComponent component, final boolean multiple, final boolean directory,
			final List<String> extensions, final BooleanSupplier ready, final Predicate<List<Path>> receive,
			final Consumer<Throwable> failed) {
		component.setTransferHandler(new FileDropHandler(component.getTransferHandler(), multiple, directory, false,
				extensions, ready, receive, failed));
		if (component.getToolTipText() == null)
			component.setToolTipText(directory ? "Drop one folder here."
					: "Drop " + (multiple ? "local " : "one ") + String.join(" / ", extensions) + " file"
							+ (multiple ? "s" : "") + " here.");
	}

	private FileDropHandler(final TransferHandler previous, final boolean multiple, final boolean directory,
			final boolean allowDirectories, final List<String> extensions, final BooleanSupplier ready,
			final Predicate<List<Path>> receive, final Consumer<Throwable> failed) {
		this.previous = previous;
		this.multiple = multiple;
		this.directory = directory;
		this.allowDirectories = allowDirectories;
		this.extensions = List.copyOf(extensions);
		this.ready = ready;
		this.receive = receive;
		this.failed = failed;
	}

	@Override
	public boolean canImport(final TransferSupport support) {
		if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
			return previous != null && previous.canImport(support);
		if (!support.getComponent().isEnabled() || !ready.getAsBoolean())
			return false;
		if (support.isDrop()) {
			if ((support.getSourceDropActions() & COPY) == 0)
				return false;
			support.setDropAction(COPY);
		}
		return true;
	}

	@Override
	public boolean importData(final TransferSupport support) {
		if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
			return previous != null && previous.importData(support);
		if (!canImport(support))
			return false;
		try {
			final Object value = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
			if (!(value instanceof List<?> entries) || entries.isEmpty())
				throw new IllegalArgumentException("Drop a local file or folder.");
			if (!multiple && entries.size() != 1)
				throw new IllegalArgumentException(
						"Drop exactly one " + (directory ? "folder." : allowDirectories ? "file or folder." : "file."));
			final var paths = new java.util.ArrayList<Path>();
			for (final var entry : entries) {
				if (!(entry instanceof File file))
					throw new IllegalArgumentException("Drop local files from your file manager.");
				final Path path = file.toPath().toAbsolutePath().normalize();
				final boolean folder = Files.isDirectory(path);
				if (directory ? !folder : !Files.isRegularFile(path) && !(allowDirectories && folder))
					throw new IllegalArgumentException("Expected a "
							+ (directory ? "folder: " : allowDirectories ? "file or folder: " : "file: ") + path);
				if (!directory && !folder && !extensions.isEmpty() && extensions.stream().noneMatch(
						extension -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extension)))
					throw new IllegalArgumentException("Expected " + String.join(" / ", extensions) + " file: " + path);
				paths.add(path);
			}
			return receive.test(List.copyOf(paths));
		} catch (final Exception failure) {
			failed.accept(failure);
			return false;
		}
	}

	@Override
	public boolean importData(final JComponent component, final Transferable transferable) {
		return importData(new TransferSupport(component, transferable));
	}

	@Override
	public boolean canImport(final JComponent component, final DataFlavor[] flavors) {
		if (java.util.Arrays.asList(flavors).contains(DataFlavor.javaFileListFlavor))
			return component.isEnabled() && ready.getAsBoolean();
		return previous != null && previous.canImport(component, flavors);
	}

	@Override
	public int getSourceActions(final JComponent component) {
		return previous == null ? NONE : previous.getSourceActions(component);
	}

	@Override
	public void exportToClipboard(final JComponent component, final Clipboard clipboard, final int action) {
		if (previous != null)
			previous.exportToClipboard(component, clipboard, action);
	}

	@Override
	public void exportAsDrag(final JComponent component, final InputEvent event, final int action) {
		if (previous != null)
			previous.exportAsDrag(component, event, action);
	}
}
