/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;

/** Task-oriented navigation; never opens files, connects or executes jobs. */
final class MigrationStartPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	enum Destination {
		DATA, FILE, MDB, COMPARE, REPORTS, SCHEMA_PLAN, SQL_EDITOR, JOB_EDITOR, DRIVERS, LEGACY_DATA
	}

	final Map<Destination, JButton> buttons = new EnumMap<>(Destination.class);
	final JToggleButton more = new JToggleButton("More tools");
	final JPanel tools = new JPanel(new GridLayout(0, 2, 8, 4));

	MigrationStartPanel(Consumer<Destination> navigate) {
		super(new BorderLayout());
		Objects.requireNonNull(navigate);
		final var content = new Content();
		content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		final var heading = new JPanel(new BorderLayout(0, 8));
		final var title = new JLabel("Move schemas and data between databases");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
		heading.add(title, BorderLayout.NORTH);
		heading.add(new JLabel("Choose direct JDBC or a CSV package. Review the target before importing."),
				BorderLayout.SOUTH);
		content.add(heading, BorderLayout.NORTH);
		final var cards = new JPanel(new GridLayout(4, 1, 0, 12));
		cards.add(card("Direct JDBC transfer",
				"Create one job for GUI and Gradle from connections and selected tables, then review the plan.\n"
						+ "No YAML preparation is needed. Choose schema only to create missing tables without copying rows.",
				button(Destination.DATA, "Open direct transfer", navigate)));
		cards.add(card("Transfer with a CSV package",
				"Create a shared GUI / Gradle job, export the source, then import the package when ready.\n"
						+ "Import needs only the target connection. Review existing empty targets or optional table creation.",
				button(Destination.FILE, "Open CSV transfer", navigate)));
		cards.add(card("Migrate an Access file",
				"Select or drop an MDB / ACCDB file, then choose tables and a target database.\n"
						+ "No source JDBC URL is needed. Transfer schema and data, or schema only, using a shared GUI / Gradle job.",
				button(Destination.MDB, "Open Access migration", navigate)));
		cards.add(card("Review saved results",
				"Open saved migration or verification reports to inspect results.\n"
						+ "Saved reports do not check the current database state.",
				button(Destination.REPORTS, "Open reports", navigate)));
		content.add(cards, BorderLayout.CENTER);
		tools.add(button(Destination.COMPARE, "Compare Schema definitions", navigate));
		tools.add(button(Destination.SCHEMA_PLAN, "Review / apply SQL migrations", navigate));
		tools.add(button(Destination.SQL_EDITOR, "SQL editor", navigate));
		tools.add(button(Destination.JOB_EDITOR, "Job YAML editor", navigate));
		tools.add(button(Destination.DRIVERS, "JDBC drivers", navigate));
		tools.add(button(Destination.LEGACY_DATA, "Advanced Data Job", navigate));
		tools.setVisible(false);
		more.addActionListener(event -> {
			tools.setVisible(more.isSelected());
			more.setText(more.isSelected() ? "Fewer tools" : "More tools");
			content.revalidate();
		});
		final var extra = new JPanel(new BorderLayout(0, 8));
		final var toggle = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		toggle.add(more);
		extra.add(toggle, BorderLayout.NORTH);
		extra.add(tools, BorderLayout.CENTER);
		extra.add(new JLabel(
				"Opening a tool keeps existing inputs and unsaved edits. Connections and execution remain explicit."),
				BorderLayout.SOUTH);
		content.add(extra, BorderLayout.SOUTH);
		final var scroll = new JScrollPane(content);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
	}

	private static final class Content extends JPanel implements javax.swing.Scrollable {
		private static final long serialVersionUID = 1L;

		Content() {
			super(new BorderLayout(12, 16));
		}

		@Override
		public java.awt.Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
			return Math.max(16,
					(orientation == javax.swing.SwingConstants.VERTICAL ? visible.height : visible.width) - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return getParent() instanceof javax.swing.JViewport viewport
					&& viewport.getHeight() > getPreferredSize().height;
		}
	}

	private JButton button(Destination destination, String text, Consumer<Destination> navigate) {
		final var button = new JButton(text);
		button.addActionListener(event -> navigate.accept(destination));
		buttons.put(destination, button);
		return button;
	}

	private JPanel card(String title, String description, JButton action) {
		final var card = new JPanel(new BorderLayout(16, 8));
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),
				BorderFactory.createEmptyBorder(16, 16, 16, 16)));
		final var label = new JLabel(title);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 17f));
		card.add(label, BorderLayout.NORTH);
		final var text = new JTextArea(description, 3, 48);
		text.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
		text.setEditable(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setOpaque(false);
		text.setFocusable(false);
		card.add(text, BorderLayout.CENTER);
		final var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		actions.add(action);
		card.add(actions, BorderLayout.EAST);
		return card;
	}

	static MigrationStartPanel install(JTabbedPane tabs, Map<Destination, ? extends Component> destinations) {
		final var targets = new EnumMap<Destination, Component>(Destination.class);
		for (var destination : Destination.values()) {
			final var target = destinations.get(destination);
			if (target == null || tabs.indexOfComponent(target) < 0)
				throw new IllegalArgumentException("Missing tool tab for " + destination);
			targets.put(destination, target);
		}
		final var start = new MigrationStartPanel(destination -> tabs.setSelectedComponent(targets.get(destination)));
		tabs.insertTab("Start", null, start, "Choose a migration task", 0);
		tabs.setSelectedComponent(start);
		return start;
	}
}
