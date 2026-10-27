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
 * Selects canonical Schema tables for an editor draft or saved migration
 * settings, without JDBC.
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
	final JButton cancel = new JButton("Cancel");
	final JButton select = new JButton("Select visible eligible");
	final JButton clear = new JButton("Clear visible");
	final JButton clearAll = new JButton("Clear all selected");
	final JTextField filter = new JTextField();
	final JCheckBox selectedOnly = new JCheckBox("Selected tables only");
	final JButton clearFilter = new JButton("Clear filter");
	final JLabel count = new JLabel();
	final JLabel selectionHelp = new JLabel();
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
	private JPanel pathRow;
	private JPanel identityRow;
	private JPanel optionsRow;
	private JPanel header;
	private JPanel actions;
	private Path managedDirectory;

	@FunctionalInterface
	interface FileDraftWriter {
		String save(SchemaSnapshot snapshot, List<String> selected, int chunkSize, boolean verify) throws Exception;
	}

	private java.util.function.Supplier<FileDraftWriter> fileDraftWriter;
	private Consumer<com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot> savedReady;

	JobDraftPanel(final Consumer<String> ready) {
		this(new BulkMigrationJobDraftBuilder(), ready);
	}

	JobDraftPanel(final SchemaSnapshot schema, final Path directory,
			final Consumer<com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot> ready) {
		this(schema, directory, ready, () -> {
		});
	}

	JobDraftPanel(final SchemaSnapshot schema, final Path directory,
			final Consumer<com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot> ready,
			final Runnable cancelled) {
		this(yaml -> {
		});
		managedDirectory = directory;
		savedReady = ready;
		schemaFile.setText(schema.file().toString());
		showSnapshot(schema);
		pathRow.setVisible(false);
		identityRow.setVisible(false);
		optionsRow.setVisible(false);
		header.add(new JLabel("<html>Select tables to copy into existing, same-named empty target tables.<br>"
				+ "Defaults: verify copied data; no row replacement or checkpoint resume.</html>"), 0);
		final var advanced = new javax.swing.JToggleButton("Copy options");
		advanced.addActionListener(event -> {
			optionsRow.setVisible(advanced.isSelected());
			header.revalidate();
		});
		header.add(advanced);
		final var destination = new JTextField(
				directory.toAbsolutePath().normalize().resolve("migration.yaml").toString());
		destination.setEditable(false);
		destination.setToolTipText(destination.getText());
		final var destinationRow = new JPanel(new BorderLayout(4, 0));
		final var destinationLabel = new JLabel("Save settings to");
		destinationLabel.setLabelFor(destination);
		destinationRow.add(destinationLabel, BorderLayout.WEST);
		destinationRow.add(destination, BorderLayout.CENTER);
		header.add(destinationRow);
		for (final var component : header.getComponents())
			if (component instanceof javax.swing.JComponent field)
				field.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		actions.add(cancel);
		cancel.addActionListener(event -> {
			if (!busy)
				cancelled.run();
		});
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"),
				"cancelMigration");
		getActionMap().put("cancelMigration", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				if (!busy)
					cancelled.run();
			}
		});
		create.setText("Save and review plan");
		status.setText(
				"Select tables to copy into same-named empty target tables. Existing rows are not replaced. Verification is enabled.");
		refresh();
	}

	JobDraftPanel(final BulkMigrationJobDraftBuilder builder, final Consumer<String> ready) {
		super(new BorderLayout(8, 8));
		this.builder = builder;
		this.ready = ready;
		setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var path = new JPanel(new BorderLayout(4, 0));
		pathRow = path;
		path.add(new JLabel("Schema XML"), BorderLayout.WEST);
		path.add(schemaFile, BorderLayout.CENTER);
		FileDropHandler.path(schemaFile, false, java.util.List.of("xml"), () -> !busy,
				FileDropHandler.errors(status, statusPanel));
		final var files = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		files.add(browse);
		files.add(inspect);
		path.add(files, BorderLayout.EAST);
		fingerprint.setEditable(false);
		final var settings = new JPanel(new FlowLayout(FlowLayout.LEFT));
		optionsRow = settings;
		settings.add(new JLabel("Job ID (optional)"));
		settings.add(jobId);
		settings.add(new JLabel("Chunk rows"));
		settings.add(chunkSize);
		settings.add(verify);
		final var top = new JPanel();
		top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
		header = top;
		final var identity = new JPanel(new BorderLayout(4, 0));
		identityRow = identity;
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
				refresh();
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
		final var filterActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		filterActions.add(selectedOnly);
		filterActions.add(clearFilter);
		find.add(filterActions, BorderLayout.EAST);
		selectedOnly.setToolTipText("Show only included tables, also matching Find tables. Selections are retained.");
		final var tableHeader = new JPanel(new BorderLayout(4, 4));
		tableHeader.add(find, BorderLayout.NORTH);
		tableHeader.add(count, BorderLayout.SOUTH);
		tableHeader.add(selectionHelp, BorderLayout.CENTER);
		final var tableArea = new JPanel(new BorderLayout(4, 4));
		tableArea.add(tableHeader, BorderLayout.NORTH);
		tableArea.add(tableScroll, BorderLayout.CENTER);
		add(tableArea, BorderLayout.CENTER);
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		this.actions = actions;
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
		selectedOnly.addItemListener(event -> refreshFilter());
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
		selectedOnly.setSelected(false);
		if (!busy)
			status.setText("Schema input changed. Read Schema again before creating a draft.");
		refresh();
	}

	void draftOptionsChanged() {
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
		schemaFile.setEnabled(!busy && managedDirectory == null);
		browse.setEnabled(!busy && managedDirectory == null);
		inspect.setEnabled(!busy && managedDirectory == null);
		jobId.setEnabled(!busy);
		chunkSize.setEnabled(!busy);
		verify.setEnabled(!busy);
		select.setEnabled(!busy && snapshot != null && tables.getRowCount() > 0);
		clear.setEnabled(!busy && snapshot != null && tables.getRowCount() > 0);
		clearAll.setEnabled(!busy && !selected().isEmpty());
		filter.setEnabled(!busy);
		selectedOnly.setEnabled(!busy && snapshot != null);
		clearFilter.setEnabled(!busy && !filter.getText().isEmpty());
		create.setEnabled(!busy && snapshot != null && !selected().isEmpty());
		cancel.setEnabled(!busy);
		tables.setEnabled(!busy);
		refreshCounts();
	}

	private void refreshFilter() {
		if (tables.isEditing())
			tables.getCellEditor().stopCellEditing();
		final String text = filter.getText().toLowerCase(java.util.Locale.ROOT);
		sorter.setRowFilter(new javax.swing.RowFilter<DefaultTableModel, Integer>() {
			public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
				if (selectedOnly.isSelected() && !Boolean.TRUE.equals(entry.getValue(0)))
					return false;
				if (text.isEmpty())
					return true;
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
		if (snapshot == null)
			selectionHelp.setText("Read Schema to see the tables available for selection.");
		else if (model.getRowCount() == 0)
			selectionHelp.setText("No tables in this Schema. Choose another Schema or source schema.");
		else if (snapshot.tables().stream().noneMatch(table -> table.selectable()))
			selectionHelp.setText("No eligible tables. Review Availability; this copy workflow requires primary keys.");
		else if (selectedOnly.isSelected() && selected == 0)
			selectionHelp.setText("No tables selected. Turn off Selected tables only to choose tables.");
		else if (tables.getRowCount() == 0)
			selectionHelp.setText(selected == 0 ? "No tables match. Clear the filter to see available tables."
					: "No tables match. Selected tables are hidden and will still be included; clear the filter to review them.");
		else if (selected == 0)
			selectionHelp.setText("Tick Include for each table to copy, or use Select visible eligible.");
		else if (hidden > 0)
			selectionHelp.setText(
					"Hidden selections will also be included. Clear the filter to review every selected table.");
		else
			selectionHelp.setText(fileDraftWriter != null
					? "Selection ready. Save the shared CSV job; no rows are exported by saving."
					: managedDirectory == null
							? "Selection ready. Create a draft to review the generated settings in the editor."
							: "Selection ready. Save and review plan checks the target; it does not copy rows.");
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
		sorter.sort();
		draftOptionsChanged();
		refresh();
	}

	void inspectSchema() {
		if (busy || managedDirectory != null)
			return;
		final String path = schemaFile.getText().trim();
		snapshot = null;
		model.setRowCount(0);
		filter.setText("");
		selectedOnly.setSelected(false);
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
					showSnapshot(result);
					fileDialogs.remember(Path.of(path));
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
		final FileDraftWriter customWriter = fileDraftWriter == null ? null : fileDraftWriter.get();
		final long requested = revision;
		busy = true;
		refresh();
		status.setText("Rechecking Schema fingerprint and ordering selected tasks…");
		new SwingWorker<Draft, Void>() {
			protected Draft doInBackground() throws Exception {
				if (customWriter != null)
					return new Draft(customWriter.save(reviewed, selected, chunk, verification), null);
				final String yaml = builder.draft(reviewed, selected, name, chunk, verification);
				return new Draft(yaml,
						managedDirectory == null ? null : DataMigrationDraftService.save(managedDirectory, yaml));
			}

			protected void done() {
				try {
					final var draft = get();
					if (requested == revision) {
						if (savedReady == null)
							ready.accept(draft.yaml());
						else
							savedReady.accept(draft.saved());
					} else
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

	private record Draft(String yaml,
			com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot saved) {
	}

	static JobDraftPanel fileTransfer(SchemaSnapshot schema, java.util.function.Supplier<FileDraftWriter> saver,
			Consumer<Path> ready, Runnable cancelled) {
		final var panel = new JobDraftPanel(path -> ready.accept(Path.of(path)));
		panel.schemaFile.setText(schema.file().toString());
		panel.showSnapshot(schema);
		panel.fileDraftWriter = saver;
		panel.pathRow.setVisible(false);
		panel.identityRow.setVisible(false);
		panel.jobId.setVisible(false);
		for (var component : ((JPanel) panel.jobId.getParent()).getComponents())
			if (component instanceof JLabel label && "Job ID (optional)".equals(label.getText()))
				label.setVisible(false);
		panel.create.setText("Save CSV transfer job");
		panel.actions.add(panel.cancel);
		panel.cancel.addActionListener(event -> {
			if (!panel.isBusy())
				cancelled.run();
		});
		panel.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"),
				"cancelFileJob");
		panel.getActionMap().put("cancelFileJob", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				if (!panel.isBusy())
					cancelled.run();
			}
		});
		panel.status.setText(
				"Select tables for export. Review export warnings before import; saving settings does not move data.");
		panel.refresh();
		return panel;
	}

	private void showSnapshot(final SchemaSnapshot value) {
		snapshot = value;
		fingerprint.setText(value.fingerprint());
		model.setRowCount(0);
		for (final var table : value.tables())
			model.addRow(new Object[] { false, table.table(), String.join(", ", table.primaryKey()),
					table.selectable() ? "Available" : table.restriction() });
	}

	private void failed(Throwable failure) {
		status.setText(EditorStatusPanel.failureMessage(failure));
		statusPanel.setDetails(EditorStatusPanel.failureDetails(failure));
	}
}
