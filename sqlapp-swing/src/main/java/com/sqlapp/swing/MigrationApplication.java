/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/** Desktop entry point for migration comparison, planning and review. */
public final class MigrationApplication {
	private MigrationApplication() {
	}

	public static void main(final String[] args) {
		if (GraphicsEnvironment.isHeadless()) {
			throw new IllegalStateException("sqlapp Swing requires a graphical desktop.");
		}
		SwingUtilities.invokeLater(MigrationApplication::show);
	}

	private static void show() {
		DesktopAppearance.install();
		final var frame = new JFrame("sqlapp — RDB transfer");
		// One row: wrapped rows reorder themselves on selection, which moves tabs under
		// the pointer.
		final var tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);

		final var comparison = new SchemaComparisonPanel();
		final var schemaPlan = new SchemaMigrationPlanPanel();
		final Predicate<Path> useSchemaDirectory = select(tabs, schemaPlan, schemaPlan::selectUpDirectory);
		final var sqlEditor = new SqlEditorPanel(useSchemaDirectory);
		final Predicate<Path> openSql = select(tabs, sqlEditor, sqlEditor::requestOpen);
		final var dataPlan = new DataMigrationPlanPanel();
		final var directTransfer = new DirectTransferPanel();
		final var jobEditor = new JobConfigurationEditorPanel(snapshot -> {
			if (!dataPlan.configurationFile.isEnabled()) {
				JOptionPane.showMessageDialog(frame,
						"Wait for the current data operation before selecting another job.");
				return;
			}
			dataPlan.showInputs();
			dataPlan.configurationFile.setText(snapshot.file().toString());
			dataPlan.expectedFingerprint.setText(snapshot.fingerprint());
			NestedTabs.select(tabs, dataPlan);
		});
		final var reports = new ReportReviewPanel();
		final var drivers = new JdbcDriverPanel();
		final var fileTransfer = new FileTransferPanel();
		final var mdbTransfer = new MdbTransferPanel();

		tabs.addTab("Direct JDBC transfer", null, directTransfer,
				"Copy directly between source and target connections, or create tables only");
		tabs.addTab("File transfer", null, fileTransfer, "Create a job, export, review and import CSV");
		tabs.addTab("Access file migration", null, mdbTransfer, "Move an MDB / ACCDB file into a database");
		tabs.addTab("Migration reports", null, reports, "Open saved migration and verification reports");
		// Tools mirror the Start screen's "More tools"; the main tasks stay on the top
		// row.
		final var tools = new JTabbedPane(JTabbedPane.LEFT, JTabbedPane.SCROLL_TAB_LAYOUT);
		tools.addTab("Schema comparison", comparison);
		tools.addTab("Schema migration plan", schemaPlan);
		tools.addTab("SQL editor", sqlEditor);
		tools.addTab("Job YAML editor", jobEditor);
		tools.addTab("JDBC drivers", drivers);
		tools.addTab("Advanced Data Job", dataPlan);
		tabs.addTab("Tools", null, tools,
				"Schema comparison and migration, editors, JDBC drivers and advanced data jobs");

		comparison.setDirectoryReceiver(useSchemaDirectory);
		comparison.setSqlEditor(openSql);
		directTransfer.setSqlEditor(openSql);
		fileTransfer.setSqlEditor(openSql);
		mdbTransfer.transfer.setSqlEditor(openSql);
		mdbTransfer.csv.setSqlEditor(openSql);
		dataPlan.setJobEditor(select(tabs, jobEditor, jobEditor::requestOpen));

		final BiPredicate<Path, ReportReviewService.Type> openReport = (path, type) -> {
			if (!reports.requestOpen(path, type))
				return false;
			NestedTabs.select(tabs, reports);
			return true;
		};
		schemaPlan.savedReport.setReceiver(openReport);
		dataPlan.savedReport.setReceiver(openReport);
		directTransfer.savedComparison.setReceiver(openReport);
		fileTransfer.savedComparison.setReceiver(openReport);
		mdbTransfer.transfer.savedComparison.setReceiver(openReport);
		mdbTransfer.csv.savedComparison.setReceiver(openReport);

		final BooleanSupplier activity = () -> comparison.isBusy() || schemaPlan.isBusy() || dataPlan.isBusy()
				|| jobEditor.isBusy() || sqlEditor.isBusy() || reports.isBusy() || drivers.isBusy()
				|| fileTransfer.isBusy() || directTransfer.isBusy() || mdbTransfer.isBusy();
		drivers.setBlockedSupplier(activity);
		drivers.setConnectionReceivers(select(tabs, schemaPlan, schemaPlan::selectDriverClass),
				select(tabs, directTransfer, name -> directTransfer.selectDriverClass(name, true)),
				select(tabs, directTransfer, name -> directTransfer.selectDriverClass(name, false)));

		MigrationStartPanel.install(tabs, java.util.Map.of(MigrationStartPanel.Destination.DATA, directTransfer,
				MigrationStartPanel.Destination.LEGACY_DATA, dataPlan, MigrationStartPanel.Destination.FILE,
				fileTransfer, MigrationStartPanel.Destination.MDB, mdbTransfer, MigrationStartPanel.Destination.COMPARE,
				comparison, MigrationStartPanel.Destination.REPORTS, reports,
				MigrationStartPanel.Destination.SCHEMA_PLAN, schemaPlan, MigrationStartPanel.Destination.SQL_EDITOR,
				sqlEditor, MigrationStartPanel.Destination.JOB_EDITOR, jobEditor,
				MigrationStartPanel.Destination.DRIVERS, drivers));
		frame.setContentPane(tabs);
		// Closing is always decided here: running work blocks it, unsaved editors may
		// veto it.
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent event) {
				if (activity.getAsBoolean()) {
					JOptionPane.showMessageDialog(frame, "An operation is running. Wait for its result before closing.",
							"Operation running", JOptionPane.INFORMATION_MESSAGE);
					return;
				}
				if (!sqlEditor.canDiscardChanges() || !jobEditor.canDiscardChanges() || activity.getAsBoolean())
					return;
				frame.dispose();
				System.exit(0);
			}
		});
		frame.setSize(1200, 840);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	/**
	 * Runs an action that accepts or rejects a value, then shows its tab when
	 * accepted.
	 */
	private static <T> Predicate<T> select(final JTabbedPane tabs, final Component tab, final Predicate<T> action) {
		return value -> {
			if (!action.test(value))
				return false;
			NestedTabs.select(tabs, tab);
			return true;
		};
	}
}
