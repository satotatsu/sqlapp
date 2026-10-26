/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Full, selectable status diagnostics shared by migration screens, without
 * invoking file or database operations.
 */
final class EditorStatusPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JButton details = new JButton("Details…");
	private final JLabel status;
	private String fullDetails;

	EditorStatusPanel(final JLabel status) {
		this(status, null);
	}

	EditorStatusPanel(final JLabel status, final Consumer<String> presenter) {
		super(new BorderLayout(8, 0));
		Objects.requireNonNull(status, "status");
		this.status = status;
		add(status, BorderLayout.CENTER);
		add(details, BorderLayout.EAST);
		details.setToolTipText("Read and copy the complete current status message.");
		details.addActionListener(event -> {
			final String message = detailsText();
			if (message == null || message.isBlank())
				return;
			if (presenter != null)
				presenter.accept(message);
			else
				JOptionPane.showMessageDialog(this, new JScrollPane(messageArea(message)), "Status details",
						JOptionPane.INFORMATION_MESSAGE);
		});
		status.addPropertyChangeListener("text", event -> {
			fullDetails = null;
			refresh(status);
		});
		refresh(status);
	}

	private void refresh(final JLabel status) {
		final String message = status.getText();
		status.setToolTipText(message);
		final String complete = detailsText();
		details.setEnabled(complete != null && !complete.isBlank());
	}

	void setDetails(final String text) {
		fullDetails = text == null || text.isBlank() ? null : text;
		refresh(status);
	}

	String detailsText() {
		return fullDetails == null ? status.getText() : fullDetails;
	}

	static JTextArea messageArea(final String message) {
		final var text = new JTextArea(message, 14, 72);
		text.setEditable(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setCaretPosition(0);
		text.getAccessibleContext().setAccessibleName("Complete status message");
		return text;
	}

	static String failureMessage(final Throwable failure) {
		final String message = failure.getMessage();
		return "Operation failed: "
				+ (message == null || message.isBlank() ? failure.getClass().getSimpleName() : message);
	}

	static String failureDetails(final Throwable failure) {
		final var text = new StringBuilder();
		final var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
		final var pending = new java.util.ArrayDeque<FailureEntry>();
		if (failure != null)
			pending.push(new FailureEntry("", failure));
		while (!pending.isEmpty()) {
			final var entry = pending.pop();
			final var current = entry.failure();
			if (!seen.add(current)) {
				text.append("\n").append(entry.label().equals("Caused by: ") ? "Cause cycle detected."
						: entry.label() + "Exception already reported (shared reference or cycle).");
				continue;
			}
			if (!text.isEmpty())
				text.append('\n');
			text.append(entry.label()).append(current);
			if (current instanceof java.sql.SQLException sql) {
				text.append("\nSQLState: ").append(sql.getSQLState() == null ? "not provided" : sql.getSQLState())
						.append("; vendor code: ").append(sql.getErrorCode());
				if (sql.getNextException() != null)
					pending.push(new FailureEntry("Next SQL exception: ", sql.getNextException()));
			}
			final var suppressed = current.getSuppressed();
			for (int index = suppressed.length - 1; index >= 0; index--)
				pending.push(new FailureEntry("Suppressed: ", suppressed[index]));
			if (current.getCause() != null)
				pending.push(new FailureEntry("Caused by: ", current.getCause()));
		}
		return text.toString();
	}

	private record FailureEntry(String label, Throwable failure) {
	}
}
