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
 * Full, selectable status text without changing the editor or invoking file
 * operations.
 */
final class EditorStatusPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JButton details = new JButton("Details…");

	EditorStatusPanel(final JLabel status) {
		this(status, null);
	}

	EditorStatusPanel(final JLabel status, final Consumer<String> presenter) {
		super(new BorderLayout(8, 0));
		Objects.requireNonNull(status, "status");
		add(status, BorderLayout.CENTER);
		add(details, BorderLayout.EAST);
		details.setToolTipText("Read and copy the complete current status message.");
		details.addActionListener(event -> {
			final String message = status.getText();
			if (message == null || message.isBlank())
				return;
			if (presenter != null)
				presenter.accept(message);
			else
				JOptionPane.showMessageDialog(this, new JScrollPane(messageArea(message)), "Editor status details",
						JOptionPane.INFORMATION_MESSAGE);
		});
		status.addPropertyChangeListener("text", event -> refresh(status));
		refresh(status);
	}

	private void refresh(final JLabel status) {
		final String message = status.getText();
		status.setToolTipText(message);
		details.setEnabled(message != null && !message.isBlank());
	}

	static JTextArea messageArea(final String message) {
		final var text = new JTextArea(message, 14, 72);
		text.setEditable(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setCaretPosition(0);
		text.getAccessibleContext().setAccessibleName("Complete editor status message");
		return text;
	}

	static String failureMessage(final Throwable failure) {
		final String message = failure.getMessage();
		return "Operation failed: "
				+ (message == null || message.isBlank() ? failure.getClass().getSimpleName() : message);
	}
}
