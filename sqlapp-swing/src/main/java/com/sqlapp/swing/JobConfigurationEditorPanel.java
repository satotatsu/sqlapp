/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot;

/**
 * Offline text editing; applying a saved job only selects it for live
 * inspection.
 */
final class JobConfigurationEditorPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final RSyntaxTextArea text = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_YAML, true);
	final RTextScrollPane editorScroll = CodeEditors.scroll(text);
	final JLabel position = new JLabel("Line 1, column 1");
	final EditorNavigationBar navigation = new EditorNavigationBar(text);
	final JButton fromSchema = new JButton("From Schema…");
	final JButton fresh = new JButton("New");
	final JButton open = new JButton("Open…");
	final JButton check = new JButton("Check syntax / types");
	final JButton save = new JButton("Save");
	final JButton saveAs = new JButton("Save as…");
	final JButton use = new JButton("Use saved job in data plan");
	final JLabel file = new JLabel("New job (unsaved)");
	final JLabel status = new JLabel("Open or enter job YAML. Live validation is performed in Data migration plan.");
	private final BulkMigrationJobConfigurationEditorIO io;
	private final Consumer<Snapshot> useJob;
	private Snapshot snapshot;
	private boolean busy;
	private long revision;
	private long validatedRevision = -1;

	JobConfigurationEditorPanel(final Consumer<Snapshot> useJob) {
		this(new BulkMigrationJobConfigurationEditorIO(), useJob);
	}

	JobConfigurationEditorPanel(final BulkMigrationJobConfigurationEditorIO io, final Consumer<Snapshot> useJob) {
		super(new BorderLayout(8, 8));
		this.io = io;
		this.useJob = useJob;
		setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		for (final var button : new JButton[] { fresh, open, fromSchema, check, save, saveAs, use })
			actions.add(button);
		final var top = new JPanel(new BorderLayout(4, 4));
		top.add(actions, BorderLayout.NORTH);
		top.add(navigation, BorderLayout.CENTER);
		top.add(file, BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);
		add(editorScroll, BorderLayout.CENTER);
		final var bottom = new JPanel(new BorderLayout(8, 0));
		bottom.add(status, BorderLayout.CENTER);
		bottom.add(position, BorderLayout.EAST);
		add(bottom, BorderLayout.SOUTH);
		text.addCaretListener(event -> updatePosition());
		text.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent event) {
				changed();
			}

			public void removeUpdate(final DocumentEvent event) {
				changed();
			}

			public void changedUpdate(final DocumentEvent event) {
				changed();
			}
		});
		fresh.addActionListener(event -> {
			if (canDiscardChanges()) {
				snapshot = null;
				navigation.history.resetWith(() -> text.setText(""));
				refresh();
			}
		});
		open.addActionListener(event -> {
			final var chooser = chooser();
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				requestOpen(chooser.getSelectedFile().toPath());
		});
		fromSchema.addActionListener(event -> openDraftBuilder());
		check.addActionListener(event -> checkSyntax());
		save.addActionListener(event -> {
			if (snapshot != null)
				saveTo(snapshot.file());
		});
		saveAs.addActionListener(event -> {
			final var chooser = chooser();
			chooser.setDialogTitle("Save job — relative paths use this folder; choose a new filename");
			chooser.setSelectedFile(new java.io.File("job.yaml"));
			if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
				saveTo(chooser.getSelectedFile().toPath());
		});
		use.addActionListener(event -> {
			if (!busy && snapshot != null && !isDirty() && validatedRevision == revision)
				useJob.accept(snapshot);
		});
		refresh();
	}

	boolean isBusy() {
		return busy;
	}

	boolean isDirty() {
		return snapshot == null ? !text.getText().isEmpty() : !snapshot.text().equals(text.getText());
	}

	boolean canDiscardChanges() {
		if (busy)
			return false;
		if (!isDirty())
			return true;
		final Object[] options = { "Discard edits", "Cancel" };
		return JOptionPane.showOptionDialog(this, "This job has unsaved edits.", "Unsaved job YAML",
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]) == 0;
	}

	private void changed() {
		revision++;
		validatedRevision = -1;
		refresh();
		if (!busy)
			status.setText("Edited. Check syntax and save, then inspect the live data plan.");
	}

	private JFileChooser chooser() {
		final var chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("Migration job YAML / JSON", "yaml", "yml", "json"));
		return chooser;
	}

	private void refresh() {
		fresh.setEnabled(!busy);
		fromSchema.setEnabled(!busy);
		navigation.setBusy(busy);
		open.setEnabled(!busy);
		check.setEnabled(!busy && !text.getText().isBlank());
		save.setEnabled(!busy && snapshot != null && isDirty());
		saveAs.setEnabled(!busy && !text.getText().isBlank());
		use.setEnabled(!busy && snapshot != null && !isDirty() && validatedRevision == revision);
		text.setEditable(!busy);
		file.setText((snapshot == null ? "New job (unsaved)" : snapshot.file().toString()) + (isDirty() ? " *" : ""));
		file.setToolTipText(file.getText());
	}

	private void openDraftBuilder() {
		if (busy || !canDiscardChanges())
			return;
		final long requested = revision;
		final var dialog = new javax.swing.JDialog(javax.swing.SwingUtilities.getWindowAncestor(this),
				"Create job from Schema XML", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		final var draft = new JobDraftPanel(yaml -> {
			if (requested != revision || busy) {
				status.setText("Editor changed while creating the draft. Open the builder again.");
				return;
			}
			acceptDraft(yaml);
			dialog.dispose();
		});
		dialog.setContentPane(draft);
		dialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				if (!draft.isBusy())
					dialog.dispose();
			}
		});
		dialog.setSize(1000, 620);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	void acceptDraft(final String yaml) {
		if (busy)
			return;
		snapshot = null;
		navigation.history.resetWith(() -> text.setText(yaml));
		text.setCaretPosition(0);
		validatedRevision = -1;
		refresh();
		status.setText("Unsaved Schema-based draft. Review targets and policies, save, then inspect the live plan.");
	}

	boolean requestOpen(final Path path) {
		if (busy || !canDiscardChanges())
			return false;
		loadFrom(path);
		return true;
	}

	void loadFrom(final Path path) {
		if (busy)
			return;
		final long requested = revision;
		busy = true;
		refresh();
		status.setText("Opening job YAML…");
		new SwingWorker<Snapshot, Void>() {
			protected Snapshot doInBackground() throws Exception {
				return io.read(path);
			}

			protected void done() {
				try {
					final var loaded = get();
					if (revision != requested) {
						status.setText("Inputs changed; open again to replace the buffer.");
						return;
					}
					snapshot = loaded;
					navigation.history.resetWith(() -> text.setText(loaded.text()));
					text.setCaretPosition(0);
					status.setText("Opened. Check syntax/types before selecting this job for live inspection.");
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failed(exception);
				} catch (final ExecutionException exception) {
					failed(exception.getCause());
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	void checkSyntax() {
		if (busy)
			return;
		final String content = text.getText();
		final long requested = revision;
		busy = true;
		refresh();
		status.setText("Checking YAML syntax and configuration types…");
		new SwingWorker<Void, Void>() {
			protected Void doInBackground() {
				io.validate(content);
				return null;
			}

			protected void done() {
				try {
					get();
					if (requested == revision) {
						validatedRevision = revision;
						status.setText(
								"Syntax/types valid. Schema, policies, fingerprints and live target still require plan inspection.");
					} else
						status.setText("Buffer changed; check again.");
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failed(exception);
				} catch (final ExecutionException exception) {
					failed(exception.getCause(), requested == revision);
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	void saveTo(final Path path) {
		if (busy)
			return;
		final String content = text.getText();
		final long requested = revision;
		final String expected = snapshot != null && snapshot.file().equals(path.toAbsolutePath().normalize())
				? snapshot.fingerprint()
				: null;
		busy = true;
		refresh();
		status.setText("Saving job YAML; checking for external changes…");
		new SwingWorker<Snapshot, Void>() {
			protected Snapshot doInBackground() throws Exception {
				return io.write(path, content, expected);
			}

			protected void done() {
				try {
					snapshot = get();
					if (requested == revision)
						validatedRevision = revision;
					status.setText(requested == revision
							? "Saved. Select the saved job and inspect its live plan before execution."
							: "Snapshot saved; buffer contains newer unsaved edits.");
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failed(exception);
				} catch (final ExecutionException exception) {
					failed(exception.getCause(), requested == revision);
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	private void updatePosition() {
		try {
			final int offset = text.getCaretPosition();
			final int line = text.getLineOfOffset(offset);
			position.setText("Line " + (line + 1) + ", column " + (offset - text.getLineStartOffset(line) + 1));
		} catch (final javax.swing.text.BadLocationException exception) {
			throw new IllegalStateException("Editor caret is outside its document.", exception);
		}
	}

	void moveToDiagnostic(final int line, final int column) {
		if (line <= 0)
			return;
		try {
			final int index = Math.min(line - 1, text.getLineCount() - 1);
			final int start = text.getLineStartOffset(index);
			int end = Math.min(text.getLineEndOffset(index), text.getDocument().getLength());
			while (end > start && "\r\n".contains(text.getText(end - 1, 1)))
				end--;
			final int offset = start + Math.min(Math.max(column - 1, 0), end - start);
			CodeEditors.reveal(text, offset);
			text.setCaretPosition(offset);
			text.requestFocusInWindow();
		} catch (final javax.swing.text.BadLocationException exception) {
			throw new IllegalStateException("Diagnostic cannot be positioned in the editor.", exception);
		}
	}

	private void failed(final Throwable failure) {
		failed(failure, false);
	}

	private void failed(final Throwable failure, final boolean currentBuffer) {
		status.setText("Operation failed: " + failure.getMessage());
		status.setToolTipText(status.getText());
		if (currentBuffer && failure instanceof BulkMigrationJobConfigurationEditorIO.ValidationException diagnostic) {
			moveToDiagnostic(diagnostic.getLine(), diagnostic.getColumn());
		}
	}
}
