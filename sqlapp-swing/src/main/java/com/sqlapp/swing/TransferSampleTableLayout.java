/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import javax.swing.JTable;
import javax.swing.table.JTableHeader;

/**
 * Bounded widths and complete, plain-text headers for offline sample tables.
 */
final class TransferSampleTableLayout {
	private TransferSampleTableLayout() {
	}

	static void scrollToColumn(JTable table, int modelColumn) {
		int view = table.convertColumnIndexToView(modelColumn);
		if (view < 0)
			throw new IllegalArgumentException("Choose a visible sample column.");
		var rectangle = table.getTableHeader().getHeaderRect(view);
		if (table.getParent() instanceof javax.swing.JViewport viewport) {
			rectangle.y = viewport.getViewPosition().y;
			rectangle.height = 1;
		}
		table.scrollRectToVisible(rectangle);
	}

	static void apply(JTable table) {
		var header = new JTableHeader(table.getColumnModel()) {
			private static final long serialVersionUID = 1L;

			@Override
			public String getToolTipText(java.awt.event.MouseEvent event) {
				int column = columnAtPoint(event.getPoint());
				return column < 0 ? null
						: "Column: " + table.getModel().getColumnName(table.convertColumnIndexToModel(column));
			}
		};
		table.setTableHeader(header);
		if (javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class,
				table) instanceof javax.swing.JScrollPane scroll)
			scroll.setColumnHeaderView(header);
		header.setToolTipText("Column names and source types");
		var metrics = header.getFontMetrics(header.getFont());
		for (int index = 0; index < table.getColumnCount(); index++) {
			var column = table.getColumnModel().getColumn(index);
			String name = table.getModel().getColumnName(column.getModelIndex());
			String measured = name.substring(0, Math.min(256, name.length()));
			int width = column.getModelIndex() == 0 ? 90
					: Math.max(160, Math.min(480, metrics.stringWidth(measured) + 28));
			column.setPreferredWidth(width);
		}
	}
}
