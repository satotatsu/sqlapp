/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
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
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
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
	final JTextArea preview = new JTextArea();
	private final JLabel status = new JLabel("Select before and after Schema XML. No database connection is used.");
	private final JButton beforeBrowse = new JButton("Browse…");
	private final JButton afterBrowse = new JButton("Browse…");

	SchemaComparisonPanel() {
		super(new BorderLayout(8, 8));
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var inputs = new JPanel(new GridLayout(2, 1, 4, 4));
		inputs.add(fileRow("Before Schema XML", original, beforeBrowse));
		inputs.add(fileRow("After Schema XML", target, afterBrowse));
		add(inputs, BorderLayout.NORTH);
		preview.setEditable(false);
		preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		add(new JScrollPane(preview), BorderLayout.CENTER);
		final var footer = new JPanel(new BorderLayout());
		final var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(compare);
		actions.add(save);
		footer.add(status, BorderLayout.CENTER);
		footer.add(actions, BorderLayout.EAST);
		add(footer, BorderLayout.SOUTH);
		save.setEnabled(false);
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
		save.addActionListener(event -> save());
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
		preview.setText("");
		save.setEnabled(false);
		status.setText("Inputs changed. Compare to generate a new review.");
	}

	private void busy(final boolean busy) {
		original.setEnabled(!busy);
		target.setEnabled(!busy);
		beforeBrowse.setEnabled(!busy);
		afterBrowse.setEnabled(!busy);
		compare.setEnabled(!busy);
		save.setEnabled(!busy && !preview.getText().isEmpty());
	}

	private void compare() {
		final String before = original.getText().trim();
		final String after = target.getText().trim();
		invalidatePreview();
		busy(true);
		status.setText("Comparing Schemas…");
		new SwingWorker<String, Void>() {
			protected String doInBackground() throws Exception {
				if (before.isEmpty() || after.isEmpty()) {
					throw new IllegalArgumentException("Select both before and after Schema XML files.");
				}
				return new SchemaComparisonService().compare(Path.of(before), Path.of(after));
			}

			protected void done() {
				try {
					preview.setText(get());
					preview.setCaretPosition(0);
					status.setText("Review generated. SQL has not been executed.");
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

	private void save() {
		final var chooser = new JFileChooser();
		chooser.setSelectedFile(new java.io.File("schema-review.txt"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		final Path destination = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		if (destination.equals(Path.of(original.getText().trim()).toAbsolutePath().normalize())
				|| destination.equals(Path.of(target.getText().trim()).toAbsolutePath().normalize())) {
			failure(new IllegalArgumentException("Choose a destination different from the input Schema files."));
			return;
		}
		if (Files.exists(destination) && JOptionPane.showConfirmDialog(this, "Replace " + destination + "?",
				"Replace review", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		final String text = preview.getText();
		final Path before = Path.of(original.getText().trim());
		final Path after = Path.of(target.getText().trim());
		busy(true);
		new SwingWorker<Void, Void>() {
			protected Void doInBackground() throws Exception {
				new ReviewFileWriter().write(destination, text, before, after);
				return null;
			}

			protected void done() {
				try {
					get();
					status.setText("Saved: " + destination);
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
		status.setText("Operation failed.");
		JOptionPane.showMessageDialog(this, failure.toString(), "Migration review failed", JOptionPane.ERROR_MESSAGE);
	}
}
