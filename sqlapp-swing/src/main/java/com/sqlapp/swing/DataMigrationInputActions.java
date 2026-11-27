/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import javax.swing.JFileChooser;
import javax.swing.SwingWorker;

/**
 * Connection and job preparation for {@link DataMigrationPlanPanel}: source
 * schema choice, JDBC URL builder, connection test and New migration. None of
 * these copy rows; inputs and status stay on the panel.
 */
final class DataMigrationInputActions {
	private final DataMigrationPlanPanel panel;
	private final DataMigrationPlanPanel.DraftDiscovery draftDiscovery;
	private final java.util.function.Function<DataMigrationDraftService.Prepared, com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot> draftDialog;
	private final DataMigrationPlanPanel.ConnectionTester connectionTester;
	private final EditorFileChooser workspaceDialogs = new EditorFileChooser();

	/**
	 * @param dialog New migration table selection; null shows the standard dialog
	 */
	DataMigrationInputActions(final DataMigrationPlanPanel panel, final DataMigrationPlanPanel.DraftDiscovery discovery,
			final java.util.function.Function<DataMigrationDraftService.Prepared, com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot> dialog,
			final DataMigrationPlanPanel.ConnectionTester tester) {
		this.panel = panel;
		this.draftDiscovery = discovery;
		this.draftDialog = dialog == null ? this::showNewMigration : dialog;
		this.connectionTester = tester;
	}

	void chooseSourceSchema(final DataMigrationPlanPanel.SchemaLister lister,
			final java.util.function.Function<java.util.List<String>, String> picker) {
		if (panel.isBusy())
			return;
		final PlanningJdbc.Spec connection;
		try {
			connection = panel.source.snapshot().spec();
		} catch (IllegalArgumentException failure) {
			panel.status.setText("Source connection: enter a JDBC URL starting with jdbc: before choosing a schema.");
			panel.source.jdbcUrl.requestFocusInWindow();
			return;
		}
		final char[] password = panel.source.password.getPassword();
		final long requested = panel.revision;
		panel.busy(true);
		panel.status.setText("Reading source schema names. No table rows are read or written.");
		new SwingWorker<java.util.List<String>, Void>() {
			@Override
			protected java.util.List<String> doInBackground() {
				try {
					return java.util.List.copyOf(lister.list(connection, password));
				} finally {
					Arrays.fill(password, '\0');
				}
			}

			@Override
			protected void done() {
				try {
					if (requested != panel.revision) {
						panel.status.setText("Connection inputs changed. Choose the source schema again.");
						return;
					}
					final var names = get();
					final String selected = picker.apply(names);
					if (requested != panel.revision)
						panel.status.setText("Connection inputs changed. Choose the source schema again.");
					else if (selected == null)
						panel.status.setText("Schema selection cancelled. Existing settings retained.");
					else {
						if (!selected.isEmpty() && !names.contains(selected))
							throw new IllegalArgumentException("Choose a schema from the current source list.");
						panel.sourceSchema.setText(selected);
						panel.status.setText("Source schema selected for New job: "
								+ (selected.isEmpty() ? "current schema" : selected) + ". Saved jobs are unchanged.");
					}
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					failed(failure);
				} catch (ExecutionException failure) {
					failed(failure.getCause());
				} catch (RuntimeException failure) {
					failed(failure);
				} finally {
					panel.busy(false);
				}
			}

			private void failed(Throwable failure) {
				panel.status.setText("Unable to read source schemas. Existing settings retained; see Details.");
				panel.statusPanel.setDetails(panel.status.getText() + "\n" + EditorStatusPanel.failureDetails(failure));
			}
		}.execute();
	}

