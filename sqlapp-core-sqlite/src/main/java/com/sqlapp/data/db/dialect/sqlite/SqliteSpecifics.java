/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite;

import com.sqlapp.data.schemas.Table;

/**
 * SQLite-specific Schema specifics keys. Keys use the {@code sqlite.} prefix;
 * the earlier unprefixed table option keys are still read.
 */
public final class SqliteSpecifics {
	/** Table declared {@code STRICT}. */
	public static final String STRICT = "sqlite.strict";
	/** Table declared {@code WITHOUT ROWID}. */
	public static final String WITHOUT_ROWID = "sqlite.withoutRowid";
	/** Earlier key for {@link #STRICT}, still read. */
	public static final String LEGACY_STRICT = "strict";
	/** Earlier key for {@link #WITHOUT_ROWID}, still read. */
	public static final String LEGACY_WITHOUT_ROWID = "without_rowid";
	/** Unnamed UNIQUE constraint; its name was generated when read. */
	public static final String UNIQUE_UNNAMED = "sqlite.unique.unnamed";
	/** Unnamed CHECK constraint; its name was generated when read. */
	public static final String CHECK_UNNAMED = "sqlite.check.unnamed";
	/** Unnamed foreign key; its name was generated when read. */
	public static final String FOREIGN_KEY_UNNAMED = "sqlite.foreignKey.unnamed";

	private SqliteSpecifics() {
	}

	public static boolean isStrict(final Table table) {
		return flag(table, STRICT, LEGACY_STRICT);
	}

	public static boolean isWithoutRowid(final Table table) {
		return flag(table, WITHOUT_ROWID, LEGACY_WITHOUT_ROWID);
	}

	/** Whether the table still carries an earlier unprefixed option key. */
	public static boolean hasLegacyKeys(final Table table) {
		return table != null && (table.getSpecifics().containsKey(LEGACY_STRICT)
				|| table.getSpecifics().containsKey(LEGACY_WITHOUT_ROWID));
	}

	/** Moves earlier unprefixed option keys to their current names. */
	public static void migrateLegacyKeys(final Table table) {
		move(table, LEGACY_STRICT, STRICT);
		move(table, LEGACY_WITHOUT_ROWID, WITHOUT_ROWID);
	}

	private static void move(final Table table, final String from, final String to) {
		String value = table.getSpecifics().get(from);
		if (value == null)
			return;
		table.getSpecifics().remove(from);
		if ("true".equals(value) || !table.getSpecifics().containsKey(to))
			table.getSpecifics().put(to, value);
	}

	/** Whether a generated constraint name should be omitted from SQLite DDL. */
	public static boolean isUnnamed(final com.sqlapp.data.schemas.properties.SpecificsProperty<?> constraint) {
		var specifics = constraint.getSpecifics();
		return "true".equals(specifics.get(UNIQUE_UNNAMED)) || "true".equals(specifics.get(CHECK_UNNAMED))
				|| "true".equals(specifics.get(FOREIGN_KEY_UNNAMED));
	}

	private static boolean flag(final Table table, final String key, final String legacyKey) {
		return table != null
				&& ("true".equals(table.getSpecifics().get(key)) || "true".equals(table.getSpecifics().get(legacyKey)));
	}
}
