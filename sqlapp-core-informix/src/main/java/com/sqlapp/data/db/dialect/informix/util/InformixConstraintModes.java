/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.util;

import com.sqlapp.data.schemas.Constraint;

/**
 * Persistent object modes, independent of transaction-level constraint
 * deferral.
 */
public final class InformixConstraintModes {
	public static final String MODE = "INFORMIX_CONSTRAINT_MODE";
	public static final String NOT_NULL_NAME = "INFORMIX_NOT_NULL_NAME";
	public static final String NOT_NULL_OWNER = "INFORMIX_NOT_NULL_OWNER";
	public static final String NOT_NULL_MODE = "INFORMIX_NOT_NULL_MODE";

	private InformixConstraintModes() {
	}

	public static String decode(String state) {
		if (state == null)
			return "ENABLED"; // System objects without a mode row are always enabled.
		return switch (state) {
		case "E" -> "ENABLED";
		case "D" -> "DISABLED";
		case "F" -> "FILTERING WITHOUT ERROR";
		case "G" -> "FILTERING WITH ERROR";
		default -> throw new IllegalStateException("Unknown Informix constraint mode: " + state);
		};
	}

	public static void apply(Constraint constraint, String state) {
		final String mode = decode(state);
		constraint.setEnable(!"DISABLED".equals(mode));
		constraint.getSpecifics().remove(MODE);
		if (mode.startsWith("FILTERING "))
			constraint.getSpecifics().put(MODE, mode);
	}

	public static String sql(Constraint constraint) {
		final String mode = constraint.getSpecifics().get(MODE);
		if (mode == null)
			return constraint.isEnable() ? null : "DISABLED";
		if (!constraint.isEnable())
			throw new IllegalArgumentException(
					"Filtering Informix constraint must be enabled: " + constraint.getName());
		if (!java.util.Set.of("FILTERING WITHOUT ERROR", "FILTERING WITH ERROR").contains(mode))
			throw new IllegalArgumentException("Invalid Informix filtering mode: " + mode);
		return mode;
	}

	public static String notNullMode(String mode) {
		if (mode == null)
			return "ENABLED";
		if (!java.util.Set.of("DISABLED", "FILTERING WITHOUT ERROR", "FILTERING WITH ERROR").contains(mode))
			throw new IllegalArgumentException("Invalid Informix NOT NULL mode: " + mode);
		return mode;
	}
}
