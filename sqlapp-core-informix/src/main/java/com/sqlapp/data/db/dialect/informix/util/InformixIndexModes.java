/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.util;

import com.sqlapp.data.schemas.Index;

/** Persistent modes of standalone Informix indexes. */
public final class InformixIndexModes {
	public static final String MODE = "INFORMIX_INDEX_MODE";

	private InformixIndexModes() {
	}

	public static void apply(final Index index, final String state) {
		final String mode = switch (state == null ? "E" : state) {
		case "E" -> "ENABLED";
		case "D" -> "DISABLED";
		case "F" -> "FILTERING WITHOUT ERROR";
		case "G" -> "FILTERING WITH ERROR";
		default -> throw new IllegalStateException("Unknown Informix index mode: " + state);
		};
		if (mode.startsWith("FILTERING ") && !index.isUnique())
			throw new IllegalStateException("Filtering mode requires a unique Informix index: " + index.getName());
		index.setEnable(!"DISABLED".equals(mode));
		index.getSpecifics().remove(MODE);
		if (mode.startsWith("FILTERING "))
			index.getSpecifics().put(MODE, mode);
	}

	public static String sql(final Index index) {
		final String mode = index.getSpecifics().get(MODE);
		if (mode == null)
			return index.isEnable() ? null : "DISABLED";
		if (!index.isEnable() || !index.isUnique())
			throw new IllegalArgumentException(
					"Filtering mode requires an enabled unique Informix index: " + index.getName());
		if (!java.util.Set.of("FILTERING WITHOUT ERROR", "FILTERING WITH ERROR").contains(mode))
			throw new IllegalArgumentException("Invalid Informix index filtering mode: " + mode);
		return mode;
	}
}
