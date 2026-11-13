/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JTabbedPane;

/** Finds and selects a screen that may sit in a tab group such as "Tools". */
final class NestedTabs {
	private NestedTabs() {
	}

	static boolean contains(final JTabbedPane tabs, final Component screen) {
		return path(tabs, screen) != null;
	}

	/**
	 * Selects the screen's tab and every enclosing group tab; false when it is not
	 * in {@code tabs}.
	 */
	static boolean select(final JTabbedPane tabs, final Component screen) {
		final var path = path(tabs, screen);
		if (path == null)
			return false;
		for (int index = path.size() - 1; index >= 0; index--)
			path.get(index).getKey().setSelectedComponent(path.get(index).getValue());
		return true;
	}

	/** (pane, tab) pairs from the screen's own pane outwards, or null. */
	private static List<Map.Entry<JTabbedPane, Component>> path(final JTabbedPane tabs, final Component screen) {
		for (int index = 0; index < tabs.getTabCount(); index++) {
			final Component tab = tabs.getComponentAt(index);
			if (tab == screen)
				return new ArrayList<>(List.of(Map.entry(tabs, screen)));
			if (tab instanceof JTabbedPane group) {
				final var inner = path(group, screen);
				if (inner != null) {
					inner.add(Map.entry(tabs, group));
					return inner;
				}
			}
		}
		return null;
	}
}
