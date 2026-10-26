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
	final JButton detect = new JButton("Detect and load");
	final javax.swing.JToggleButton chooseFormat = new javax.swing.JToggleButton("Choose format");
	final JComboBox<RecentReport> recent = new JComboBox<>();
	final JButton reopen = new JButton("Open recent");
	final JButton clearRecent = new JButton("Clear history");
	final JPanel recentRow = new JPanel(new BorderLayout(8, 0));
	private final java.util.List<RecentReport> history = new java.util.ArrayList<>();

	record RecentReport(Path file, ReportReviewService.Type type, boolean automatic) {
		RecentReport {
			file = java.util.Objects.requireNonNull(file).toAbsolutePath().normalize();
			java.util.Objects.requireNonNull(type);
		}

		@Override
		public String toString() {
			return file + " — " + type + (automatic ? " (auto)" : "");
		}
	}

	private final ReportReviewView view = new ReportReviewView();
	final JTextArea summary = view.summary;
	final JTextArea details = view.details;
	final JTable tasks = view.tasks;
	final JLabel status = new JLabel("Saved artifacts only. The current database state is not checked.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	private final JButton browse = new JButton("Browse…");
	private final Loader loader;
	private final AutoLoader autoLoader;
	private boolean assigningType;
	private long revision;
	private boolean busy;
	private final EditorFileChooser fileDialogs = new EditorFileChooser();

	@FunctionalInterface
	interface Loader {
		ReportReviewService.Review read(Path file, ReportReviewService.Type type);
	}

	@FunctionalInterface
	interface AutoLoader {
		ReportReviewAutoLoader.Detected read(Path file);
	}

	ReportReviewPanel() {
		this(new ReportReviewService()::read);
	}

	ReportReviewPanel(final Loader loader) {
		this(loader, new ReportReviewAutoLoader()::read);
	}

	ReportReviewPanel(final Loader loader, final AutoLoader autoLoader) {
		super(new BorderLayout(8, 8));
		this.loader = java.util.Objects.requireNonNull(loader, "loader");
		this.autoLoader = java.util.Objects.requireNonNull(autoLoader, "autoLoader");
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var input = new JPanel(new BorderLayout(8, 8));
		final var format = new JPanel(new FlowLayout(FlowLayout.LEFT));
		format.add(detect);
		format.add(chooseFormat);
		final var manual = new JPanel(new FlowLayout(FlowLayout.LEFT));
		manual.add(new JLabel("Report type"));
		manual.add(type);
		manual.add(load);
		manual.setVisible(false);
		chooseFormat.addActionListener(event -> {
			manual.setVisible(chooseFormat.isSelected());
			input.revalidate();
		});
		final var extra = new JPanel(new BorderLayout(4, 4));
		extra.add(manual, BorderLayout.NORTH);
		final var recentLabel = new JLabel("Recent reports");
		recentLabel.setLabelFor(recent);
		recentRow.add(recentLabel, BorderLayout.WEST);
		recentRow.add(recent, BorderLayout.CENTER);
		final var recentActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		recentActions.add(reopen);
		recentActions.add(clearRecent);
		recentRow.add(recentActions, BorderLayout.EAST);
		final var renderer = new javax.swing.DefaultListCellRenderer();
		renderer.putClientProperty("html.disable", Boolean.TRUE);
		recent.setRenderer(renderer);
		recent.addActionListener(event -> refreshRecentControls());
		reopen.addActionListener(event -> openRecent());
		clearRecent.addActionListener(event -> clearRecentReports());
		extra.add(recentRow, BorderLayout.SOUTH);
		input.add(extra, BorderLayout.SOUTH);
		input.add(format, BorderLayout.NORTH);
		input.add(new JLabel("Report JSON"), BorderLayout.WEST);
		input.add(file, BorderLayout.CENTER);
		FileDropHandler.path(file, false, java.util.List.of("json"), () -> !busy,
				FileDropHandler.errors(status, statusPanel));
		input.add(browse, BorderLayout.EAST);
		add(input, BorderLayout.NORTH);
		add(view, BorderLayout.CENTER);
		add(statusPanel, BorderLayout.SOUTH);
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
		type.addActionListener(event -> {
			if (!assigningType)
				clear();
		});
		browse.addActionListener(event -> {
			final var chooser = chooser();
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				file.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		});
		load.addActionListener(event -> load());
		detect.addActionListener(event -> load(true));
		refreshRecentControls();
		clear();
	}

	private void clear() {
		revision++;
		view.clear();
		status.setText(
				"Select a JSON file and Detect and load, or Choose format. Current database state is not checked.");
	}

	JFileChooser chooser() {
		return fileDialogs.forFile(new FileNameExtensionFilter("Migration report JSON", "json"), file.getText());
	}

	private void busy(final boolean busy) {
		this.busy = busy;
		file.setEnabled(!busy);
		type.setEnabled(!busy);
		browse.setEnabled(!busy);
		load.setEnabled(!busy);
		detect.setEnabled(!busy);
		chooseFormat.setEnabled(!busy);
		refreshRecentControls();
	}

	private void refreshRecentControls() {
		final boolean available = recent.getItemCount() > 0;
		recentRow.setVisible(available);
		recent.setEnabled(!busy && available);
		reopen.setEnabled(!busy && recent.getSelectedItem() != null);
		clearRecent.setEnabled(!busy && available);
		final var selected = (RecentReport) recent.getSelectedItem();
		recent.setToolTipText(selected == null ? null : "Report: " + selected);
	}

	private void rememberRecent(Path path, ReportReviewService.Type reportType, boolean automatic) {
		final var entry = new RecentReport(path, reportType, automatic);
		history.removeIf(previous -> previous.file().equals(entry.file()));
		history.addFirst(entry);
		if (history.size() > 10)
			history.removeLast();
		recent.removeAllItems();
		for (var item : history)
			recent.addItem(item);
		recent.setSelectedIndex(0);
		refreshRecentControls();
		recentRow.revalidate();
	}

	void clearRecentReports() {
		if (busy)
			return;
		history.clear();
		recent.removeAllItems();
		refreshRecentControls();
		recentRow.getParent().revalidate();
	}

	void openRecent() {
		if (busy)
			return;
		final var selected = (RecentReport) recent.getSelectedItem();
		if (selected == null)
			return;
		if (selected.automatic()) {
			file.setText(selected.file().toString());
			load(true);
		} else
			requestOpen(selected.file(), selected.type());
	}

	private void load() {
		load(false);
	}

	private void load(final boolean automatic) {
		if (busy)
			return;
		final String path = file.getText().trim();
		final var selectedType = (ReportReviewService.Type) type.getSelectedItem();
		clear();
		final long requestedRevision = revision;
		busy(true);
		status.setText("Reading and validating saved report…");
		new SwingWorker<ReportReviewAutoLoader.Detected, Void>() {
			@Override
			protected ReportReviewAutoLoader.Detected doInBackground() {
				if (path.isEmpty()) {
					throw new IllegalArgumentException("Select a report JSON file.");
				}
				return automatic ? autoLoader.read(Path.of(path))
						: new ReportReviewAutoLoader.Detected(selectedType, loader.read(Path.of(path), selectedType));
			}

			@Override
			protected void done() {
				try {
					if (revision != requestedRevision) {
						return;
					}
					final var loaded = get();
					final var review = loaded.review();
					assigningType = true;
					try {
						type.setSelectedItem(loaded.type());
					} finally {
						assigningType = false;
					}
					view.showReview(review);
					fileDialogs.remember(Path.of(path));
					rememberRecent(Path.of(path), loaded.type(), automatic);
					status.setText((automatic ? "Detected " + loaded.type() + ". " : "Loaded saved report. ")
							+ "No live checks, approval or execution performed.");
					statusPanel.setDetails(status.getText() + "\nFile: " + Path.of(path).toAbsolutePath().normalize()
							+ "\nReport type: " + loaded.type() + "\n\n" + review.summary());
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failure(exception, path, automatic ? null : selectedType);
				} catch (final ExecutionException exception) {
					failure(exception.getCause(), path, automatic ? null : selectedType);
				} finally {
					busy(false);
				}
			}
		}.execute();
	}

	boolean requestOpen(final Path selectedFile, final ReportReviewService.Type selectedType) {
		if (busy)
			return false;
		java.util.Objects.requireNonNull(selectedFile, "report file");
		java.util.Objects.requireNonNull(selectedType, "report type");
		file.setText(selectedFile.toAbsolutePath().normalize().toString());
		type.setSelectedItem(selectedType);
		load();
		return true;
	}

	private void failure(final Throwable failure, final String path, final ReportReviewService.Type selectedType) {
		summary.setText("Unable to load the selected report.\n" + EditorStatusPanel.failureDetails(failure));
		status.setText("Load failed. Check the report type, format and file.");
		statusPanel.setDetails(status.getText() + "\nFile: " + path + "\nReport type: "
				+ (selectedType == null ? "Automatic detection" : selectedType) + "\n\n" + summary.getText());
	}

	boolean isBusy() {
		return busy;
	}
}
