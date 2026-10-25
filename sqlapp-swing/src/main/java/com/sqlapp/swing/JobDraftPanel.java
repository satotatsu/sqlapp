/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder.SchemaSnapshot;

/**
 * Selects canonical Schema tables and creates an unsaved YAML draft, without
 * JDBC.
 */
final class JobDraftPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField schemaFile = new JTextField();
	final JTextField fingerprint = new JTextField();
	final JTextField jobId = new JTextField(16);
	final JSpinner chunkSize = new JSpinner(new SpinnerNumberModel(10_000, 1, 1_000_000, 1000));
	final JCheckBox verify = new JCheckBox("Verify copied data", true);
	final JButton inspect = new JButton("Read Schema");
	final JButton create = new JButton("Create draft in editor");
	final JButton select = new JButton("Select visible eligible");
	final JButton clear = new JButton("Clear visible");
	final JButton clearAll = new JButton("Clear all selected");
	final JTextField filter = new JTextField();
	final JButton clearFilter = new JButton("Clear filter");
	final JLabel count = new JLabel();
	final JLabel status = new JLabel(
			"Choose Schema XML. INSERT into empty targets; resume disabled. No database connection.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	final DefaultTableModel model = new DefaultTableModel(
			new String[] { "Include", "Table", "Primary key", "Availability" }, 0) {
		@Override
		public Class<?> getColumnClass(int column) {
			return column == 0 ? Boolean.class : String.class;
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			return !busy && column == 0 && snapshot != null && snapshot.tables().get(row).selectable();
		}
	};
	final JTable tables = new JTable(model) {
		@Override
		public String getToolTipText(java.awt.event.MouseEvent event) {
			final int row = rowAtPoint(event.getPoint()), column = columnAtPoint(event.getPoint());
			return row < 0 || column < 0 ? null : String.valueOf(getValueAt(row, column));
		}
	};
	private final BulkMigrationJobDraftBuilder builder;
	private final Consumer<String> ready;
	private SchemaSnapshot snapshot;
	private boolean busy;
	private boolean selectingBatch;
	private final javax.swing.table.TableRowSorter<DefaultTableModel> sorter = new javax.swing.table.TableRowSorter<>(
			model);
	private long revision;
	private final EditorFileChooser fileDialogs = new EditorFileChooser();
	private final JButton browse = new JButton("Browse…");

	JobDraftPanel(final Consumer<String> ready) {
		this(new BulkMigrationJobDraftBuilder(), ready);
	}

	JobDraftPanel(final BulkMigrationJobDraftBuilder builder, final Consumer<String> ready) {
		super(new BorderLayout(8, 8));
		this.builder = builder;
		this.ready = ready;
		setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var path = new JPanel(new BorderLayout(4, 0));
		path.add(new JLabel("Schema XML"), BorderLayout.WEST);
		path.add(schemaFile, BorderLayout.CENTER);
		final var files = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		files.add(browse);
		files.add(inspect);
		path.add(files, BorderLayout.EAST);
		fingerprint.setEditable(false);
		final var settings = new JPanel(new FlowLayout(FlowLayout.LEFT));
		settings.add(new JLabel("Job ID (optional)"));
		settings.add(jobId);
		settings.add(new JLabel("Chunk rows"));
		settings.add(chunkSize);
		settings.add(verify);
		final var top = new JPanel(new java.awt.GridLayout(3, 1, 4, 4));
		final var identity = new JPanel(new BorderLayout(4, 0));
		identity.add(new JLabel("Schema SHA-256"), BorderLayout.WEST);
		identity.add(fingerprint, BorderLayout.CENTER);
		top.add(path);
		top.add(identity);
		top.add(settings);
		add(top, BorderLayout.NORTH);
		tables.setRowSorter(sorter);
		sorter.setSortsOnUpdates(true);
		sorter.addRowSorterListener(event -> {
			if (!selectingBatch)
				refreshCounts();
		});
		tables.getColumnModel().getColumn(0).setMaxWidth(70);
		tables.getColumnModel().getColumn(1).setPreferredWidth(220);
		tables.getColumnModel().getColumn(3).setPreferredWidth(450);
		final var tableScroll = new JScrollPane(tables);
		tableScroll.setColumnHeaderView(tables.getTableHeader());
		final var find = new JPanel(new BorderLayout(6, 0));
		final var filterLabel = new JLabel("Find tables");
		filterLabel.setLabelFor(filter);
		find.add(filterLabel, BorderLayout.WEST);
		find.add(filter, BorderLayout.CENTER);
		find.add(clearFilter, BorderLayout.EAST);
		final var tableHeader = new JPanel(new BorderLayout(4, 4));
		tableHeader.add(find, BorderLayout.NORTH);
		tableHeader.add(count, BorderLayout.SOUTH);
		final var tableArea = new JPanel(new BorderLayout(4, 4));
		tableArea.add(tableHeader, BorderLayout.NORTH);
		tableArea.add(tableScroll, BorderLayout.CENTER);
		add(tableArea, BorderLayout.CENTER);
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(select);
		actions.add(clear);
		actions.add(clearAll);
		actions.add(create);
		create.setToolTipText("Create a draft for all selected tables, including selections hidden by the filter.");
		select.setToolTipText("Select eligible tables in the current filtered view only.");
		clear.setToolTipText("Clear selections in the current filtered view only.");
		clearAll.setToolTipText("Clear every selected table, including hidden selections.");
		final var bottom = new JPanel(new BorderLayout());
		bottom.add(actions, BorderLayout.NORTH);
		bottom.add(statusPanel, BorderLayout.SOUTH);
		add(bottom, BorderLayout.SOUTH);
		schemaFile.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent event) {
				changed();
			}

			public void removeUpdate(DocumentEvent event) {
				changed();
			}

			public void changedUpdate(DocumentEvent event) {
				changed();
			}
		});
		model.addTableModelListener(event -> {
			if (selectingBatch)
				return;
			draftOptionsChanged();
			refresh();
		});
		jobId.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent event) {
				draftOptionsChanged();
			}

			public void removeUpdate(DocumentEvent event) {
				draftOptionsChanged();
			}

			public void changedUpdate(DocumentEvent event) {
				draftOptionsChanged();
			}
		});
		chunkSize.addChangeListener(event -> draftOptionsChanged());
		verify.addItemListener(event -> draftOptionsChanged());
		browse.addActionListener(event -> {
			final var chooser = chooser();
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				schemaFile.setText(chooser.getSelectedFile().getAbsolutePath());
		});
		inspect.addActionListener(event -> inspectSchema());
		create.addActionListener(event -> createDraft());
		select.addActionListener(event -> updateSelection(true, false));
		clear.addActionListener(event -> updateSelection(false, false));
		clearAll.addActionListener(event -> updateSelection(false, true));
		clearFilter.addActionListener(event -> filter.setText(""));
		filter.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent event) {
				refreshFilter();
			}

			public void removeUpdate(DocumentEvent event) {
				refreshFilter();
			}

			public void changedUpdate(DocumentEvent event) {
				refreshFilter();
			}
		});
		for (final String keys : new String[] { "control F", "meta F" })
			getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(javax.swing.KeyStroke.getKeyStroke(keys),
					"find-tables");
		getActionMap().put("find-tables", new javax.swing.AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(java.awt.event.ActionEvent event) {
				if (!busy) {
					filter.requestFocusInWindow();
					filter.selectAll();
				}
			}
		});
		refresh();
	}

	boolean isBusy() {
		return busy;
	}

	private void changed() {
		revision++;
		snapshot = null;
		fingerprint.setText("");
		model.setRowCount(0);
		filter.setText("");
		if (!busy)
			status.setText("Schema input changed. Read Schema again before creating a draft.");
		refresh();
	}

	private void draftOptionsChanged() {
		revision++;
		if (!busy)
			status.setText("Draft options changed. Review the selection and create the draft again.");
	}

	JFileChooser chooser() {
		return fileDialogs.forFile(new javax.swing.filechooser.FileNameExtensionFilter("Schema XML", "xml"),
				schemaFile.getText());
	}

	private List<String> selected() {
		final var selected = new ArrayList<String>();
		for (int row = 0; row < model.getRowCount(); row++)
			if (Boolean.TRUE.equals(model.getValueAt(row, 0)))
				selected.add((String) model.getValueAt(row, 1));
		return selected;
	}

	private void refresh() {
		schemaFile.setEnabled(!busy);
		browse.setEnabled(!busy);
		inspect.setEnabled(!busy);
		jobId.setEnabled(!busy);
		chunkSize.setEnabled(!busy);
		verify.setEnabled(!busy);
		select.setEnabled(!busy && snapshot != null && tables.getRowCount() > 0);
		clear.setEnabled(!busy && snapshot != null && tables.getRowCount() > 0);
		clearAll.setEnabled(!busy && !selected().isEmpty());
		filter.setEnabled(!busy);
		clearFilter.setEnabled(!busy && !filter.getText().isEmpty());
		create.setEnabled(!busy && snapshot != null && !selected().isEmpty());
		tables.setEnabled(!busy);
		refreshCounts();
	}

	private void refreshFilter() {
		if (tables.isEditing())
			tables.getCellEditor().stopCellEditing();
		final String text = filter.getText().toLowerCase(java.util.Locale.ROOT);
		sorter.setRowFilter(text.isEmpty() ? null : new javax.swing.RowFilter<DefaultTableModel, Integer>() {
			public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
				for (int column = 1; column < entry.getValueCount(); column++)
					if (entry.getStringValue(column).toLowerCase(java.util.Locale.ROOT).contains(text))
						return true;
				return false;
			}
		});
		refresh();
	}

	private void refreshCounts() {
		int selected = 0, hidden = 0;
		for (int row = 0; row < model.getRowCount(); row++) {
			if (!Boolean.TRUE.equals(model.getValueAt(row, 0)))
				continue;
			selected++;
			if (tables.convertRowIndexToView(row) < 0)
				hidden++;
		}
		count.setText(tables.getRowCount() + " / " + model.getRowCount() + " tables; " + selected + " selected ("
				+ hidden + " hidden)");
	}

	private void updateSelection(final boolean include, final boolean all) {
		if (busy || snapshot == null)
			return;
		if (tables.isEditing())
			tables.getCellEditor().stopCellEditing();
		final var rows = new ArrayList<Integer>();
		for (int row = 0; row < (all ? model.getRowCount() : tables.getRowCount()); row++)
			rows.add(all ? row : tables.convertRowIndexToModel(row));
		selectingBatch = true;
		try {
			for (final int row : rows)
				if (!include || snapshot.tables().get(row).selectable())
					model.setValueAt(include, row, 0);
		} finally {
			selectingBatch = false;
		}
		draftOptionsChanged();
		refresh();
	}

	void inspectSchema() {
		if (busy)
			return;
		final String path = schemaFile.getText().trim();
		snapshot = null;
		model.setRowCount(0);
		filter.setText("");
		fingerprint.setText("");
		final long requested = revision;
		busy = true;
		refresh();
		status.setText("Reading Schema model and eligibility…");
		new SwingWorker<SchemaSnapshot, Void>() {
			protected SchemaSnapshot doInBackground() throws Exception {
				if (path.isEmpty())
					throw new IllegalArgumentException("Choose a Schema XML file.");
				return builder.inspect(Path.of(path));
			}

			protected void done() {
				try {
					final var result = get();
					if (requested != revision) {
						status.setText("Schema path or settings changed; read again.");
						return;
					}
					snapshot = result;
					fileDialogs.remember(Path.of(path));
					fingerprint.setText(result.fingerprint());
					for (final var table : result.tables())
						model.addRow(new Object[] { false, table.table(), String.join(", ", table.primaryKey()),
								table.selectable() ? "Available" : table.restriction() });
					status.setText(result.tables().size()
							+ " tables read. Select tables; review generated YAML before live inspection.");
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					failed(failure);
				} catch (ExecutionException failure) {
					failed(failure.getCause());
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	void createDraft() {
		if (busy || snapshot == null || selected().isEmpty())
			return;
		if (tables.isEditing())
			tables.getCellEditor().stopCellEditing();
		try {
			chunkSize.commitEdit();
		} catch (java.text.ParseException failure) {
			status.setText("Enter a valid chunk row count from 1 to 1000000.");
			return;
		}
		final var reviewed = snapshot;
		final var selected = selected();
		final String name = jobId.getText();
		final int chunk = ((Number) chunkSize.getValue()).intValue();
		final boolean verification = verify.isSelected();
		final long requested = revision;
		busy = true;
		refresh();
		status.setText("Rechecking Schema fingerprint and ordering selected tasks…");
		new SwingWorker<String, Void>() {
			protected String doInBackground() throws Exception {
				return builder.draft(reviewed, selected, name, chunk, verification);
			}

			protected void done() {
				try {
					final String yaml = get();
					if (requested == revision)
						ready.accept(yaml);
					else
						status.setText("Inputs changed; create the draft again.");
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					failed(failure);
				} catch (ExecutionException failure) {
					failed(failure.getCause());
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	private void failed(Throwable failure) {
		status.setText(EditorStatusPanel.failureMessage(failure));
		statusPanel.setDetails(EditorStatusPanel.failureDetails(failure));
	}
}
