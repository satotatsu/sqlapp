/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import com.sqlapp.data.db.command.migration.schema.MigrationSqlInspection;

/**
 * Read-only presentation of shared parser output, separate from the source
 * buffer.
 */
final class SqlSplitPreviewPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTable parts = new JTable();
	final org.fife.ui.rsyntaxtextarea.RSyntaxTextArea sql = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_SQL, false);
	final JTextArea summary = new JTextArea(3, 0);
	final JButton previous = new JButton("Previous part");
	final JButton next = new JButton("Next part");
	final JButton copy = new JButton("Copy part");
	final JButton findSource = new JButton("Find in SQL");
	final JCheckBox hideComments = new JCheckBox("Hide comments");
	final JTextField filter = new JTextField(14);
	final JLabel count = new JLabel();
	final JLabel copyStatus = new JLabel("Select a part to inspect or copy its parser output.");
	private final Consumer<String> clipboard;
	private MigrationSqlInspection.Result result;
	private Consumer<String> sourceFinder;

	SqlSplitPreviewPanel() {
		this(text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null));
	}

	SqlSplitPreviewPanel(final Consumer<String> clipboard) {
		super(new BorderLayout(4, 4));
		this.clipboard = java.util.Objects.requireNonNull(clipboard, "clipboard");
		summary.setEditable(false);
		summary.setLineWrap(true);
		summary.setWrapStyleWord(true);
		final var tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		tools.add(previous);
		tools.add(next);
		tools.add(copy);
		tools.add(findSource);
		findSource.setToolTipText("Select the parser text in the source SQL when it has exactly one literal match.");
		tools.add(hideComments);
		final var filterLabel = new JLabel("Filter parts");
		filterLabel.setLabelFor(filter);
		tools.add(filterLabel);
		tools.add(filter);
		tools.add(count);
		final var actions = new JPanel(new BorderLayout(4, 4));
		actions.add(tools, BorderLayout.NORTH);
		actions.add(copyStatus, BorderLayout.SOUTH);
		final var header = new JPanel(new BorderLayout(4, 4));
		header.add(new JScrollPane(summary), BorderLayout.CENTER);
		header.add(actions, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);
		parts.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		parts.setAutoCreateRowSorter(true);
		final var scroll = new JScrollPane(parts);
		scroll.setColumnHeaderView(parts.getTableHeader());
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, CodeEditors.scroll(sql));
		split.setResizeWeight(0.35);
		add(split, BorderLayout.CENTER);
		parts.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting())
				refreshSelection();
		});
		previous.addActionListener(event -> move(-1));
		next.addActionListener(event -> move(1));
		copy.addActionListener(event -> copySelected());
		findSource.addActionListener(event -> {
			final var selected = selectedPart();
			if (selected != null && sourceFinder != null)
				sourceFinder.accept(selected.text());
		});
		hideComments.addActionListener(event -> applyFilter());
		filter.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent event) {
				applyFilter();
			}

			public void removeUpdate(final DocumentEvent event) {
				applyFilter();
			}

			public void changedUpdate(final DocumentEvent event) {
				applyFilter();
			}
		});
		clear();
	}

	void clear() {
		result = null;
		parts.setModel(new DefaultTableModel());
		filter.setText("");
		hideComments.setSelected(false);
		applyFilter();
		summary.setText(
				"Select a product/version and inspect statement boundaries. No SQL validation or execution is performed.");
	}

	void setSourceFinder(final Consumer<String> finder) {
		sourceFinder = finder;
		refreshSelection();
	}

	void showResult(final MigrationSqlInspection.Result inspected) {
		clear();
		result = inspected;
		summary.setText(inspected.product() + " " + inspected.version() + "; resolved dialect: "
				+ inspected.resolvedDialect() + "\nSQL statements / batches: " + inspected.sqlParts() + "; comments: "
				+ inspected.commentParts()
				+ "\nParser output only. SQL Server batches may contain several statements. Stored-program boundaries require manual review. Syntax, permissions and execution safety are not validated.");
		final var model = new DefaultTableModel(new Object[] { "Part", "Kind", "Characters", "First line" }, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}

			@Override
			public Class<?> getColumnClass(int column) {
				return column == 0 || column == 2 ? Integer.class : String.class;
			}
		};
		for (final var part : inspected.parts()) {
			model.addRow(new Object[] { part.number(), part.comment() ? "Comment" : "SQL / batch", part.text().length(),
					part.text().lines().findFirst().orElse("") });
		}
		parts.setModel(model);
		parts.getColumnModel().getColumn(3).setPreferredWidth(500);
		applyFilter();
	}

	@SuppressWarnings("unchecked")
	private void applyFilter() {
		parts.clearSelection();
		final var sorter = (TableRowSorter<DefaultTableModel>) parts.getRowSorter();
		final String query = filter.getText().toLowerCase(Locale.ROOT);
		final var filtered = result;
		final boolean excludeComments = hideComments.isSelected();
		sorter.setRowFilter(filtered == null ? null : new RowFilter<DefaultTableModel, Integer>() {
			@Override
			public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
				final var part = filtered.parts().get(entry.getIdentifier());
				return (!excludeComments || !part.comment()) && part.text().toLowerCase(Locale.ROOT).contains(query);
			}
		});
		count.setText(parts.getRowCount() + " / " + parts.getModel().getRowCount() + " parts");
		if (parts.getRowCount() > 0)
			parts.setRowSelectionInterval(0, 0);
		else
			refreshSelection();
	}

	private MigrationSqlInspection.Part selectedPart() {
		final int row = parts.getSelectedRow();
		return result != null && row >= 0 ? result.parts().get(parts.convertRowIndexToModel(row)) : null;
	}

	private void refreshSelection() {
		final var part = selectedPart();
		sql.setText(part == null ? "" : part.text());
		sql.setCaretPosition(0);
		copy.setEnabled(part != null);
		findSource.setEnabled(part != null && sourceFinder != null);
		previous.setEnabled(part != null && parts.getSelectedRow() > 0);
		next.setEnabled(part != null && parts.getSelectedRow() + 1 < parts.getRowCount());
		copyStatus.setText("Parser output may omit statement delimiters. Copying does not validate SQL.");
		copyStatus.setToolTipText(copyStatus.getText());
	}

	private void move(final int offset) {
		final int row = parts.getSelectedRow() + offset;
		if (row < 0 || row >= parts.getRowCount())
			return;
		parts.setRowSelectionInterval(row, row);
		parts.scrollRectToVisible(parts.getCellRect(row, 0, true));
	}

	private void copySelected() {
		final var part = selectedPart();
		if (part == null)
			return;
		try {
			clipboard.accept(part.text());
			copyStatus.setText("Copied parser output for part " + part.number() + ". Review delimiters before use.");
		} catch (final RuntimeException failure) {
			copyStatus.setText("Copy failed: " + failure);
		}
		copyStatus.setToolTipText(copyStatus.getText());
	}
}
