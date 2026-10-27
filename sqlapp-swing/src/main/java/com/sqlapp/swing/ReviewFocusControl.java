/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;

/**
 * Temporarily gives a connected review more space without changing its inputs.
 */
final class ReviewFocusControl {
	private final JToggleButton toggle;
	private final JToggleButton advanced;
	private int inputDivider;

	ReviewFocusControl(JSplitPane split, JComponent inputViewport, JComponent controls, JToggleButton toggle,
			JToggleButton advanced) {
		this.toggle = toggle;
		this.advanced = advanced;
		toggle.setToolTipText(
				"Hide input settings to give results more space. Inputs and the reviewed plan are retained.");
		toggle.addActionListener(event -> {
			if (toggle.isSelected())
				inputDivider = split.getDividerLocation();
			inputViewport.setVisible(!toggle.isSelected());
			inputViewport.getParent().revalidate();
			split.setDividerLocation(toggle.isSelected() ? controls.getPreferredSize().height + 4 : inputDivider);
			toggle.setText(toggle.isSelected() ? "Show input settings" : "Focus on results");
			advanced.setEnabled(!toggle.isSelected());
		});
	}

	void setBusy(boolean busy) {
		toggle.setEnabled(!busy);
		advanced.setEnabled(!busy && !toggle.isSelected());
	}

	void showInputs() {
		if (toggle.isSelected())
			toggle.doClick(0);
	}
}
