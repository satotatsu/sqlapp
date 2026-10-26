/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Explicit, literal-filtered schema choice; empty string means the current
 * schema.
 */
final class SourceSchemaSelectionPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField filter = new JTextField();
	final DefaultListModel<String> model = new DefaultListModel<>();
	final JList<String> schemas = new JList<>(model);
	final JButton use = new JButton("Use selected schema");
	final JButton current = new JButton("Use current schema");
	final JButton cancel = new JButton("Cancel");
	final JLabel count = new JLabel();

	SourceSchemaSelectionPanel(List<String> names, String selected, Consumer<String> ready, Runnable cancelled) {
		super(new BorderLayout(8, 8));
		final var choices = List.copyOf(names);
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var find = new JPanel(new BorderLayout(8, 4));
		final var label = new JLabel("Find schema");
		label.setLabelFor(filter);
		find.add(label, BorderLayout.WEST);
		find.add(filter, BorderLayout.CENTER);
		find.add(count, BorderLayout.SOUTH);
		add(find, BorderLayout.NORTH);
		schemas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		final var renderer = new DefaultListCellRenderer();
		renderer.putClientProperty("html.disable", Boolean.TRUE);
		schemas.setCellRenderer(renderer);
		add(new JScrollPane(schemas), BorderLayout.CENTER);
		final var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(current);
		actions.add(use);
		actions.add(cancel);
		add(actions, BorderLayout.SOUTH);
		final Runnable refresh = () -> {
			schemas.clearSelection();
			model.clear();
			final String query = filter.getText().toLowerCase(Locale.ROOT);
			for (var name : choices)
				if (name.toLowerCase(Locale.ROOT).contains(query))
					model.addElement(name);
			count.setText(
					model.size() + " / " + choices.size() + " schemas. Selection is used for New migration only.");
			use.setEnabled(false);
		};
		filter.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent event) {
				refresh.run();
			}

			public void removeUpdate(DocumentEvent event) {
				refresh.run();
			}

			public void changedUpdate(DocumentEvent event) {
				refresh.run();
			}
		});
		schemas.addListSelectionListener(event -> use.setEnabled(schemas.getSelectedValue() != null));
		use.addActionListener(event -> {
			if (schemas.getSelectedValue() != null)
				ready.accept(schemas.getSelectedValue());
		});
		current.addActionListener(event -> ready.accept(""));
		cancel.addActionListener(event -> cancelled.run());
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
		getActionMap().put("cancel", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				cancelled.run();
			}
		});
		refresh.run();
		if (choices.contains(selected))
			schemas.setSelectedValue(selected, true);
	}

	static String showDialog(java.awt.Component owner, List<String> names, String selected) {
		final var result = new java.util.concurrent.atomic.AtomicReference<String>();
		final var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Choose source schema",
				java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setContentPane(new SourceSchemaSelectionPanel(names, selected, name -> {
			result.set(name);
			dialog.dispose();
		}, dialog::dispose));
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setSize(700, 440);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		return result.get();
	}
}