	void chooseNewMigration() {
		if (panel.isBusy())
			return;
		if (!validateNewMigrationConnections())
			return;
		final var chooser = workspaceDialogs.forDirectory("");
		chooser.setDialogTitle("Choose a folder for migration settings");
		if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION)
			createMigration(chooser.getSelectedFile().toPath());
	}

	void buildConnectionUrl(final ConnectionFields fields, final java.util.function.Function<String, String> dialog) {
		if (panel.isBusy())
			return;
		if (fields != panel.source && fields != panel.target)
			throw new IllegalArgumentException("Unknown connection fields.");
		final long requested = panel.revision;
		panel.busy(true);
		try {
			final String url = dialog.apply(fields.jdbcUrl.getText());
			if (requested != panel.revision)
				panel.status.setText("Connection inputs changed. Open Build URL again.");
			else if (url == null)
				panel.status.setText("URL builder cancelled. Existing connection retained.");
			else {
				new PlanningJdbc.Spec(url, "", "");
				fields.jdbcUrl.setText(url);
				panel.status.setText(
						"JDBC URL updated. Enter credentials and use Test connection; no connection was opened.");
			}
		} catch (RuntimeException failure) {
			panel.status.setText("Unable to build the JDBC URL. Existing connection retained.");
			panel.statusPanel.setDetails(panel.status.getText() + "\n" + EditorStatusPanel.failureDetails(failure));
		} finally {
			panel.busy(false);
		}
	}

	String showUrlBuilder(final String currentUrl, final String connectionName) {
		return JdbcUrlBuilderPanel.showDialog(panel, currentUrl, connectionName);
	}

	void testConnection(final ConnectionFields fields) {
		if (panel.isBusy())
			return;
		if (fields != panel.source && fields != panel.target)
			throw new IllegalArgumentException("Unknown connection fields.");
		final String name = fields == panel.source ? "Source" : "Target";
		final PlanningJdbc.Spec connection;
		try {
			connection = fields.snapshot().spec();
		} catch (IllegalArgumentException failure) {
			panel.status.setText(name + " connection: enter a JDBC URL starting with jdbc: before testing.");
			fields.jdbcUrl.requestFocusInWindow();
			return;
		}
		final char[] password = fields.password.getPassword();
		final long requested = panel.revision;
		panel.busy(true);
		panel.status.setText("Testing " + name.toLowerCase(java.util.Locale.ROOT)
				+ " connection. No table rows are read or written.");
		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() {
				try {
					return connectionTester.test(connection, password);
				} finally {
					Arrays.fill(password, '\0');
				}
			}

			@Override
			protected void done() {
				try {
					if (requested != panel.revision) {
						panel.status.setText("Connection inputs changed. Test the connection again.");
						return;
					}
					final String product = get();
					panel.status.setText(name + " connection succeeded: " + product);
					panel.statusPanel.setDetails(panel.status.getText()
							+ "\nConnection and supported dialect checked. Inspect the migration plan to validate tables, keys and checkpoint state. This test does not approve execution.");
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					testFailed(failure);
				} catch (ExecutionException failure) {
					testFailed(failure.getCause());
				} finally {
					panel.busy(false);
				}
			}

			private void testFailed(final Throwable failure) {
				panel.status
						.setText(name + " connection failed. Check the URL, credentials and JDBC driver; see Details.");
				panel.statusPanel.setDetails(panel.status.getText() + "\n" + EditorStatusPanel.failureDetails(failure));
			}
		}.execute();
	}

	void createMigration(final Path parent) {
		if (panel.isBusy())
			return;
		if (!validateNewMigrationConnections())
			return;
		final PlanningJdbc.Spec connection = panel.source.snapshot().spec();
		final char[] password = panel.source.password.getPassword();
		final String schemaName = panel.sourceSchema.getText();
		final long requested = panel.revision;
		panel.busy(true);
		panel.status.setText("Reading source tables for a new migration. No rows are copied.");
		new SwingWorker<DataMigrationDraftService.Prepared, Void>() {
			protected DataMigrationDraftService.Prepared doInBackground() {
				try {
					return draftDiscovery.discover(connection, password, schemaName, parent);
				} finally {
					Arrays.fill(password, '\0');
				}
			}

			protected void done() {
				boolean inspect = false;
				try {
					final var prepared = get();
					if (requested != panel.revision) {
						panel.status.setText("Connection inputs changed. Start New job again.");
						return;
					}
					workspaceDialogs.rememberDirectory(parent);
					final var saved = draftDialog.apply(prepared);
					if (saved != null && requested == panel.revision) {
						panel.configurationFile.setText(saved.file().toString());
						panel.expectedFingerprint.setText(saved.fingerprint());
						inspect = true;
					} else
						panel.status.setText("New job cancelled or inputs changed. Previous settings retained.");
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					creationFailed(failure);
				} catch (ExecutionException failure) {
					creationFailed(failure.getCause());
				} catch (RuntimeException failure) {
					creationFailed(failure);
				} finally {
					panel.busy(false);
				}
				if (inspect)
					panel.build();
			}
		}.execute();
	}

	private void creationFailed(final Throwable failure) {
		panel.status.setText("New job could not be created. Existing settings retained.");
		panel.statusPanel.setDetails(panel.status.getText() + "\n" + EditorStatusPanel.failureDetails(failure));
	}

	private boolean validateNewMigrationConnections() {
		for (final var fields : new ConnectionFields[] { panel.source, panel.target }) {
			try {
				fields.snapshot().spec();
			} catch (IllegalArgumentException failure) {
				final String name = fields == panel.source ? "Source" : "Target";
				panel.status.setText(name + " connection: enter a JDBC URL starting with jdbc: before New job.");
				panel.statusPanel.setDetails(panel.status.getText()
						+ "\nYou can also load a saved connection profile. Passwords must be entered for this session.");
				fields.jdbcUrl.requestFocusInWindow();
				return false;
			}
		}
		return true;
	}

	private com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot showNewMigration(
			final DataMigrationDraftService.Prepared prepared) {
		final var saved = new java.util.concurrent.atomic.AtomicReference<com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot>();
		final var dialog = new javax.swing.JDialog(javax.swing.SwingUtilities.getWindowAncestor(panel),
				"New job — select tables", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		final var draft = new JobDraftPanel(prepared.schema(), prepared.directory(), snapshot -> {
			saved.set(snapshot);
			dialog.dispose();
		}, dialog::dispose);
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
		dialog.setLocationRelativeTo(panel);
		dialog.setVisible(true);
		return saved.get();
	}
}
