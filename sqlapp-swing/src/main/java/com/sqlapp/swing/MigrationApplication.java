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
			final var frame = new JFrame("sqlapp — Migration review");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			final var tabs = new JTabbedPane();
			tabs.addTab("Schema comparison", new SchemaComparisonPanel());
			final var schemaPlan = new SchemaMigrationPlanPanel();
			tabs.addTab("Schema migration plan", schemaPlan);
			final var dataPlan = new DataMigrationPlanPanel();
			tabs.addTab("Data migration plan", dataPlan);
			final var jobEditor = new JobConfigurationEditorPanel(snapshot -> {
				if (!dataPlan.configurationFile.isEnabled()) {
					JOptionPane.showMessageDialog(frame,
							"Wait for the current data operation before selecting another job.");
					return;
				}
				dataPlan.configurationFile.setText(snapshot.file().toString());
				dataPlan.expectedFingerprint.setText(snapshot.fingerprint());
				tabs.setSelectedComponent(dataPlan);
			});
			tabs.addTab("Job YAML editor", jobEditor);
			tabs.addTab("Migration reports", new ReportReviewPanel());
			frame.setContentPane(tabs);
			frame.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(final WindowEvent event) {
					frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
					if (schemaPlan.isExecuting() || dataPlan.isExecuting() || jobEditor.isBusy()) {
						JOptionPane.showMessageDialog(frame,
								"An operation is running. Wait for its result before closing.", "Operation running",
								JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					if (jobEditor.canDiscardChanges())
						frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				}
			});
			frame.setSize(1000, 700);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
}
