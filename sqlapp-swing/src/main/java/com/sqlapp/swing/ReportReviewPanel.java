/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Read-only view of saved artifacts; all IO and validation run off the EDT. */
final class ReportReviewPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField file = new JTextField();
	final JComboBox<ReportReviewService.Type> type = new JComboBox<>(ReportReviewService.Type.values());
	final JButton load = new JButton("Load report");
	private final ReportReviewView view = new ReportReviewView();
	final JTextArea summary = view.summary;
	final JTextArea details = view.details;
	final JTable tasks = view.tasks;
	final JLabel status = new JLabel("Saved artifacts only. The current database state is not checked.");
	private final JButton browse = new JButton("Browse…");
	private final Loader loader;
	private long revision;

	@FunctionalInterface
	interface Loader {
		ReportReviewService.Review read(Path file, ReportReviewService.Type type);
	}

	ReportReviewPanel() {
		this(new ReportReviewService()::read);
	}

	ReportReviewPanel(final Loader loader) {
		super(new BorderLayout(8, 8));
		this.loader = java.util.Objects.requireNonNull(loader, "loader");
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var input = new JPanel(new BorderLayout(8, 8));
		final var format = new JPanel(new FlowLayout(FlowLayout.LEFT));
		format.add(new JLabel("Report type"));
		format.add(type);
		format.add(load);
		input.add(format, BorderLayout.NORTH);
		input.add(new JLabel("Report JSON"), BorderLayout.WEST);
		input.add(file, BorderLayout.CENTER);
		input.add(browse, BorderLayout.EAST);
		add(input, BorderLayout.NORTH);
		add(view, BorderLayout.CENTER);
		add(status, BorderLayout.SOUTH);
		file.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent event) {
				clear();
			}

			public void removeUpdate(final DocumentEvent event) {
				clear();
			}

			public void changedUpdate(final DocumentEvent event) {
				clear();
			}
		});
		type.addActionListener(event -> clear());
		browse.addActionListener(event -> {
			final var chooser = new JFileChooser();
			chooser.setFileFilter(new FileNameExtensionFilter("Migration report JSON", "json"));
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				file.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		});
		load.addActionListener(event -> load());
		clear();
	}

	private void clear() {
		revision++;
		view.clear();
		status.setText("Select a report type and JSON file. Current database state is not checked.");
	}

	private void busy(final boolean busy) {
		file.setEnabled(!busy);
		type.setEnabled(!busy);
		browse.setEnabled(!busy);
		load.setEnabled(!busy);
	}

	private void load() {
		final String path = file.getText().trim();
		final var selectedType = (ReportReviewService.Type) type.getSelectedItem();
		clear();
		final long requestedRevision = revision;
		busy(true);
		status.setText("Reading and validating saved report…");
		new SwingWorker<ReportReviewService.Review, Void>() {
			@Override
			protected ReportReviewService.Review doInBackground() {
				if (path.isEmpty()) {
					throw new IllegalArgumentException("Select a report JSON file.");
				}
				return loader.read(Path.of(path), selectedType);
			}

			@Override
			protected void done() {
				try {
					if (revision != requestedRevision) {
						return;
					}
					final var review = get();
					view.showReview(review);
					status.setText("Loaded saved report. No live checks, approval or execution performed.");
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failure(exception);
				} catch (final ExecutionException exception) {
					failure(exception.getCause());
				} finally {
					busy(false);
				}
			}
		}.execute();
	}

	private void failure(final Throwable failure) {
		summary.setText("Unable to load the selected report.\n" + failure
				+ (failure.getCause() == null ? "" : "\nCause: " + failure.getCause()));
		status.setText("Load failed. Check the report type, format and file.");
	}
}
