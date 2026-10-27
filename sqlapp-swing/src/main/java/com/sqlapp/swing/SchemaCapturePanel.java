/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Explicit metadata capture with the shared connection helpers. */
final class SchemaCapturePanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final ConnectionFields connection = new ConnectionFields("Database connection");
	final JTextField schema = new JTextField();
	final JButton chooseSchema = new JButton("Choose schema…");
	final JButton capture = new JButton("Read and use Schema XML");
	final JButton cancel = new JButton("Cancel");
	final JToggleButton driverOptions = new JToggleButton("Driver class");
	final JLabel status = new JLabel("Read metadata only. No table rows are read and no migration SQL is executed.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	private final Path parent;
	private final Capturer capturer;
	private final java.util.function.BiFunction<PlanningJdbc.Spec, char[], String> tester;
	private final Consumer<Path> ready;
	private boolean busy;
	private long revision;

	enum Action {
		CAPTURE, TEST, SCHEMAS
	}

	@FunctionalInterface
	interface Capturer {
		Path capture(PlanningJdbc.Spec spec, char[] secret, String schema, Path parent);
	}

	private record Result(Path file, String product, List<String> schemas) {
	}

	SchemaCapturePanel(Path parent, Consumer<Path> ready, Runnable cancelled) {
		this(parent, ready, cancelled, new SchemaCaptureService()::capture, new JdbcConnectionTestService()::test);
	}

	SchemaCapturePanel(Path parent, Consumer<Path> ready, Runnable cancelled, Capturer capturer,
			java.util.function.BiFunction<PlanningJdbc.Spec, char[], String> tester) {
		super(new BorderLayout(8, 8));
		this.parent = parent;
		this.ready = ready;
		this.capturer = capturer;
		this.tester = tester;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		add(connection, BorderLayout.CENTER);
		final var schemaRow = new JPanel(new BorderLayout(4, 0));
		schemaRow.add(schema);
		schemaRow.add(chooseSchema, BorderLayout.EAST);
		connection.row(6, new JLabel("Schema (blank: current)"), schemaRow);
		final var destination = new JTextField(parent.toAbsolutePath().normalize().toString());
		destination.setEditable(false);
		connection.row(7, new JLabel("New schema-* folder in"), destination);
		driverOptions.addActionListener(event -> {
			connection.showDriver(driverOptions.isSelected());
			connection.revalidate();
		});
		final var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(driverOptions);
		actions.add(capture);
		actions.add(cancel);
		final var bottom = new JPanel(new BorderLayout(4, 4));
		bottom.add(statusPanel, BorderLayout.NORTH);
		bottom.add(actions, BorderLayout.SOUTH);
		add(bottom, BorderLayout.SOUTH);
		final var changed = new DocumentListener() {
			public void insertUpdate(DocumentEvent event) {
				revision++;
			}

			public void removeUpdate(DocumentEvent event) {
				revision++;
			}

			public void changedUpdate(DocumentEvent event) {
				revision++;
			}
		};
		for (var field : new JTextField[] { connection.jdbcUrl, connection.username, connection.password,
				connection.driverClass, schema })
			field.getDocument().addDocumentListener(changed);
		final var identityChanged = new DocumentListener() {
			private void reset() {
				if (!schema.getText().isEmpty())
					schema.setText("");
			}

			public void insertUpdate(DocumentEvent event) {
				reset();
			}

			public void removeUpdate(DocumentEvent event) {
				reset();
			}

			public void changedUpdate(DocumentEvent event) {
				reset();
			}
		};
		for (var field : new JTextField[] { connection.jdbcUrl, connection.username, connection.driverClass })
			field.getDocument().addDocumentListener(identityChanged);
		capture.addActionListener(event -> run(Action.CAPTURE));
		connection.test.addActionListener(event -> run(Action.TEST));
		chooseSchema.addActionListener(event -> run(Action.SCHEMAS));
		connection.buildUrl.addActionListener(event -> {
			if (isBusy())
				return;
			final long requested = revision;
			busy(true);
			try {
				final String url = JdbcUrlBuilderPanel.showDialog(this, connection.jdbcUrl.getText(), "Schema capture");
				if (url != null && requested == revision)
					connection.jdbcUrl.setText(url);
			} finally {
				busy(false);
			}
		});
		final Runnable close = () -> {
			if (!isBusy()) {
				connection.password.setText("");
				cancelled.run();
			}
		};
		cancel.addActionListener(event -> close.run());
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
		getActionMap().put("cancel", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				close.run();
			}
		});
	}

	boolean isBusy() {
		return busy || connection.profile.isBusy();
	}

	private void busy(boolean value) {
		busy = value;
		enable(connection, !value);
		capture.setEnabled(!value);
		cancel.setEnabled(!value);
		driverOptions.setEnabled(!value);
	}

	private static void enable(Container container, boolean enabled) {
		for (var child : container.getComponents()) {
			child.setEnabled(enabled);
			if (child instanceof Container nested)
				enable(nested, enabled);
		}
	}

	void run(Action action) {
		if (isBusy())
			return;
		final PlanningJdbc.Spec spec;
		try {
			spec = connection.snapshot().spec();
		} catch (IllegalArgumentException failure) {
			status.setText("Enter a JDBC URL starting with jdbc: first.");
			connection.jdbcUrl.requestFocusInWindow();
			return;
		}
		final char[] secret = connection.password.getPassword();
		final String selectedSchema = schema.getText();
		final long requested = revision;
		busy(true);
		status.setText("Reading database metadata. No table rows or migration SQL execution.");
		new SwingWorker<Result, Void>() {
			@Override
			protected Result doInBackground() {
				try {
					return switch (action) {
					case CAPTURE -> new Result(capturer.capture(spec, secret, selectedSchema, parent), null, null);
					case TEST -> new Result(null, tester.apply(spec, secret), null);
					case SCHEMAS -> new Result(null, null, new SourceSchemaDiscoveryService().list(spec, secret));
					};
				} finally {
					Arrays.fill(secret, '\0');
				}
			}

			@Override
			protected void done() {
				try {
					final var result = get();
					if (requested != revision) {
						status.setText("Inputs changed. Start again."
								+ (result.file() == null ? "" : " Captured XML: " + result.file()));
						return;
					}
					if (action == Action.CAPTURE) {
						connection.password.setText("");
						ready.accept(result.file());
					} else if (action == Action.TEST)
						status.setText("Connection succeeded: " + result.product());
					else {
						final String choice = SourceSchemaSelectionPanel.showDialog(SchemaCapturePanel.this,
								result.schemas(), schema.getText());
						if (requested != revision)
							status.setText("Inputs changed. Choose the Schema again.");
						else if (choice == null)
							status.setText("Schema selection cancelled. Current setting retained.");
						else {
							schema.setText(choice);
							status.setText("Schema selected. Read and use Schema XML when ready.");
						}
					}
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					failed(failure);
				} catch (ExecutionException failure) {
					failed(failure.getCause());
				} finally {
					busy(false);
				}
			}

			private void failed(Throwable failure) {
				if (requested != revision) {
					status.setText("Inputs changed. Start again.");
					return;
				}
				status.setText("Operation failed. Check connection and Schema permissions; see Details.");
				statusPanel.setDetails(status.getText() + "\n" + EditorStatusPanel.failureDetails(failure));
			}
		}.execute();
	}
}
