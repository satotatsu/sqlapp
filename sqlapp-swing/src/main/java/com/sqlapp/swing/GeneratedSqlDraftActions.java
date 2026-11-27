/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.*;
import javax.swing.*;

/** Offline save/edit handoff for an immutable displayed SQL draft. */
final class GeneratedSqlDraftActions extends JPanel {
	private static final long serialVersionUID = 1L;
	final JButton save = new JButton("Save SQL draft…");
	final JButton edit = new JButton("Edit saved SQL…");
	private final BooleanSupplier busy;
	private final Consumer<Boolean> setBusy;
	private final Consumer<String> status;
	private final Consumer<Throwable> failure;
	private final EditorFileChooser files = new EditorFileChooser();
	private final Saver writer;
	private String draft;
	private List<Path> inputs = List.of();
	private Path saved;
	private long revision;
	private Predicate<Path> editor;

	@FunctionalInterface
	interface Saver {
		void write(Path file, String text, List<Path> inputs) throws Exception;
	}

	GeneratedSqlDraftActions(ReadOnlyCodePanel view, BooleanSupplier busy, Consumer<Boolean> setBusy,
			Consumer<String> status, Consumer<Throwable> failure) {
		this(view, busy, setBusy, status, failure,
				(file, text, inputs) -> new ReviewFileWriter().write(file, text, inputs));
	}

	GeneratedSqlDraftActions(ReadOnlyCodePanel view, BooleanSupplier busy, Consumer<Boolean> setBusy,
			Consumer<String> status, Consumer<Throwable> failure, Saver writer) {
		super(new FlowLayout(FlowLayout.LEFT, 4, 0));
		this.busy = busy;
		this.setBusy = setBusy;
		this.status = status;
		this.failure = failure;
		this.writer = writer;
		add(save);
		add(edit);
		final var footer = new JPanel(new BorderLayout(4, 4));
		footer.add(view.position, BorderLayout.NORTH);
		footer.add(this, BorderLayout.SOUTH);
		view.add(footer, BorderLayout.SOUTH);
		save.addActionListener(event -> chooseSave());
		edit.addActionListener(event -> {
			if (!busy.getAsBoolean() && saved != null && editor != null)
				status.accept(editor.test(saved)
						? "Editing saved SQL draft. Review and execute it separately; the transfer job is unchanged."
						: "SQL editor could not open the saved draft. Finish its current work and retry.");
		});
		refresh();
	}

	void show(String text, List<Path> protectedInputs) {
		revision++;
		draft = text == null || text.isBlank() ? null : text;
		inputs = List.copyOf(protectedInputs);
		saved = null;
		refresh();
	}

	void clear() {
		show(null, List.of());
	}

	void setEditor(Predicate<Path> receiver) {
		editor = receiver;
		refresh();
	}

	void refresh() {
		save.setEnabled(!busy.getAsBoolean() && draft != null);
		edit.setEnabled(!busy.getAsBoolean() && saved != null && editor != null);
	}

	private void chooseSave() {
		if (busy.getAsBoolean() || draft == null)
			return;
		setBusy.accept(true);
		Path selected = null;
		try {
			final var chooser = files.forOutput(
					new javax.swing.filechooser.FileNameExtensionFilter("Schema SQL draft", "sql"),
					SchemaComparisonService.defaultSqlDraftName(),
					inputs.isEmpty() ? "" : inputs.getFirst().toString());
			if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				final var file = chooser.getSelectedFile().toPath();
				if (!Files.exists(file) || JOptionPane.showConfirmDialog(this, "Replace " + file + "?",
						"Replace SQL draft", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
					selected = file;
			}
		} finally {
			setBusy.accept(false);
		}
		if (selected != null)
			saveTo(selected);
	}

	void saveTo(Path destination) {
		if (busy.getAsBoolean() || draft == null)
			return;
		if (destination == null || destination.getFileName() == null
				|| !destination.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".sql")) {
			status.accept("Choose a .sql file for the draft.");
			return;
		}
		final String text = draft;
		final var protectedInputs = inputs;
		final long requested = revision;
		setBusy.accept(true);
		refresh();
		new SwingWorker<Void, Void>() {
			protected Void doInBackground() throws Exception {
				writer.write(destination, text, protectedInputs);
				return null;
			}

			protected void done() {
				try {
					get();
					files.remember(destination);
					if (requested == revision) {
						saved = destination.toAbsolutePath().normalize();
						status.accept("Saved SQL draft. Review before use; saving does not execute or authorize it: "
								+ saved);
					} else
						status.accept(
								"Saved the previous SQL snapshot. Review the current inputs again; editor handoff was not enabled.");
				} catch (Exception error) {
					if (error instanceof InterruptedException)
						Thread.currentThread().interrupt();
					status.accept("SQL draft save failed. The reviewed transfer is retained; check Details and retry.");
					failure.accept(error instanceof java.util.concurrent.ExecutionException ? error.getCause() : error);
				} finally {
					setBusy.accept(false);
					refresh();
				}
			}
		}.execute();
	}
}
