/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.formdev.flatlaf.FlatLightLaf;

/** Install the desktop theme before constructing Swing components. */
final class DesktopAppearance {
	private DesktopAppearance() {
	}

	static void install() {
		try {
			UIManager.setLookAndFeel(new FlatLightLaf());
		} catch (final UnsupportedLookAndFeelException exception) {
			throw new IllegalStateException("Unable to initialize FlatLaf.", exception);
		}
	}
}
