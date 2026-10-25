/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

final class SchemaComparisonPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField original = new JTextField();
	final JTextField target = new JTextField();
	final JButton compare = new JButton("Compare");
	final JButton save = new JButton("Save review…");
	final RSyntaxTextArea preview = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_SQL, false);
	final RSyntaxTextArea sqlPreview = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_SQL, false);
	final JButton saveSql = new JButton("Save SQL draft…");
	final JButton useDirectory = new JButton("Use saved SQL directory");
	private Path savedSql;
	private java.util.function.Predicate<Path> directoryReceiver;
	private boolean busy;
	final javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
	private SchemaComparisonService.Result result;
	private long revision;
	private final Comparer comparer;
	private final Saver saver;

	@FunctionalInterface
	interface Comparer {
		SchemaComparisonService.Result inspect(Path original, Path target) throws Exception;
	}

	@FunctionalInterface
	interface Saver {
		void save(SchemaComparisonService.Result result, Path destination, boolean sqlDraft) throws Exception;
	}

	final JLabel status = new JLabel("Select before and after Schema XML. No database connection is used.");
	private final JButton beforeBrowse = new JButton("Browse…");
	private final JButton afterBrowse = new JButton("Browse…");

	SchemaComparisonPanel() {
		this(new SchemaComparisonService()::inspect, new SchemaComparisonService()::save);
	}

	SchemaComparisonPanel(final Comparer comparer, final Saver saver) {
		super(new BorderLayout(8, 8));
		this.comparer = java.util.Objects.requireNonNull(comparer, "comparer");
		this.saver = java.util.Objects.requireNonNull(saver, "saver");
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var inputs = new JPanel(new GridLayout(2, 1, 4, 4));
		inputs.add(fileRow("Before Schema XML", original, beforeBrowse));
		inputs.add(fileRow("After Schema XML", target, afterBrowse));
		add(inputs, BorderLayout.NORTH);
		preview.setEditable(false);
		sqlPreview.setEditable(false);
		tabs.addTab("Review", CodeEditors.scroll(preview));
		tabs.addTab("SQL draft", CodeEditors.scroll(sqlPreview));
		add(tabs, BorderLayout.CENTER);
		final var footer = new JPanel(new BorderLayout());
		final var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(compare);
		actions.add(save);
		actions.add(saveSql);
		actions.add(useDirectory);
		footer.add(status, BorderLayout.NORTH);
		footer.add(actions, BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
		save.setEnabled(false);
		saveSql.setEnabled(false);
		useDirectory.setEnabled(false);
		useDirectory.addActionListener(event -> useSavedDirectory());
		final DocumentListener changed = new DocumentListener() {
			public void insertUpdate(final DocumentEvent event) {
				invalidatePreview();
			}

			public void removeUpdate(final DocumentEvent event) {
				invalidatePreview();
			}

			public void changedUpdate(final DocumentEvent event) {
				invalidatePreview();
			}
		};
		original.getDocument().addDocumentListener(changed);
		target.getDocument().addDocumentListener(changed);
		compare.addActionListener(event -> compare());
		save.addActionListener(event -> chooseSave(false));
		saveSql.addActionListener(event -> chooseSave(true));
	}

	private JPanel fileRow(final String label, final JTextField field, final JButton browse) {
		final var row = new JPanel(new BorderLayout(8, 0));
		row.add(new JLabel(label), BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		row.add(browse, BorderLayout.EAST);
		browse.addActionListener(event -> {
			final var chooser = new JFileChooser();
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				field.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		});
		return row;
	}

	private void invalidatePreview() {
		revision++;
		result = null;
		savedSql = null;
		useDirectory.setEnabled(false);
		preview.setText("");
		sqlPreview.setText("");
		save.setEnabled(false);
		saveSql.setEnabled(false);
		status.setText("Inputs changed. Compare to generate a new review.");
	}

	private void busy(final boolean busy) {
		this.busy = busy;
		original.setEnabled(!busy);
		target.setEnabled(!busy);
		beforeBrowse.setEnabled(!busy);
		afterBrowse.setEnabled(!busy);
		compare.setEnabled(!busy);
		save.setEnabled(!busy && result != null);
		saveSql.setEnabled(!busy && result != null && result.statements() > 0);
		useDirectory.setEnabled(!busy && savedSql != null && result != null && directoryReceiver != null);
	}

	private void compare() {
		final String before = original.getText().trim();
		final String after = target.getText().trim();
		invalidatePreview();
		final long requestedRevision = revision;
		busy(true);
		status.setText("Comparing Schemas…");
		new SwingWorker<SchemaComparisonService.Result, Void>() {
			protected SchemaComparisonService.Result doInBackground() throws Exception {
				if (before.isEmpty() || after.isEmpty()) {
					throw new IllegalArgumentException("Select both before and after Schema XML files.");
				}
				return comparer.inspect(Path.of(before), Path.of(after));
			}

			protected void done() {
				try {
					if (revision != requestedRevision)
						return;
					final var compared = get();
					result = compared;
					preview.setText(result.review());
					sqlPreview.setText(result.sqlDraft());
					sqlPreview.setCaretPosition(0);
					preview.setCaretPosition(0);
					status.setText("Review generated: " + result.statements()
							+ " SQL statements. Draft requires review; no SQL executed.");
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

	private void chooseSave(final boolean sqlDraft) {
		if (result == null)
			return;
		final var chooser = new JFileChooser();
		chooser.setSelectedFile(
				new java.io.File(sqlDraft ? SchemaComparisonService.defaultSqlDraftName() : "schema-review.txt"));
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
				sqlDraft ? "Migration SQL draft" : "Schema review", sqlDraft ? "sql" : "txt"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		final Path destination = chooser.getSelectedFile().toPath();
		if (Files.exists(destination) && JOptionPane.showConfirmDialog(this, "Replace " + destination + "?",
				"Replace artifact", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			return;
		saveTo(destination, sqlDraft);
	}

	void saveTo(final Path destination, final boolean sqlDraft) {
		if (result == null || !(sqlDraft ? saveSql : save).isEnabled())
			return;
		final var snapshot = result;
		final long requestedRevision = revision;
		busy(true);
		new SwingWorker<Void, Void>() {
			protected Void doInBackground() throws Exception {
				saver.save(snapshot, destination, sqlDraft);
				return null;
			}

			protected void done() {
				try {
					get();
					if (sqlDraft && revision == requestedRevision)
						savedSql = destination.toAbsolutePath().normalize();
					status.setText(
							"Saved " + (sqlDraft ? "SQL draft (review before use): " : "review: ") + destination);
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

	boolean isBusy() {
		return busy;
	}

	void setDirectoryReceiver(final java.util.function.Predicate<Path> receiver) {
		directoryReceiver = receiver;
		busy(busy);
	}

	void useSavedDirectory() {
		if (!useDirectory.isEnabled())
			return;
		final var snapshot = result;
		final Path saved = savedSql;
		final long requestedRevision = revision;
		busy(true);
		status.setText("Checking saved draft and versioned SQL directory…");
		new SwingWorker<Path, Void>() {
			protected Path doInBackground() throws Exception {
				return new SchemaComparisonService().migrationDirectory(snapshot, saved);
			}

			protected void done() {
				try {
					if (revision != requestedRevision)
						return;
					final Path directory = get();
					if (directoryReceiver.test(directory))
						status.setText(
								"Selected Up SQL directory. Review all files there and build a new connected plan.");
					else
						status.setText(
								"Migration plan screen is busy. Saved draft retained; try again after it finishes.");
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
		status.setText("Operation failed: " + failure);
		status.setToolTipText(status.getText());
	}
}
