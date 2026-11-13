/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

/**
 * A numbered step of a screen ("1. Connect …"), so the form reads in the order
 * it is used.
 */
final class StepSection extends JPanel {
	private static final long serialVersionUID = 1L;
	private final TitledBorder border;

	StepSection(final String title) {
		super(new BorderLayout(4, 4));
		border = BorderFactory.createTitledBorder(title);
		border.setTitleFont(getFont().deriveFont(java.awt.Font.BOLD));
		setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(2, 4, 4, 4)));
	}

	String title() {
		return border.getTitle();
	}

	void setTitle(final String title) {
		border.setTitle(title);
		repaint();
	}
}
