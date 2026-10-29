/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;

/** Desktop entry point for migration comparison, planning and review. */
public final class MigrationApplication {
	private MigrationApplication() {
	}

	public static void main(final String[] args) {
		if (GraphicsEnvironment.isHeadless()) {
			throw new IllegalStateException("sqlapp Swing requires a graphical desktop.");
		}
		SwingUtilities.invokeLater(() -> {
			DesktopAppearance.install();
			final var frame = new JFrame("sqlapp — RDB transfer");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			final var tabs = new JTabbedPane();
			final var comparison = new SchemaComparisonPanel();
			tabs.addTab("Schema comparison", comparison);
			final var schemaPlan = new SchemaMigrationPlanPanel();
			tabs.addTab("Schema migration plan", schemaPlan);
			comparison.setDirectoryReceiver(directory -> {
				if (!schemaPlan.selectUpDirectory(directory))
					return false;
				tabs.setSelectedComponent(schemaPlan);
				return true;
			});
			final var sqlEditor = new SqlEditorPanel(directory -> {
				if (!schemaPlan.selectUpDirectory(directory))
					return false;
				tabs.setSelectedComponent(schemaPlan);
				return true;
			});
			tabs.addTab("SQL editor", sqlEditor);
			comparison.setSqlEditor(path -> {
				if (!sqlEditor.requestOpen(path))
					return false;
				tabs.setSelectedComponent(sqlEditor);
				return true;
			});

			final var dataPlan = new DataMigrationPlanPanel();
			tabs.addTab("Advanced Data Job", dataPlan);
			final var directTransfer = new DirectTransferPanel();
			directTransfer.setSqlEditor(path -> {
				if (!sqlEditor.requestOpen(path))
					return false;
				tabs.setSelectedComponent(sqlEditor);
				return true;
			});
			final var jobEditor = new JobConfigurationEditorPanel(snapshot -> {
				if (!dataPlan.configurationFile.isEnabled()) {
					JOptionPane.showMessageDialog(frame,
							"Wait for the current data operation before selecting another job.");
					return;
				}
				dataPlan.showInputs();
				dataPlan.configurationFile.setText(snapshot.file().toString());
				dataPlan.expectedFingerprint.setText(snapshot.fingerprint());
				tabs.setSelectedComponent(dataPlan);
			});
			tabs.addTab("Job YAML editor", jobEditor);
			dataPlan.setJobEditor(path -> {
				if (!jobEditor.requestOpen(path))
					return false;
				tabs.setSelectedComponent(jobEditor);
				return true;
			});
			final var reports = new ReportReviewPanel();
			tabs.addTab("Migration reports", reports);
			final java.util.function.BiPredicate<java.nio.file.Path, ReportReviewService.Type> openReport = (path,
					type) -> {
				if (!reports.requestOpen(path, type))
					return false;
				tabs.setSelectedComponent(reports);
				return true;
			};
			schemaPlan.savedReport.setReceiver(openReport);
			dataPlan.savedReport.setReceiver(openReport);
			final var drivers = new JdbcDriverPanel();
			final var fileTransfer = new FileTransferPanel();
			final var mdbTransfer = new MdbTransferPanel();
			mdbTransfer.transfer.setSqlEditor(path -> {
				if (!sqlEditor.requestOpen(path))
					return false;
				tabs.setSelectedComponent(sqlEditor);
				return true;
			});
			mdbTransfer.csv.setSqlEditor(path -> {
				if (!sqlEditor.requestOpen(path))
					return false;
				tabs.setSelectedComponent(sqlEditor);
				return true;
			});
			fileTransfer.setSqlEditor(path -> {
				if (!sqlEditor.requestOpen(path))
					return false;
				tabs.setSelectedComponent(sqlEditor);
				return true;
			});
			tabs.addTab("CSV package transfer", fileTransfer);
			tabs.addTab("Access file migration", mdbTransfer);
			tabs.addTab("JDBC drivers", drivers);
			final java.util.function.BooleanSupplier activity = () -> comparison.isBusy() || schemaPlan.isBusy()
					|| dataPlan.isBusy() || jobEditor.isBusy() || sqlEditor.isBusy() || reports.isBusy()
					|| drivers.isBusy() || fileTransfer.isBusy() || directTransfer.isBusy() || mdbTransfer.isBusy();
			drivers.setBlockedSupplier(activity);
			drivers.setConnectionReceivers(name -> {
				if (!schemaPlan.selectDriverClass(name))
					return false;
				tabs.setSelectedComponent(schemaPlan);
				return true;
			}, name -> {
				if (!directTransfer.selectDriverClass(name, true))
					return false;
				tabs.setSelectedComponent(directTransfer);
				return true;
			}, name -> {
				if (!directTransfer.selectDriverClass(name, false))
					return false;
				tabs.setSelectedComponent(directTransfer);
				return true;
			});
			tabs.insertTab("Direct JDBC transfer", null, directTransfer,
					"Copy directly between source and target connections, or create tables only", 0);
			tabs.remove(fileTransfer);
			tabs.insertTab("CSV package transfer", null, fileTransfer,
					"Create a shared job, export, review and import CSV", 1);
			MigrationStartPanel.install(tabs, java.util.Map.of(MigrationStartPanel.Destination.DATA, directTransfer,
					MigrationStartPanel.Destination.LEGACY_DATA, dataPlan, MigrationStartPanel.Destination.FILE,
					fileTransfer, MigrationStartPanel.Destination.MDB, mdbTransfer,
					MigrationStartPanel.Destination.COMPARE, comparison, MigrationStartPanel.Destination.REPORTS,
					reports, MigrationStartPanel.Destination.SCHEMA_PLAN, schemaPlan,
					MigrationStartPanel.Destination.SQL_EDITOR, sqlEditor, MigrationStartPanel.Destination.JOB_EDITOR,
					jobEditor, MigrationStartPanel.Destination.DRIVERS, drivers));
			frame.setContentPane(tabs);
			frame.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(final WindowEvent event) {
					frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
					if (activity.getAsBoolean()) {
						JOptionPane.showMessageDialog(frame,
								"An operation is running. Wait for its result before closing.", "Operation running",
								JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					if (sqlEditor.canDiscardChanges() && jobEditor.canDiscardChanges() && !activity.getAsBoolean())
						frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				}
			});
			frame.setSize(1000, 700);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
}
