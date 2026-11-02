/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService;

/**
 * Captured-Schema evidence only; target database state is intentionally
 * unknown.
 */
final class TransferNameMappingSchemaPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final DefaultTableModel model = new DefaultTableModel(
			new String[] { "Source table", "Source column", "Target table", "Target column", "Source model type",
					"Source PK", "Source nullable", "Target definition preview", "Preview issue" },
			0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	final ReportDetailTablePanel details = new ReportDetailTablePanel(new JTable(model), null, "Find mapping",
			"mapped columns");
	final JTextArea evidence = new JTextArea(4, 1);
	private boolean captured;
	private final javax.swing.table.TableColumn definitionColumn;
	private final javax.swing.table.TableColumn issueColumn;

	TransferNameMappingSchemaPanel() {
		super(new BorderLayout(4, 4));
		add(details, BorderLayout.CENTER);
		int[] widths = { 170, 150, 170, 150, 110, 80, 80, 360, 360 };
		for (int column = 0; column < widths.length; column++)
			details.table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
		definitionColumn = details.table.getColumnModel().getColumn(7);
		issueColumn = details.table.getColumnModel().getColumn(8);
		details.table.removeColumn(definitionColumn);
		details.table.removeColumn(issueColumn);
		evidence.setEditable(false);
		evidence.setLineWrap(true);
		evidence.setWrapStyleWord(true);
		add(new JScrollPane(evidence), BorderLayout.SOUTH);
		evidence.setText("Choose a source Schema XML and use Check Schema. No database is accessed or modified.");
	}

	void showResult(TransferNameMappingInspectionService.Result result) {
		model.setRowCount(0);
		if (result.targetDialect() != null) {
			if (details.table.convertColumnIndexToView(7) < 0)
				details.table.addColumn(definitionColumn);
			if (details.table.convertColumnIndexToView(8) < 0)
				details.table.addColumn(issueColumn);
			details.table.moveColumn(details.table.convertColumnIndexToView(7), 5);
			details.table.moveColumn(details.table.convertColumnIndexToView(8), 6);
		} else {
			if (details.table.convertColumnIndexToView(7) >= 0)
				details.table.removeColumn(definitionColumn);
			if (details.table.convertColumnIndexToView(8) >= 0)
				details.table.removeColumn(issueColumn);
		}
		for (var table : result.tables())
			for (var column : table.columns())
				model.addRow(new Object[] { table.source(), column.source(), table.target(), column.target(),
						column.sourceType(), column.primaryKey() ? "Yes" : "No", column.nullable() ? "Yes" : "No",
						column.targetDefinition() == null
								? column.previewIssue() == null ? "Not requested" : "Unavailable"
								: column.targetDefinition(),
						column.previewIssue() == null ? "" : column.previewIssue() });
		evidence.setText("Captured Schema mapping checked: " + result.tables().size() + " tables; "
				+ result.warnings().size() + " warnings. Database Review is still required.\n" + "Schema: "
				+ result.schemaFile() + "\n" + result.schemaFingerprint() + "\nMapping: " + result.mappingFingerprint()
				+ (result.warnings().isEmpty() ? "" : "\n" + String.join("\n", result.warnings())));
		if (result.targetDialect() != null)
			evidence.append("\nTarget builder: " + result.targetDialect() + "; unavailable column previews: "
					+ result.tables().stream().flatMap(table -> table.columns().stream())
							.filter(column -> column.previewIssue() != null).count()
					+ ". Offline column definitions only; table factories may add or change syntax. Existing target columns, generated target-only keys and full CREATE/ALTER SQL are not represented. No value conversion or verification was performed.");
		evidence.setCaretPosition(0);
		captured = true;
	}

	void clear(String message) {
		model.setRowCount(0);
		evidence.setText(message);
		captured = false;
	}

	void changed() {
		if (captured)
			clear("Names, source Schema path or target preview changed. Use Check Schema again; database Review is still required.");
	}
}
