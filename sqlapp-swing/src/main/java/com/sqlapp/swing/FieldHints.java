/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import javax.swing.text.JTextComponent;

/** Short input hints shown inside empty fields, keeping field labels short. */
final class FieldHints {
	/** FlatLaf client property; other look and feels ignore it. */
	private static final String PLACEHOLDER = "JTextField.placeholderText";

	private FieldHints() {
	}

	static <T extends JTextComponent> T placeholder(final T field, final String hint) {
		field.putClientProperty(PLACEHOLDER, hint);
		return field;
	}
}
